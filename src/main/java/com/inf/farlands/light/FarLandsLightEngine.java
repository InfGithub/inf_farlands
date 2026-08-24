package com.inf.farlands.light;

import com.inf.farlands.Config;
import com.inf.farlands.InfFarlands;
import com.inf.farlands.IntSectionPos;
import com.inf.farlands.WindowedChunk;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.Unit;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;

/**
 * FarLands 光照引擎，替代 vanilla 的 {@code LevelLightEngine} 与
 * {@code ThreadedLevelLightEngine}。
 *
 * <p>继承 {@code ThreadedLevelLightEngine} 以与 {@code ChunkMap} 和
 * {@code ServerChunkCache} 字段类型兼容。{@code super()} 创建的 vanilla sky/block
 * 引擎不使用——所有 public 方法都被覆写，委托给 {@link FarLandsSkyLightEngine} 与
 * {@link FarLandsBlockLightEngine}。
 *
 * <p>服务端并行：服务端构造（ChunkMap 版）启用 per-chunk 任务队列 +
 * 后台传播线程池 + per-chunk 锁（半径 2）+ light ticket（任务期间 chunk 不卸载）。
 * 主线程/生成线程只入队；客户端构造保持同步直调（渲染线程每帧 runLightUpdates）。
 * 双路径通过 {@code serverSide} 区分，客户端不受影响。
 */
@SuppressWarnings({ "null" })
public class FarLandsLightEngine extends ThreadedLevelLightEngine {

    final FarLandsSkyLightEngine skyEngine;
    final FarLandsBlockLightEngine blockEngine;
    final LightChunkGetter chunkSource;

    // ---- 服务端并行（客户端为 null）----

    private final boolean serverSide;
    private final FarLandsLightQueue queue;
    private final LightTaskLock taskLock;
    private final ExecutorService lightPool;
    private final ArrayDeque<FarLandsSkyLightEngine> skyPool = new ArrayDeque<>();
    private final ArrayDeque<FarLandsBlockLightEngine> blockPool = new ArrayDeque<>();
    private final FarLandsDataLayerStorage sharedSkyStorage;
    private final FarLandsDataLayerStorage sharedBlockStorage;
    private final ConcurrentHashMap<Long, Integer> sharedSkyTopSections;

    /** Server-side constructor called via {@code ChunkMap}. */
    public FarLandsLightEngine(
            LightChunkGetter chunkSource,
            ChunkMap chunkMap,
            boolean skyLight,
            ProcessorMailbox<Runnable> taskMailbox,
            ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox) {
        super(chunkSource, chunkMap, skyLight, taskMailbox, sorterMailbox);
        this.chunkSource = chunkSource;
        this.serverSide = true;
        this.sharedBlockStorage = new FarLandsDataLayerStorage();
        this.sharedSkyStorage = new FarLandsDataLayerStorage();
        this.sharedSkyTopSections = new ConcurrentHashMap<>();
        this.blockEngine = new FarLandsBlockLightEngine(chunkSource, sharedBlockStorage);
        this.skyEngine = skyLight ? new FarLandsSkyLightEngine(chunkSource, sharedSkyStorage, sharedSkyTopSections) : null;
        this.queue = new FarLandsLightQueue();
        this.taskLock = new LightTaskLock();
        int parallelism = Config.parallelLightThreads;
        if (parallelism <= 0) {
            // 0 = 自动：CPU 逻辑线程数的一半（min 1）
            parallelism = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        }
        this.lightPool = Executors.newFixedThreadPool(parallelism, r -> {
            Thread t = new Thread(r, "farlands-light");
            t.setDaemon(true);
            return t;
        });
    }

    /** Client-side constructor called via {@code ClientChunkCache}. */
    public FarLandsLightEngine(LightChunkGetter chunkSource, boolean hasSkyLight) {
        super(chunkSource, null, hasSkyLight,
                ProcessorMailbox.create(
                        net.minecraft.Util.backgroundExecutor(), "farlands-light"),
                null);
        this.chunkSource = chunkSource;
        this.serverSide = false;
        this.blockEngine = new FarLandsBlockLightEngine(chunkSource);
        this.skyEngine = hasSkyLight ? new FarLandsSkyLightEngine(chunkSource) : null;
        this.queue = null;
        this.taskLock = null;
        this.lightPool = null;
        this.sharedSkyStorage = null;
        this.sharedBlockStorage = null;
        this.sharedSkyTopSections = null;
    }

    @Override
    public void close() {
        if (lightPool != null) {
            lightPool.shutdownNow(); // 强制中断，防池线程卡任务不退出（线程池泄漏）
        }
    }

    @Override
    protected void updateChunkStatus(ChunkPos pos) {
        // 服务端/客户端一致：同步清理该 chunk 光照层（CHM 线程安全；与传播并发为
        // 弱一致瞬态，重载/下次任务修正）。卸载不排队——chunk 即将卸载，ticket 无意义。
        blockEngine.removeChunk(pos);
        if (skyEngine != null) skyEngine.removeChunk(pos);
    }

    @Override
    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean isLighted) {
        if (serverSide) {
            ChunkPos pos = chunk.getPos();
            if (isLighted) {
                chunk.setLightCorrect(true);
                return CompletableFuture.completedFuture(chunk);
            }
            CompletableFuture<ChunkAccess> result = new CompletableFuture<>();
            FarLandsLightQueue.ChunkWork tasks = queue.queueChunkLighting(pos, () -> {
                // 播种（后台传播线程）
                FarLandsSkyLightEngine sky = getSkyForTask();
                FarLandsBlockLightEngine blk = getBlockForTask();
                try {
                    blk.propagateLightSources(pos);
                    if (skyEngine != null) {
                        sky.propagateLightSources(pos);
                    }
                    // 播种只 enqueue 光源，必须 propagate 才扩散（原同步模型靠每 tick
                    // runLightUpdates 处理队列；任务模型里播种任务结束后无人处理）。
                    blk.runPropagation();
                    if (skyEngine != null) {
                        sky.runPropagation();
                    }
                } finally {
                    releaseSky(sky);
                    releaseBlock(blk);
                }
                chunk.setLightCorrect(true);
            });
            tasks.onComplete.whenComplete((v, t) -> {
                if (t != null) {
                    result.completeExceptionally(t);
                } else if (!result.isDone()) {
                    result.complete(chunk);
                }
            });
            onEnqueued(tasks);
            return result;
        }
        if (chunk != null && !isLighted) {
            propagateLightSources(chunk.getPos());
        }
        chunk.setLightCorrect(true);
        return CompletableFuture.completedFuture(chunk);
    }

    // === LightEventListener ===

    @Override
    public void checkBlock(BlockPos pos) {
        if (serverSide) {
            onEnqueued(queue.queueBlockChange(pos));
            return;
        }
        blockEngine.checkBlock(pos);
        if (skyEngine != null) skyEngine.checkBlock(pos);
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        if (serverSide) {
            onEnqueued(queue.queueSectionChange(pos, isEmpty));
            return;
        }
        blockEngine.updateSectionStatus(pos, isEmpty);
        if (skyEngine != null) skyEngine.updateSectionStatus(pos, isEmpty);
    }

    @Override
    public boolean hasLightWork() {
        if (serverSide) {
            return queue.hasWork();
        }
        return (skyEngine != null && skyEngine.hasLightWork())
                || blockEngine.hasLightWork();
    }

    @Override
    public int runLightUpdates() {
        if (serverSide) {
            scheduleTasks();
            return 0;
        }
        int i = 0;
        i += blockEngine.runLightUpdates();
        if (skyEngine != null) i += skyEngine.runLightUpdates();
        return i;
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enabled) {
        blockEngine.setLightEnabled(pos, enabled);
        if (skyEngine != null) skyEngine.setLightEnabled(pos, enabled);
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        blockEngine.propagateLightSources(pos);
        if (skyEngine != null) skyEngine.propagateLightSources(pos);
    }

    // ==================== LevelLightEngine 覆写 ====================

    @Override
    public int getRawBrightness(BlockPos pos, int skyDarken) {
        int sky = skyEngine == null ? 0 : skyEngine.getLightValue(pos) - skyDarken;
        int block = blockEngine.getLightValue(pos);
        return Math.max(block, sky);
    }

    @Override
    public LayerLightEventListener getLayerListener(LightLayer layer) {
        if (layer == LightLayer.BLOCK) return blockEngine;
        return skyEngine != null ? skyEngine : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
    }

    @Override
    public void queueSectionData(LightLayer layer, SectionPos pos, DataLayer data) {
        if (layer == LightLayer.BLOCK) {
            if (data != null) blockEngine.setDataLayer(pos.asLong(), data, ChunkPos.asLong(pos.x(), pos.z()));
            else blockEngine.updateSectionStatus(pos, true);
        } else if (skyEngine != null) {
            if (data != null) skyEngine.setDataLayer(pos.asLong(), data, ChunkPos.asLong(pos.x(), pos.z()));
            else skyEngine.updateSectionStatus(pos, true);
        }
    }

    @Override
    public boolean lightOnInSection(SectionPos pos) {
        long key = pos.asLong();
        if (blockEngine.getDataLayer(key) != null) return true;
        if (skyEngine != null && skyEngine.getDataLayer(key) != null) return true;
        // 兜底：chunk 已加载 = 可编译（幽灵块修复）
        IntSectionPos sp = IntSectionPos.getSectionPos(key);
        return chunkSource.getChunkForLighting(sp.x, sp.z) != null;
    }

    @Override
    public void retainData(ChunkPos pos, boolean retain) {
        // 无操作 —— 单层存储无保留概念
    }

    // ==================== 新增公共 API ====================

    /** Direct access for persistence (§5 packets, ChunkSerializer). */
    public DataLayer getSkyDataLayer(SectionPos pos) {
        return skyEngine == null ? null : skyEngine.getDataLayer(pos.asLong());
    }

    /** Direct access for persistence (§5 packets, ChunkSerializer). */
    public DataLayer getBlockDataLayer(SectionPos pos) {
        return blockEngine.getDataLayer(pos.asLong());
    }

    // ==================== initializeLight（异步）====================

    @Override
    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean lightEnabled) {
        return CompletableFuture.supplyAsync(() -> {
            if (chunk instanceof WindowedChunk wc) {
                for (var e : wc.windowedAllSections().entrySet()) {
                    LevelChunkSection s = e.getValue();
                    if (s == null || s.hasOnlyAir()) continue;
                    SectionPos sp = SectionPos.of(chunk.getPos(), e.getKey());
                    blockEngine.updateSectionStatus(sp, false);
                    if (skyEngine != null) skyEngine.updateSectionStatus(sp, false);
                }
            }
            return chunk;
        });
    }

    public FarLandsLightPacketData buildLightPacket(ChunkPos pos) {
        var sky = new Int2ObjectOpenHashMap<byte[]>();
        var block = new Int2ObjectOpenHashMap<byte[]>();
        var lc = chunkSource.getChunkForLighting(pos.x, pos.z);
        // getChunkForLighting 可能返回 ChunkSerializer.read 的 ImposterProtoChunk——它的
        // allSections 只含窗口 section（构造时从窗口视图转移），极端 Y section 数据在 wrapped
        // LevelChunk（loadWindowSections 写入 ipc.getWrapped()）——必须解包取真实数据，否则
        // 打包 0 层（重进极端 Y 光照黑）。
        if (lc instanceof ImposterProtoChunk ipc) {
            lc = ipc.getWrapped();
        }
        if (!(lc instanceof WindowedChunk wc)) return new FarLandsLightPacketData(sky, block);
        for (var e : wc.windowedAllSections().entrySet()) {
            int sy = e.getKey();
            LevelChunkSection sec = e.getValue();
            if (sec == null || sec.hasOnlyAir()) continue;
            SectionPos sp = SectionPos.of(pos, sy);
            if (skyEngine != null) {
                DataLayer sl = skyEngine.getDataLayer(sp.asLong());
                // 层存在就发（含全 0 遮挡层）——只发非空层会让客户端无层，
                // sky.getLightValue 无层返回 15 → 深地下（遮挡 0）全亮。
                if (sl != null) sky.put(sy, sl.copy().getData());
            }
            DataLayer bl = blockEngine.getDataLayer(sp.asLong());
            if (bl != null) block.put(sy, bl.copy().getData());
        }
        return new FarLandsLightPacketData(sky, block);
    }

    // ==================== ChunkMap 兼容 ====================

    private boolean runningLightUpdates;

    @Override
    public void tryScheduleUpdate() {
        if (serverSide) {
            scheduleTasks();
            return;
        }
        if (!runningLightUpdates && hasLightWork()) {
            runningLightUpdates = true;
            try {
                runLightUpdates();
            } finally {
                runningLightUpdates = false;
            }
        }
    }

    @Override
    public CompletableFuture<?> waitForPendingTasks(int x, int z) {
        if (serverSide) {
            return queue.getChunkSyncFuture(x, z);
        }
        return CompletableFuture.completedFuture(null);
    }

    // ==================== 调度 / 池 / ticket ====================

    /**
     * 后台调度激活标志——保证同时只有一个 drainLight 调度循环。
     * 入队即唤醒（onEnqueued），任务完成不依赖主线程下一 tick（退出/保存等
     * 主线程忙循环场景下在途任务仍会完成，lightChunk future 落地，genRef 归零，
     * scheduleUnload 递归自愈）。
     */
    private final AtomicBoolean consumerActive = new AtomicBoolean();

    /** 唤醒后台调度循环（CAS 单例；已在跑则跳过）。 */
    private void wakeConsumer() {
        if (consumerActive.compareAndSet(false, true)) {
            try {
                lightPool.submit(this::drainLight);
            } catch (RejectedExecutionException e) {
                consumerActive.set(false); // 池已关闭，回滚激活标志
            }
        }
    }

    /**
     * 后台调度循环（池线程，单例）。poll pendingWork → takeTask → tryLock →
     * submit executeTask（执行仍在池线程，并行传播不退化）。循环到队列空；
     * 退出时 finally 重检 hasWork 再唤醒，闭合"退出瞬间入队"竞态。
     * 锁冲突 requeue 回 FIFO 队尾（自然延迟）+ yield 防自旋。
     */
    private void drainLight() {
        try {
            while (true) {
                long key = queue.nextDirty();
                if (key == Long.MIN_VALUE) {
                    break;
                }
                FarLandsLightQueue.ChunkWork tasks = queue.takeTask(key);
                if (tasks == null) {
                    continue; // 已被并发取走（takeTask 原子 remove），空转一次
                }
                int cx = tasks.chunkX();
                int cz = tasks.chunkZ();
                if (!taskLock.tryLock(cx, cz)) {
                    queue.requeue(tasks); // 相邻任务占用，重排队尾下轮重试
                    Thread.yield();
                    continue;
                }
                lightPool.submit(() -> executeTask(tasks, cx, cz));
            }
        } finally {
            consumerActive.set(false);
            if (queue.hasWork() && consumerActive.compareAndSet(false, true)) {
                lightPool.submit(this::drainLight);
            }
        }
    }

    /** 每 tick 调度入口：唤醒后台调度（限量 maxLightTasksPerTick 由后台消费替代）。 */
    private void scheduleTasks() {
        if (queue == null) {
            return;
        }
        wakeConsumer();
    }

    private void executeTask(FarLandsLightQueue.ChunkWork tasks, int cx, int cz) {
        try {
            if (tasks.isUnload) {
                blockEngine.removeChunk(new ChunkPos(cx, cz));
                if (skyEngine != null) skyEngine.removeChunk(new ChunkPos(cx, cz));
            } else {
                FarLandsSkyLightEngine sky = getSkyForTask();
                FarLandsBlockLightEngine blk = getBlockForTask();
                try {
                    if (tasks.seedTasks != null) {
                        for (Runnable r : tasks.seedTasks) {
                            r.run();
                        }
                    }
                    if (!tasks.blockChanges.isEmpty() || tasks.sectionChanges != null) {
                        blk.processBlocksChanged(cx, cz, tasks.blockChanges, tasks.sectionChanges);
                        if (skyEngine != null) {
                            sky.processBlocksChanged(cx, cz, tasks.blockChanges, tasks.sectionChanges);
                        }
                    }
                } finally {
                    releaseSky(sky);
                    releaseBlock(blk);
                }
            }
        } catch (Throwable t) {
            InfFarlands.LOGGER.error("Light task exception chunk={},{}", cx, cz, t);
        } finally {
            taskLock.unlock(cx, cz);
            tasks.onComplete.complete(null);
            // 任务完成补唤醒：防 drainLight 单例漏调度——残留任务由在跑任务的
            // 完成链式续接（light future 落地 → 生成继续 → getChunk 解除阻塞）
            wakeConsumer();
            // ticket 移除必须主线程（ScalableLux：ticket 操作非线程安全）
            mainExecutor().execute(() -> {
                if (queue.removeWorkRef(tasks.chunkKey) <= 0) {
                    ServerLevel level = (ServerLevel) chunkSource.getLevel();
                    level.getChunkSource().removeRegionTicket(
                            FarLandsLightQueue.CHUNK_WORK_TICKET,
                            new ChunkPos(cx, cz), 0, Unit.INSTANCE);
                }
            });
        }
    }

    /** 入队后加 light ticket（必须主线程；非主线程调用点如生成线程先回主线程）。 */
    private void onEnqueued(FarLandsLightQueue.ChunkWork tasks) {
        wakeConsumer(); // 入队即唤醒后台调度，不依赖主线程下一 tick
        if (tasks.isTicketAdded) {
            return;
        }
        tasks.isTicketAdded = true;
        if (mainExecutor().isSameThread()) {
            addTicketFor(tasks);
        } else {
            mainExecutor().execute(() -> addTicketFor(tasks));
        }
    }

    private void addTicketFor(FarLandsLightQueue.ChunkWork tasks) {
        long key = tasks.chunkKey;
        if (queue.addWorkRef(key) == 1) {
            ServerLevel level = (ServerLevel) chunkSource.getLevel();
            level.getChunkSource().addRegionTicket(
                    FarLandsLightQueue.CHUNK_WORK_TICKET,
                    new ChunkPos(tasks.chunkX(), tasks.chunkZ()), 0, Unit.INSTANCE);
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

    @SuppressWarnings({"resource", "unchecked"})
    private BlockableEventLoop<Runnable> mainExecutor() {
        ServerLevel level = (ServerLevel) chunkSource.getLevel();
        try {
            return (BlockableEventLoop<Runnable>) F_MAIN_EXECUTOR.get(level.getChunkSource().chunkMap);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- 引擎池（一任务一实例，共享 storage）----

    private FarLandsSkyLightEngine getSkyForTask() {
        synchronized (skyPool) {
            FarLandsSkyLightEngine e = skyPool.pollFirst();
            if (e != null) {
                return e;
            }
        }
        return new FarLandsSkyLightEngine(chunkSource, sharedSkyStorage, sharedSkyTopSections);
    }

    private void releaseSky(FarLandsSkyLightEngine e) {
        synchronized (skyPool) {
            skyPool.addFirst(e);
        }
    }

    private FarLandsBlockLightEngine getBlockForTask() {
        synchronized (blockPool) {
            FarLandsBlockLightEngine e = blockPool.pollFirst();
            if (e != null) {
                return e;
            }
        }
        return new FarLandsBlockLightEngine(chunkSource, sharedBlockStorage);
    }

    private void releaseBlock(FarLandsBlockLightEngine e) {
        synchronized (blockPool) {
            blockPool.addFirst(e);
        }
    }
}