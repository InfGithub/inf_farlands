package com.inf.farlands.serialize;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.inf.farlands.Config;
import com.inf.farlands.window.EntitySectionWindow;
import com.inf.farlands.InfFarlands;
import com.inf.farlands.window.WindowedChunk;
import com.inf.farlands.light.FarLandsLightEngine;
import com.inf.farlands.terrain.pipeline.FarLandsGenState;
import com.inf.farlands.terrain.pipeline.GenQueue;

import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

/**
 * fsa 生命周期层：清理判定/编码队列/批调度/内存管理/读回。
 *
 * encode 一律主线程，静止假设与窗口内 section 冲突，用主线程串行代替静止：
 *   待编码队列 pendingEncode 用 ConcurrentLinkedDeque，unloadQueue 线程 flushChunk 入队、
 *   主线程 tick 消费，只存 (chunk, sy, mode)，编码时现取最新状态——等待期间 setBlock/
 *   光照变化被编码捕捉，且编码时刻与主线程写串行 → 无竞态。
 *
 * 每 tick tick() drain 预算 256：现取 → encode → prepare 按文件+mode 分组 → IO 写。
 *   CLEANUP 即清理：commit + 删内存，删 section/stage/dirty/光照
 *   PERSIST 即定期/卸载：commit + 清 dirty，不删内存
 *   encode/写失败：不 commit，dirty 保留重试
 *
 * 触发：cleanup 窗口变化时边界外脏入队 CLEANUP / flushChunk 卸载时脏入队 PERSIST /
 *   flushAllDirty 每 6000 tick 全部脏入队 PERSIST / shutdownSyncFlush 关服同步兜底。
 */
@SuppressWarnings("null")
public final class SectionLifecycle {

    /** 每 tick 清理入队上限。 */
    public static final int CLEANUP_BUDGET = 1024;
    /** 每 tick 主线程 encode 上限，分摊预算，防卡 tick。 */
    public static final int ENCODE_BUDGET = 256;

    /** 待编码单元：只存引用，编码时现取最新，主线程串行无竞态。mode = cleanup 语义。 */
    private record EncodeUnit(LevelChunk chunk, int sectionY, boolean cleanup) {
    }

    /** commit 后动作的单位，cleanup：删内存；persist：清 dirty。 */
    private record CleanupUnit(LevelChunk chunk, int sectionY) {
    }

    /** 待编码队列，unloadQueue 线程入队 + 主线程 tick 消费，线程安全。 */
    private static final ConcurrentLinkedDeque<EncodeUnit> pendingEncode = new ConcurrentLinkedDeque<>();

    /** 窗口未建立时加载的 chunk，loadChunkSections windowSy=0 → 标记延迟读回；强引用，
     * 生命周期短——窗口建立后 1 tick 内清空。重进瞬间 ranges 空 → 读回空转的兜底：
     * 窗口建立后 retryPendingReads 重新触发读回，磁盘数据在但重进漏读 → section 消失。 */
    private static final Set<LevelChunk> pendingWindowRead = ConcurrentHashMap.newKeySet();

    private static final Method M_GET_CHUNKS;

    static {
        try {
            M_GET_CHUNKS = ChunkMap.class.getDeclaredMethod("getChunks");
            M_GET_CHUNKS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SectionLifecycle() {
    }

    // ---- 触发入队 ----

    /** 窗口变化时扫描清理，主线程，InfFarlands.onServerTick windowChanged 块调用。 */
    public static void cleanup(MinecraftServer server) {
        int[] budget = { CLEANUP_BUDGET };
        for (ServerLevel level : server.getAllLevels()) {
            for (ChunkHolder holder : getChunks(level)) {
                // getLatestChunk 而非 getTickingChunk：暂停/登出后 chunk 降级，tickingChunkFuture
                // 完成成 UNLOADED → getTickingChunk 返回 null → 漏遍历；getLatestChunk 不依赖
                // ticking 状态，只要 chunk 在 holder 即返回。
                ChunkAccess ca = holder.getLatestChunk();
                if (!(ca instanceof LevelChunk lc) || GenQueue.isChunkBusy(lc)) {
                    continue;
                }
                WindowedChunk wc = (WindowedChunk) lc;
                // 增量扫描：只遍历窗口并集±余量外的 section——成本 O(log n + 边界外数)
                wc.forEachOutsideWindows(Config.fsaCleanupMargin, sy -> {
                    if (budget[0] <= 0) {
                        return;
                    }
                    if (wc.isSectionDirty(sy)) {
                        pendingEncode.add(new EncodeUnit(lc, sy, true));
                        budget[0]--;
                    } else {
                        removeFromMemory(lc, sy);
                    }
                });
            }
        }
    }

    /** 卸载编码在途任务数，关服等待用，任务 finally 递减。 */
    private static final AtomicInteger ENCODE_TASKS_IN_FLIGHT = new AtomicInteger();

    /** 卸载编码独立线程池，单线程 daemon。与 genPool 隔离——编码不抢生成线程
     * （探索时 fill 与卸载编码曾共享 genPool，fill 被延迟 → 生成效率下降）。 */
    private static final ExecutorService ENCODE_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "farlands-encode");
        t.setDaemon(true);
        return t;
    });

    /** 卸载时写该 chunk 全部脏 section，ChunkMap.scheduleUnload 调用，unloadQueue 线程。
     * 生成/光照在途即 isChunkBusy → 跳过：半成品/光照未就绪不落盘，数据由光照完成补触发
     * persistChunkDirty 兜底写盘。fill 在途由 GEN_WORK_TICKET 保加载，不卸载；light 在途
     * 由 CHUNK_WORK_TICKET 保加载——卸载时不在途，写盘数据完整，含光照。
     * A2：编码提交独立 ENCODE_POOL，不占主线程预算、不阻塞 unloadQueue、不与生成抢线程——
     * flushChunk 返回后 LevelChunk 引用即释放（闭包只被编码任务短暂持有）。并发约定：
     * cleanup 主线程可能并发删除本 chunk 窗口外 section——编码读 null/旧引用均无害，
     * cleanup 只删磁盘已有或已入队的 section；同一 section 的重复写幂等。 */
    public static void flushChunk(LevelChunk lc) {
        if (GenQueue.isChunkBusy(lc)) {
            return;
        }
        ServerLevel level = (ServerLevel) lc.getLevel();
        ENCODE_TASKS_IN_FLIGHT.incrementAndGet();
        try {
            ENCODE_POOL.submit(() -> {
                try {
                    encodeAndSubmit(lc, level);
                } finally {
                    ENCODE_TASKS_IN_FLIGHT.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException e) {
            ENCODE_TASKS_IN_FLIGHT.decrementAndGet();
            // 池关闭：编码丢弃，数据由重进重生成兜底
        }
    }

    /** 编码池线程：现取现编码该 chunk 全部脏 section，回主线程 prepareWrite + submitWrite。
     * 提交前验证 chunk 对象未变——已重新加载为新对象则丢弃该编码，数据由新 chunk 的写盘
     * 路径处理，防旧数据覆盖新数据。验证 null 或同一对象均提交（未重载 = 正常卸载）。 */
    private static void encodeAndSubmit(LevelChunk lc, ServerLevel level) {
        ChunkPos cp = lc.getPos();
        // path → (entry, slotIdx, sy)
        Map<Path, List<Object[]>> byFile = new LinkedHashMap<>();
        for (Integer sy : ((WindowedChunk) lc).windowedAllSections().keySet()) {
            if (!((WindowedChunk) lc).isSectionDirty(sy)) {
                continue;
            }
            byte[] entry = encodeNow(lc, sy);
            if (entry == null) {
                continue;
            }
            Path path = SectionIO.filePath(level, cp.x, cp.z, sy);
            byFile.computeIfAbsent(path, k -> new ArrayList<>())
                    .add(new Object[] { entry, SectionStorage.slotIndex(cp.x & 31, cp.z & 31, sy & 31), sy });
        }
        if (byFile.isEmpty()) {
            return;
        }
        SectionIO.runOnMainThread(() -> {
            // 验证：chunk 未重新加载（同一对象或已完全卸载）才提交
            ChunkAccess ca = level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
            if (ca != null && ca != lc) {
                return; // 重新加载为新对象 → 丢弃旧编码
            }
            for (Map.Entry<Path, List<Object[]>> e : byFile.entrySet()) {
                Path path = e.getKey();
                SectionStorage st = SectionIO.getOrOpen(path);
                List<SectionStorage.PendingWrite> batch = new ArrayList<>();
                for (Object[] meta : e.getValue()) {
                    SectionStorage.PendingWrite pw = st.prepareWrite((Integer) meta[1], (byte[]) meta[0]);
                    if (pw != null) {
                        batch.add(pw);
                    }
                }
                if (!batch.isEmpty()) {
                    // 卸载路径：onAllDone 只 commit，不 clearDirty——chunk 已释放，dirty 随对象消亡
                    SectionIO.submitWrite(level, path, batch, () -> {
                        SectionStorage st2 = SectionIO.getOrOpen(path);
                        for (SectionStorage.PendingWrite pw : batch) {
                            st2.commitWrite(pw);
                        }
                    });
                }
            }
        }, level);
    }

    /** 关服：等卸载编码任务全部提交，有界。genPool 执行编码 → runOnMainThread 提交
     * prepareWrite+submitWrite——循环内反复 drain 主线程以消费提交回调。超时：未提交的
     * 编码丢弃，重进重生成兜底。必须在 awaitIODrain 之前调用，否则编码任务提交的
     * IO 写未被等待。 */
    public static void awaitEncodeTasks(MinecraftServer server, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline && ENCODE_TASKS_IN_FLIGHT.get() > 0) {
            SectionIO.drainMainThreadTasks(server);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 光照完成补触发，GenQueue.triggerLight whenComplete，lightPool 线程：该 chunk 脏 section
     * 入队 PERSIST——fill+光照已完成，数据完整。调用点保证不 busy，无条件入队，幂等。 */
    public static void persistChunkDirty(LevelChunk lc) {
        enqueueDirty(lc);
    }

    /** 该 chunk 全部脏 section 入队 PERSIST，只存引用，主线程 tick 现取现编码。 */
    private static void enqueueDirty(LevelChunk lc) {
        WindowedChunk wc = (WindowedChunk) lc;
        for (Integer sy : wc.windowedAllSections().keySet()) {
            if (wc.isSectionDirty(sy)) {
                pendingEncode.add(new EncodeUnit(lc, sy, false));
            }
        }
    }

    /** 定期持久化，每 PERSIST_INTERVAL tick，主线程：全部加载 chunk 脏 section 入队 PERSIST。 */
    public static void flushAllDirty(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (ChunkHolder holder : getChunks(level)) {
                ChunkAccess ca = holder.getLatestChunk();
                if (!(ca instanceof LevelChunk lc)) {
                    continue;
                }
                if (GenQueue.isChunkBusy(lc)) {
                    continue; // 生成/光照在途：跳过，由光照完成补触发 persistChunkDirty 写盘
                }
                WindowedChunk wc = (WindowedChunk) lc;
                for (Integer sy : wc.windowedAllSections().keySet()) {
                    if (wc.isSectionDirty(sy)) {
                        pendingEncode.add(new EncodeUnit(lc, sy, false));
                    }
                }
            }
        }
    }

    // ---- 每 tick 编码消费：主线程 ----

    /** 每 tick drain 预算：现取 → encode → prepare → 提交写，按文件+mode 分组。 */
    public static void tick() {
        Map<Path, List<SectionStorage.PendingWrite>> cleanupByFile = new LinkedHashMap<>();
        Map<Path, List<CleanupUnit>> cleanupUnits = new LinkedHashMap<>();
        Map<Path, List<SectionStorage.PendingWrite>> persistByFile = new LinkedHashMap<>();
        Map<Path, List<CleanupUnit>> persistUnits = new LinkedHashMap<>();
        Map<Path, ServerLevel> levelByPath = new HashMap<>();

        int budget = ENCODE_BUDGET;
        while (budget-- > 0) {
            EncodeUnit u = pendingEncode.poll();
            if (u == null) {
                break;
            }
            byte[] entry = encodeNow(u.chunk(), u.sectionY());
            if (entry == null) {
                continue; // encode 失败：丢弃，dirty 保留，下次触发重入队
            }
            ServerLevel level = (ServerLevel) u.chunk().getLevel();
            ChunkPos cp = u.chunk().getPos();
            Path path = SectionIO.filePath(level, cp.x, cp.z, u.sectionY());
            SectionStorage st = SectionIO.getOrOpen(path);
            SectionStorage.PendingWrite pw = st.prepareWrite(
                    SectionStorage.slotIndex(cp.x & 31, cp.z & 31, u.sectionY() & 31), entry);
            if (pw == null) {
                continue;
            }
            levelByPath.put(path, level);
            if (u.cleanup()) {
                cleanupByFile.computeIfAbsent(path, k -> new ArrayList<>()).add(pw);
                cleanupUnits.computeIfAbsent(path, k -> new ArrayList<>())
                        .add(new CleanupUnit(u.chunk(), u.sectionY()));
            } else {
                persistByFile.computeIfAbsent(path, k -> new ArrayList<>()).add(pw);
                persistUnits.computeIfAbsent(path, k -> new ArrayList<>())
                        .add(new CleanupUnit(u.chunk(), u.sectionY()));
            }
        }
        submitBatches(levelByPath, cleanupByFile, cleanupUnits, true);
        submitBatches(levelByPath, persistByFile, persistUnits, false);
    }

    /** 关服同步兜底：仍脏 section 同步 encode+prepare+doWrite+commit + 同步刷盘。
     * 调用方已 awaitIODrain，IO 队列空，加 drainMainThreadTasks，commit 回调执行完——
     * 本方法处理屏障后新标脏的，罕见；全主线程同步，无异步回调依赖。 */
    public static void shutdownSyncFlush(MinecraftServer server) {
        Map<Path, List<SectionStorage.PendingWrite>> byFile = new LinkedHashMap<>();
        Map<Path, List<CleanupUnit>> unitsByFile = new LinkedHashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (ChunkHolder holder : getChunks(level)) {
                // getLatestChunk 而非 getTickingChunk：关服/暂停后 chunk 降级非 TICKING →
                // getTickingChunk 返回 null → 漏写盘，数据丢根因；getLatestChunk 不依赖 ticking。
                ChunkAccess ca = holder.getLatestChunk();
                if (!(ca instanceof LevelChunk lc)) {
                    continue;
                }
                if (GenQueue.isChunkBusy(lc)) {
                    continue; // 关服等待超时后仍有在途：跳过，重进重生成，兜底
                }
                WindowedChunk wc = (WindowedChunk) lc;
                for (Integer sy : wc.windowedAllSections().keySet()) {
                    if (!wc.isSectionDirty(sy)) {
                        continue;
                    }
                    byte[] entry = encodeNow(lc, sy); // PERSIST 语义
                    if (entry == null) {
                        continue;
                    }
                    ChunkPos cp = lc.getPos();
                    Path path = SectionIO.filePath(level, cp.x, cp.z, sy);
                    SectionStorage st = SectionIO.getOrOpen(path);
                    SectionStorage.PendingWrite pw = st.prepareWrite(
                            SectionStorage.slotIndex(cp.x & 31, cp.z & 31, sy & 31), entry);
                    if (pw != null) {
                        byFile.computeIfAbsent(path, k -> new ArrayList<>()).add(pw);
                        unitsByFile.computeIfAbsent(path, k -> new ArrayList<>())
                                .add(new CleanupUnit(lc, sy));
                    }
                }
            }
        }
        // 同步写即主线程 file.write + commit + 清 dirty
        for (Map.Entry<Path, List<SectionStorage.PendingWrite>> e : byFile.entrySet()) {
            Path path = e.getKey();
            SectionStorage st = SectionIO.getOrOpen(path);
            try {
                st.doWrite(e.getValue());
            } catch (Exception ex) {
                InfFarlands.LOGGER.error("fsa shutdown write failed {}", path, ex);
                continue;
            }
            for (SectionStorage.PendingWrite pw : e.getValue()) {
                st.commitWrite(pw);
            }
            for (CleanupUnit u : unitsByFile.get(path)) {
                ((WindowedChunk) u.chunk()).clearSectionDirty(u.sectionY());
            }
        }
        // 同步刷盘全部缓存文件
        SectionIO.flushAllSync();
    }

    /** 提交写批；onAllDone 按 mode：cleanup → commit + 删内存；persist → commit + 清 dirty。 */
    private static void submitBatches(Map<Path, ServerLevel> levelByPath,
            Map<Path, List<SectionStorage.PendingWrite>> byFile,
            Map<Path, List<CleanupUnit>> unitsByFile, boolean cleanup) {
        for (Map.Entry<Path, List<SectionStorage.PendingWrite>> e : byFile.entrySet()) {
            Path path = e.getKey();
            List<SectionStorage.PendingWrite> batch = e.getValue();
            List<CleanupUnit> units = unitsByFile.get(path);
            SectionIO.submitWrite(levelByPath.get(path), path, batch, () -> {
                SectionStorage st = SectionIO.getOrOpen(path);
                for (SectionStorage.PendingWrite pw : batch) {
                    st.commitWrite(pw);
                }
                if (units != null) {
                    for (CleanupUnit u : units) {
                        if (cleanup) {
                            removeFromMemory(u.chunk(), u.sectionY());
                        } else {
                            ((WindowedChunk) u.chunk()).clearSectionDirty(u.sectionY());
                        }
                    }
                }
            });
        }
    }

    private static final AtomicInteger ENCODE_FAIL_LOGGED = new AtomicInteger();

    /** 现取现编码单个 section，等待期间的变化被捕捉。主线程 tick 编码与 genPool 卸载编码
     * 共用：纯读 + 局部对象，任意线程安全；编码时刻与主线程写串行 → 无竞态。 */
    private static byte[] encodeNow(LevelChunk lc, int sy) {
        try {
            LevelChunkSection section = ((WindowedChunk) lc).windowedAllSections().get(sy);
            if (section == null) {
                return null;
            }
            ServerLevel level = (ServerLevel) lc.getLevel();
            LevelLightEngine le = level.getChunkSource().getLightEngine();
            DataLayer bl = le.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(lc.getPos(), sy));
            DataLayer sl = le.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(lc.getPos(), sy));
            int stage = FarLandsGenState.getStage(lc, sy);
            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            return SectionSerializer.encode(section, bl, sl, stage, biomes, sy);
        } catch (Exception e) {
            // encode 失败静默 → 数据不写盘 → 丢，dirty 保留重试
            if (ENCODE_FAIL_LOGGED.getAndIncrement() < 20) {
                InfFarlands.LOGGER.info("ENCODE-FAIL chunk={},{} sy={} err={}",
                        lc.getPos().x, lc.getPos().z, sy, e.toString());
            }
            return null;
        }
    }

    /** 删内存：section + stage + 脏标记 + 光照层，主线程。窗口内不删。 */
    private static void removeFromMemory(LevelChunk lc, int sy) {
        if (EntitySectionWindow.inAnyWindow(sy)) {
            return;
        }
        WindowedChunk wc = (WindowedChunk) lc;
        wc.windowedAllSections().remove(sy);
        wc.removeActiveSection(sy);
        FarLandsGenState.removeStage(lc, sy);
        wc.clearSectionDirty(sy);
        if (lc.getLevel() instanceof ServerLevel sl
                && sl.getChunkSource().getLightEngine() instanceof FarLandsLightEngine fle) {
            SectionPos pos = SectionPos.of(lc.getPos(), sy);
            fle.removeSectionData(LightLayer.BLOCK, pos);
            fle.removeSectionData(LightLayer.SKY, pos);
        }
    }

    // ---- 读回 ----

    /** chunk 加载后读回窗口内 section，主线程；全部完成后 onDone，随后可入生成队列。 */
    public static void loadChunkSections(LevelChunk lc, Runnable onDone) {
        ServerLevel level = (ServerLevel) lc.getLevel();
        ChunkPos cp = lc.getPos();
        List<Integer> windowSy = new ArrayList<>();
        EntitySectionWindow.forEachSectionInAnyWindow(windowSy::add);
        if (windowSy.isEmpty()) {
            // 窗口未建立，重进瞬间 ranges 空 → 标记延迟读回，窗口建立后 retryPendingReads
            // 重新触发；onDone 照常，enqueueChunk 的 collectSegments 窗口空 → 不生成，等待重试
            pendingWindowRead.add(lc);
            SectionIO.unmarkReadingBatch(lc, windowSy);
            onDone.run();
            return;
        }
        pendingWindowRead.remove(lc); // 读回已发起，幂等，可能未标记过
        SectionIO.markReadingBatch(lc, windowSy);

        Map<Path, List<SectionStorage.SlotRef>> byFile = new LinkedHashMap<>();
        Map<Path, ServerLevel> levelByPath = new HashMap<>();
        for (int sy : windowSy) {
            Path path = SectionIO.filePath(level, cp.x, cp.z, sy);
            if (!Files.exists(path)) {
                continue; // 无文件 = 无数据，不创建
            }
            SectionStorage st = SectionIO.getOrOpen(path);
            SectionStorage.SlotRef ref = st.getSlot(
                    SectionStorage.slotIndex(cp.x & 31, cp.z & 31, sy & 31), sy);
            if (ref != null) {
                byFile.computeIfAbsent(path, k -> new ArrayList<>()).add(ref);
                levelByPath.put(path, level);
            }
        }
        if (byFile.isEmpty()) {
            SectionIO.unmarkReadingBatch(lc, windowSy);
            onDone.run();
            return;
        }
        AtomicInteger pending = new AtomicInteger(byFile.size());
        for (Map.Entry<Path, List<SectionStorage.SlotRef>> e : byFile.entrySet()) {
            SectionIO.submitRead(levelByPath.get(e.getKey()), e.getKey(), e.getValue(),
                    decodedList -> {
                for (SectionIO.DecodedWithSy d : decodedList) {
                    applyDecoded(lc, d.sectionY(), d.decoded());
                }
                if (pending.decrementAndGet() <= 0) {
                    SectionIO.unmarkReadingBatch(lc, windowSy);
                    skySourcesReady(lc);
                    onDone.run();
                }
            });
        }
    }

    /** 每 tick 于 onServerTick：重试"窗口未建立时加载"的 chunk 读回——窗口已建立 →
     * 重新 loadChunkSections，windowSy>0 → 读回窗口 section，含重进瞬间漏读的；
     * 窗口仍空 → loadChunkSections 内重新标记，下 tick 再试；已卸载 → 数据已落盘不再处理。 */
    public static void retryPendingReads(MinecraftServer server) {
        if (pendingWindowRead.isEmpty() || EntitySectionWindow.ranges().length == 0) {
            return; // 无 pending 或窗口未建立，Preparing/玩家未注册期：零开销早退——
                    // 此前 ranges 空也全量重试 → 初次进入世界每 tick 巨量 stat 卡死
        }
        int budget = 32; // 每 tick 最多重试 32 个，防单 tick 巨量 Files.exists stat
        for (LevelChunk lc : pendingWindowRead) {
            if (budget-- <= 0) {
                break; // 剩余留到下轮，pending 不减少，下轮继续
            }
            pendingWindowRead.remove(lc); // 先移除防重入；windowSy 仍空会在 loadChunkSections 内重新标记
            if (lc.getLevel() instanceof ServerLevel) {
                loadChunkSections(lc, () -> GenQueue.enqueueChunk(lc));
            }
        }
    }

    /** 窗口滑入单 section 读回，主线程；完成后 onDone，可随后入生成队列。 */
    public static void loadSection(LevelChunk lc, int sectionY, Runnable onDone) {
        SectionIO.markReading(lc, sectionY);
        ServerLevel level = (ServerLevel) lc.getLevel();
        ChunkPos cp = lc.getPos();
        Path path = SectionIO.filePath(level, cp.x, cp.z, sectionY);
        boolean exist = Files.exists(path);
        if (!exist) {
            SectionIO.unmarkReading(lc, sectionY);
            onDone.run();
            return;
        }
        SectionStorage st = SectionIO.getOrOpen(path);
        SectionStorage.SlotRef ref = st.getSlot(
                SectionStorage.slotIndex(cp.x & 31, cp.z & 31, sectionY & 31), sectionY);
        if (ref == null) {
            SectionIO.unmarkReading(lc, sectionY);
            onDone.run();
            return;
        }
        SectionIO.submitRead(level, path, List.of(ref), decodedList -> {
            SectionIO.unmarkReading(lc, sectionY);
            for (SectionIO.DecodedWithSy d : decodedList) {
                applyDecoded(lc, d.sectionY(), d.decoded());
            }
            skySourcesReady(lc);
            onDone.run();
        });
    }

    /** 读回完成触发：清理 SKY_WAITERS 中等待该 chunk 的条目，并触发邻居边界重播。
     * 读回 chunk 的 SkyLightSources 未 fillFrom（source 全 minY）→ boundaryAllOpen false →
     * 触发重播，重播用伪源与播种时一致，无新增错误；读回每 chunk 一次，低频。 */
    private static void skySourcesReady(LevelChunk lc) {
        if (lc.getLevel() instanceof ServerLevel sl
                && sl.getChunkSource().getLightEngine() instanceof com.inf.farlands.light.FarLandsLightEngine fle) {
            fle.onChunkSkySourcesReady(lc.getPos());
        }
    }

    /** 读回结果应用，主线程：数据 + 光照 + stage + §5 补发。读回不标脏，磁盘已有。 */
    private static void applyDecoded(LevelChunk lc, int sy, SectionSerializer.DecodedSection decoded) {
        WindowedChunk wc = (WindowedChunk) lc;
        wc.windowedAllSections().put(sy, decoded.section());
        wc.addActiveSection(sy);
        lc.getSection(lc.getSectionIndexFromSectionY(sy)); // 数组同步，get 内部 arr[idx]=s
        if (lc.getLevel() instanceof ServerLevel sl) {
            LevelLightEngine le = sl.getChunkSource().getLightEngine();
            SectionPos pos = SectionPos.of(lc.getPos(), sy);
            if (decoded.blockLight() != null) {
                le.queueSectionData(LightLayer.BLOCK, pos, decoded.blockLight());
            }
            if (decoded.skyLight() != null) {
                le.queueSectionData(LightLayer.SKY, pos, decoded.skyLight());
            }
        }
        FarLandsGenState.setStage(lc, sy, decoded.stage());
        InfFarlands.enqueueSectionSend(lc, sy);
    }

    // ---- 工具 ----

    @SuppressWarnings("unchecked")
    private static Iterable<ChunkHolder> getChunks(ServerLevel level) {
        try {
            return (Iterable<ChunkHolder>) M_GET_CHUNKS.invoke(level.getChunkSource().chunkMap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
