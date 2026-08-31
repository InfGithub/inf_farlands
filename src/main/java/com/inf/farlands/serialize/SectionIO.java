package com.inf.farlands.serialize;

import com.inf.farlands.Config;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * fsa 异步 IO 层。
 *
 * 线程模型架构为主线程权威 + 无状态 IO：
 *   文件缓存/状态在主线程，SectionStorage 主线程独占；IO 线程只做纯 file 读写
 *   doWrite/readData/writePages/close，不碰文件状态。
 *   提交 API 由主线程调用：主线程 getOrOpen 拿 storage → 提交 IO 任务，闭包引用 storage。
 *
 * 两段式写：主线程 prepareWrite 做 alloc → 提交 IO doWrite → 成功回调主线程 commitWrite
 *   offsets+脏页+释放旧。写失败不回调 → 不 commit → 内存保留，dirty 重试。
 *
 * LRU 淘汰：缓存移除 + IO 队列 close 任务——队列顺序保证该文件 pending 写先完成再 close。
 *
 * 读回判定：主线程 getSlot，offsets 即时——不依赖 IO 队列。
 */

@SuppressWarnings({ "resource", "null" })
public final class SectionIO {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "farlands-fsa-io");
        t.setDaemon(true);
        return t;
    });

    /** 主线程独占：打开的 fsa 文件缓存，LinkedHashMap 插入序 ≈ LRU，getOrOpen 提升。 */
    private static final Map<Path, SectionStorage> cache = new LinkedHashMap<>(Config.fsaCacheLimit, 0.75f, true);

    /** 主线程访问：chunkKey → 读回在途 sectionY 集合。 */
    private static final ConcurrentHashMap<Long, IntSet> readingInFlight = new ConcurrentHashMap<>();

    /** 批量读回结果：sectionY → 解码结果。 */
    public record DecodedWithSy(int sectionY, SectionSerializer.DecodedSection decoded) {
    }

    private static final Field F_STORAGE_SOURCE;

    static {
        try {
            F_STORAGE_SOURCE = MinecraftServer.class.getDeclaredField("storageSource");
            F_STORAGE_SOURCE.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Field F_MAIN_EXECUTOR;
    static {
        try {
            F_MAIN_EXECUTOR = ChunkMap.class.getDeclaredField("mainThreadExecutor");
            F_MAIN_EXECUTOR.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SectionIO() {
    }

    // ---- 路径 ----

    private static Path dimensionDir(ServerLevel level) {
        try {
            LevelStorageSource.LevelStorageAccess access =
                    (LevelStorageSource.LevelStorageAccess) F_STORAGE_SOURCE.get(level.getServer());
            return access.getDimensionPath(level.dimension()).resolve("fsa");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** fsa 文件路径：s.{regionX}.{yBlock}.{regionZ}.fsa，yb = 绝对 sectionY >> 5。 */
    public static Path filePath(ServerLevel level, int cx, int cz, int sy) {
        return dimensionDir(level).resolve("s." + (cx >> 5) + "." + (sy >> 5) + "." + (cz >> 5) + ".fsa");
    }

    // ---- 主线程：文件缓存 ----

    /** 主线程：懒打开并获取 storage。 */
    public static SectionStorage getOrOpen(Path path) {
        SectionStorage storage = cache.get(path);
        if (storage != null) {
            return storage;
        }
        try {
            Files.createDirectories(path.getParent());
            storage = SectionStorage.open(path, false);
            cache.put(path, storage);
            if (cache.size() > Config.fsaCacheLimit) {
                // 淘汰最旧：缓存移除 + 先刷偏移表脏页，否则被淘汰文件的偏移表永不落盘 → 重进
                // getSlot 读陈旧 offset → 丢 section；IO 队列 close，pending 写先执行，队列顺序保证
                Path oldest = cache.keySet().iterator().next();
                SectionStorage old = cache.remove(oldest);
                if (old != null) {
                    List<SectionStorage.PageWrite> pages = old.flushAggregate();
                    if (!pages.isEmpty()) {
                        submitWritePages(oldest, pages);
                    }
                    submit(() -> {
                        try {
                            old.close();
                        } catch (Exception ignored) {
                        }
                    });
                }
            }
            return storage;
        } catch (Exception e) {
            throw new RuntimeException("fsa open " + path, e);
        }
    }

    // ---- 提交：主线程调用，IO 线程执行 ----

    /** 提交任务到 IO 队列，串行执行。 */
    public static void submit(Runnable task) {
        try {
            IO.submit(task);
        } catch (RejectedExecutionException e) {
            // 池关闭即关服：丢弃
        }
    }

    /**
     * 两段式写：提交 doWrite → 成功回调主线程，调用方 commitWrite + 删内存。
     * 失败不回调，pending 丢弃，内存保留，dirty 重试。
     */
    public static void submitWrite(ServerLevel level, Path path, List<SectionStorage.PendingWrite> batch,
            Runnable onAllDone) {
        if (batch.isEmpty()) {
            return;
        }
        SectionStorage storage = getOrOpen(path);
        submit(() -> {
            try {
                storage.doWrite(batch);
            } catch (Exception e) {
                com.inf.farlands.InfFarlands.LOGGER.error("fsa doWrite failed {}", path, e);
                return; // 失败：不回调，不 commit
            }
            runOnMainThread(() -> {
                if (onAllDone != null) {
                    onAllDone.run();
                }
            }, level);
        });
    }

    /**
     * 批量读回：IO 线程读全部条目 + decode → 主线程回调，仅成功解码的，带 sectionY。
     * 损坏/读失败的条目跳过，调用方对其走生成管线。
     */
    public static void submitRead(ServerLevel level, Path path, List<SectionStorage.SlotRef> refs,
            Consumer<List<DecodedWithSy>> onFound) {
        if (refs.isEmpty()) {
            runOnMainThread(() -> onFound.accept(List.of()), level);
            return;
        }
        SectionStorage storage = getOrOpen(path);
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        submit(() -> {
            List<DecodedWithSy> decoded = new ArrayList<>(refs.size());
            for (SectionStorage.SlotRef ref : refs) {
                try {
                    byte[] entry = storage.readData(ref.sectorOffset(), ref.sectorCount());
                    if (entry != null) {
                        SectionSerializer.DecodedSection d =
                                SectionSerializer.decode(entry, biomes, ref.sectionY());
                        decoded.add(new DecodedWithSy(ref.sectionY(), d));
                    }
                } catch (Exception e) {
                    // 损坏视为不存在：跳过，生成管线接管
                }
            }
            runOnMainThread(() -> onFound.accept(decoded), level);
        });
    }

    /** IO 线程：刷该文件的脏页，即主线程 flushAggregate 产出的页。 */
    public static void submitWritePages(Path path, List<SectionStorage.PageWrite> pages) {
        if (pages.isEmpty()) {
            return;
        }
        SectionStorage storage = getOrOpen(path);
        submit(() -> {
            try {
                storage.writePages(pages);
            } catch (Exception e) {
                com.inf.farlands.InfFarlands.LOGGER.error("fsa writePages failed {}", path, e);
            }
        });
    }

    /** 主线程：全部缓存文件偏移表脏页刷盘，定期持久化配套——运行中磁盘偏移表陈旧会致重进
     * getSlot 错位丢 section；崩溃/强退兜底。LRU 淘汰路径另有单独刷盘。 */
    public static void flushAllOffsetTables() {
        for (Path p : new ArrayList<>(cache.keySet())) {
            SectionStorage st = cache.get(p);
            if (st == null) {
                continue;
            }
            List<SectionStorage.PageWrite> pages = st.flushAggregate();
            if (!pages.isEmpty()) {
                submitWritePages(p, pages);
            }
        }
    }

    // ---- 刷盘/关闭 ----

    /** 关服：等 IO 队列 drain，屏障任务无超时，IO 单线程必然执行。 */
    public static void awaitIODrain() {
        CountDownLatch latch = new CountDownLatch(1);
        submit(() -> latch.countDown());
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 关服：主线程排空 pending 任务，commit 回调等。单任务异常跳过不中断。 */
    public static void drainMainThreadTasks(MinecraftServer server) {
        try {
            for (ServerLevel level : server.getAllLevels()) {
                ChunkMap chunkMap = level.getChunkSource().chunkMap;
                Object main = F_MAIN_EXECUTOR.get(chunkMap);
                if (main instanceof net.minecraft.util.thread.BlockableEventLoop<?> loop) {
                    @SuppressWarnings("unchecked")
                    net.minecraft.util.thread.BlockableEventLoop<Runnable> l =
                            (net.minecraft.util.thread.BlockableEventLoop<Runnable>) loop;
                    int guard = 0;
                    while (guard++ < 100000) {
                        try {
                            if (!l.pollTask()) {
                                break;
                            }
                        } catch (Exception e) {
                            // 单个任务异常：跳过继续排空，防御 vanilla 残留任务
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 反射失败：跳过，防御
        }
    }

    /** 关服：主线程同步刷盘全部缓存文件，flushAggregate + writePages。 */
    public static void flushAllSync() {
        for (Path p : new ArrayList<>(cache.keySet())) {
            SectionStorage st = cache.get(p);
            if (st == null) {
                continue;
            }
            List<SectionStorage.PageWrite> pages = st.flushAggregate();
            if (pages.isEmpty()) {
                continue;
            }
            try {
                st.writePages(pages);
            } catch (Exception e) {
                com.inf.farlands.InfFarlands.LOGGER.error("fsa flushAllSync failed {}", p, e);
            }
        }
    }

    // ---- 读回在途：主线程 ----

    /** 该 chunk 的读回在途 section 集合，无则空集。 */
    public static IntSet readingSet(long chunkKey) {
        return readingInFlight.get(chunkKey);
    }

    public static boolean isReading(long chunkKey, int sectionY) {
        IntSet set = readingInFlight.get(chunkKey);
        return set != null && set.contains(sectionY);
    }

    public static void markReading(LevelChunk chunk, int sectionY) {
        readingInFlight.computeIfAbsent(chunk.getPos().toLong(), k -> new IntOpenHashSet()).add(sectionY);
    }

    public static void markReadingBatch(LevelChunk chunk, Iterable<Integer> sectionYs) {
        long key = chunk.getPos().toLong();
        IntSet set = readingInFlight.computeIfAbsent(key, k -> new IntOpenHashSet());
        for (int sy : sectionYs) {
            set.add(sy);
        }
    }

    public static void unmarkReading(LevelChunk chunk, int sectionY) {
        long key = chunk.getPos().toLong();
        IntSet set = readingInFlight.get(key);
        if (set != null) {
            set.remove(sectionY);
            if (set.isEmpty()) {
                readingInFlight.remove(key, set);
            }
        }
    }

    public static void unmarkReadingBatch(LevelChunk chunk, Iterable<Integer> sectionYs) {
        long key = chunk.getPos().toLong();
        IntSet set = readingInFlight.get(key);
        if (set == null) {
            return;
        }
        for (int sy : sectionYs) {
            set.remove(sy);
        }
        if (set.isEmpty()) {
            readingInFlight.remove(key, set);
        }
    }

    // ---- 调度 ----

    /** 调度任务到主线程，经 ServerLevel 的 chunkMap 实例的 ChunkMap.mainThreadExecutor——
     *  mainThreadExecutor 是实例字段，get(null) 会 NPE 导致回调在 IO 线程执行。 */
    @SuppressWarnings("unchecked")
    public static void runOnMainThread(Runnable task, ServerLevel level) {
        try {
            ChunkMap chunkMap = level.getChunkSource().chunkMap;
            Object main = F_MAIN_EXECUTOR.get(chunkMap);
            if (main instanceof net.minecraft.util.thread.BlockableEventLoop<?> loop) {
                ((net.minecraft.util.thread.BlockableEventLoop<Runnable>) loop).execute(task);
            } else {
                task.run();
            }
        } catch (Exception e) {
            task.run(); // 反射失败：当前线程执行，防御
        }
    }
}
