package com.inf.farlands.terrain.pipeline;

import com.inf.farlands.terrain.noisefiller.OverworldNoiseFiller;

import com.inf.farlands.Config;
import com.inf.farlands.Constants;
import com.inf.farlands.window.EntitySectionWindow;
import com.inf.farlands.InfFarlands;
import com.inf.farlands.light.FarLandsLightEngine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * 地形管线生成任务队列，静态类。per-section 瞬态任务，全局并发队列，4 worker
 * 异步并发消费，drainGen 仿光照 drainLight，调度单例 CAS + 任务 submit 到池。
 *
 * 每次唤醒 submit ≤ {@link Config#maxGenTasksPerTick}，宽松防风暴。唤醒源 =
 * onServerTick 的 tick() 与入队的 enqueue。submit 后任务在池并发执行。
 *
 * 光照衔接按 chunk 级去重：GenTask 只推进 NOISE，随后 notifyGenerated 触发一次
 * fillFrom + lightChunk。每 chunk 每批次一次，CAS 在途标志去重，不再每 section
 * 重复播种。光照完成回调：promoteAllGenToLighted 全 NOISE→LIGHTED，播种覆盖
 * 全 chunk，然后释放在途，hasAnyGen 再检查驱动下一批。
 */
@SuppressWarnings("null")
public final class GenQueue {

    /** 生成任务队列：按距最近玩家距离优先级，近先生成；synchronized 保护，PriorityQueue 非线程安全。 */
    private static final PriorityQueue<GenTask> QUEUE = new PriorityQueue<>(Comparator.comparingInt(GenTask::priority));

    // ---- P2 动态优先级 ----
    // 队列按距最近玩家距离排序（GenTask.computePriority 遍历任务所在维度全部玩家，实时）。
    // 玩家移动后入队快照旧——由 onServerTick 检测任一玩家 chunk 变化后调 rebuildQueue
    // 重算全部任务 priority + 重新堆化（多玩家全量触发，无单玩家快照）。

    /** P2：玩家位置变化 → 全部在途任务按当前距离重排，修复快照旧。 */
    public static void rebuildQueue() {
        synchronized (QUEUE) {
            if (QUEUE.isEmpty()) {
                return;
            }
            List<GenTask> all = new ArrayList<>(QUEUE);
            QUEUE.clear();
            for (GenTask t : all) {
                t.refreshPriority(); // 按当前玩家位置重算
                QUEUE.offer(t);      // 重新堆化
            }
        }
    }

    /** 生成 worker 线程数按 Config.genWorkerThreads：0 = 自动 CPU 逻辑线程一半，1 = 单线程，N = 恰好 N。 */
    private static int genWorkerCount() {
        int n = Config.genWorkerThreads;
        if (n <= 0) {
            n = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        }
        return n;
    }

    private static final ExecutorService POOL = Executors.newFixedThreadPool(genWorkerCount(), r -> {
        Thread t = new Thread(r, "farlands-gen");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicBoolean consumerActive = new AtomicBoolean();

    /** 每 chunk 光照在途标志，chunkKey → CAS；卸载残留接受，量小。 */
    private static final ConcurrentHashMap<Long, AtomicBoolean> LIGHT_IN_FLIGHT = new ConcurrentHashMap<>();

    /** 每 chunk 生成在途标志，chunkKey → CAS，同 chunk 至多一个生成任务，防并发写 heightmap。 */
    private static final ConcurrentHashMap<Long, AtomicBoolean> CHUNK_IN_FLIGHT = new ConcurrentHashMap<>();

    /** 三维度 NoiseFiller 惰性缓存，settings/fluidPicker 从各维度第一个 level 拿。 */
    private static volatile OverworldNoiseFiller overworldFiller;
    private static volatile com.inf.farlands.terrain.noisefiller.TheNetherNoiseFiller netherFiller;
    private static volatile com.inf.farlands.terrain.noisefiller.TheEndNoiseFiller endFiller;

    private GenQueue() {
    }

    /** 惰性取维度 NoiseFiller，来自该维度第一个 ServerLevel。 */
    static com.inf.farlands.terrain.noisefiller.NoiseFiller filler(ServerLevel level) {
        if (level.dimension() == net.minecraft.world.level.Level.NETHER) {
            com.inf.farlands.terrain.noisefiller.TheNetherNoiseFiller f = netherFiller;
            if (f == null) {
                synchronized (GenQueue.class) {
                    f = netherFiller;
                    if (f == null) {
                        f = com.inf.farlands.terrain.noisefiller.TheNetherNoiseFiller.of(level);
                        netherFiller = f;
                    }
                }
            }
            return f;
        }
        if (level.dimension() == net.minecraft.world.level.Level.END) {
            com.inf.farlands.terrain.noisefiller.TheEndNoiseFiller f = endFiller;
            if (f == null) {
                synchronized (GenQueue.class) {
                    f = endFiller;
                    if (f == null) {
                        f = com.inf.farlands.terrain.noisefiller.TheEndNoiseFiller.of(level);
                        endFiller = f;
                    }
                }
            }
            return f;
        }
        OverworldNoiseFiller f = overworldFiller;
        if (f == null) {
            synchronized (GenQueue.class) {
                f = overworldFiller;
                if (f == null) {
                    f = OverworldNoiseFiller.of(level);
                    overworldFiller = f;
                }
            }
        }
        return f;
    }

    // ---- 主线程：触发入队 ----

    /** fill 任务期间保持 chunk 加载的 ticket，radius 0 → ticketLevel 33 = FULL，只保加载不卸载。
     * 与 light 的 CHUNK_WORK_TICKET 同型不同类——避免同 chunk 双 ticket 引用计数混淆。
     * 根因：fill 在途无 ticket 时 chunk 可被卸载 → fill 完成后的 light 播种错对象/空播种 →
     * 补触发写盘无光照 + stage=LIGHTED → 重进光照永久缺失，即竞态。 */
    private static final TicketType<Unit> GEN_WORK_TICKET =
            TicketType.create("inf_farlands_gen", (a, b) -> 0, 0);

    /** 主线程：fill 在途保加载，enqueue/enqueueChunk CAS 成功后调用，chunk 必在 holder。
     * ticket 操作非线程安全，DistanceManager.tickets 主线程独占，调用点保证主线程。 */
    private static void addGenTicket(LevelChunk chunk) {
        ServerLevel sl = (ServerLevel) chunk.getLevel();
        sl.getChunkSource().addRegionTicket(GEN_WORK_TICKET, chunk.getPos(), 0, Unit.INSTANCE);
    }

    /** genPool 线程：移除 fill ticket，经 SectionIO.runOnMainThread 回主线程 executor——
     * 异步延迟 = ticket 多存活一会儿，保守方向无害。 */
    private static void removeGenTicket(LevelChunk chunk) {
        ServerLevel sl = (ServerLevel) chunk.getLevel();
        com.inf.farlands.serialize.SectionIO.runOnMainThread(
                () -> sl.getChunkSource().removeRegionTicket(GEN_WORK_TICKET, chunk.getPos(), 0, Unit.INSTANCE),
                sl);
    }

    /** Y 触发：主线程上的单 section 请求。幂等：已生成不入队；入队粒度 = chunk 级任务。 */
    public static void enqueue(LevelChunk chunk, int sectionY) {
        if (FarLandsGenState.isOrAfter(chunk, sectionY, FarLandsGenState.NOISE)) {
            return;
        }
        // fsa 读回在途：该 section 正在从磁盘恢复——完成回调会再调 enqueue，由 isOrAfter 跳过
        if (com.inf.farlands.serialize.SectionIO.isReading(chunk.getPos().toLong(), sectionY)) {
            return;
        }
        long key = chunk.getPos().toLong();
        if (CHUNK_IN_FLIGHT.computeIfAbsent(key, k -> new AtomicBoolean()).compareAndSet(false, true)) {
            addGenTicket(chunk); // fill 在途保加载，防卸载 → light 播种错对象竞态
            synchronized (QUEUE) {
                QUEUE.add(new GenTask(chunk));
            }
            wakeConsumer();
        }
        // 已在途：不重复入队——在途任务 execute 扫窗口并集，含此 section；execute 后新入队
        // 的由 completeTask 再检查兜底
    }

    /** XZ 触发：chunk 在主线程短路完成 → 处于任一玩家视距或外圈内才入队。
     *  过滤视距外 chunk：Beardifier.getChunk(STRUCTURE_REFERENCES, require=true) 强制加载
     *  远处触发短路 → 不入队，防洪水级联 + 客户端 0 层黑。
     *  距离判断不依赖 tracking view 时序：玩家快速移动时 tracking view 每 tick 才更新，
     *  新加载 chunk 若此刻不在 tracking 内会永远错过入队，跑动时前方完全不生成、停下 burst——
     *  chunk 能加载 = 视距 ticket 驱动 = 必然在视距内，直接按玩家坐标算距离。 */
    public static void enqueueChunk(LevelChunk chunk) {
        if (!isNearPlayer(chunk)) {
            return; // 视距外：不入队，远处语义=未生成
        }
        long key = chunk.getPos().toLong();
        if (CHUNK_IN_FLIGHT.computeIfAbsent(key, k -> new AtomicBoolean()).compareAndSet(false, true)) {
            addGenTicket(chunk); // fill 在途保加载，防卸载 → light 播种错对象竞态
            synchronized (QUEUE) {
                QUEUE.add(new GenTask(chunk));
            }
            wakeConsumer();
        }
    }

    /** 预加载：出生点指定 section 范围入队，绕过 tracking view 过滤，玩家未加入；
     * 幂等：范围内全已生成，isOrAfter GEN 则不入队。fill 在途 ticket 保加载。 */
    public static void preload(LevelChunk chunk, int minSy, int maxSy) {
        boolean anyPending = false;
        for (int sy = minSy; sy <= maxSy; sy++) {
            if (sy > Constants.MAX_CHUNK - 1 || sy < -Constants.MAX_CHUNK) {
                continue;
            }
            if (!FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.NOISE)) {
                anyPending = true;
                break;
            }
        }
        if (!anyPending) {
            return;
        }
        long key = chunk.getPos().toLong();
        if (CHUNK_IN_FLIGHT.computeIfAbsent(key, k -> new AtomicBoolean()).compareAndSet(false, true)) {
            addGenTicket(chunk); // fill 在途保加载
            int[] range = new int[maxSy - minSy + 1];
            for (int i = 0; i < range.length; i++) {
                range[i] = minSy + i;
            }
            synchronized (QUEUE) {
                QUEUE.add(new GenTask(chunk, range));
            }
            wakeConsumer();
        }
    }

    /** 该 chunk 是否在任一玩家的视距或外圈 1 内——直接距离判断，
     *  不依赖 tracking view，其每 tick 更新，快速移动时会滞后错过入队。 */
    private static boolean isNearPlayer(LevelChunk chunk) {
        Level level = chunk.getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return false;
        }
        ChunkPos cp = chunk.getPos();
        for (ServerPlayer p : sl.players()) {
            ChunkTrackingView view = p.getChunkTrackingView();
            int viewDistance = view instanceof ChunkTrackingView.Positioned pos ? pos.viewDistance() : 8;
            if (ChunkTrackingView.isWithinDistance(
                    p.chunkPosition().x, p.chunkPosition().z, viewDistance, cp.x, cp.z, true)) {
                return true;
            }
        }
        return false;
    }

    /** execute 完成回调：清在途 + 再检查窗口并集内未 NOISE，覆盖 execute 期间新入队 section。
     * 连续任务 → ticket 保持，下一批 fill 仍在途；无未处理 → genPool 线程释放 fill ticket。
     * 在途条目 remove 而非 set(false)：任务结束后条目不永存，防随探索单调增长。 */
    static void completeTask(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        CHUNK_IN_FLIGHT.remove(key);
        if (hasUnprocessed(chunk)) {
            if (CHUNK_IN_FLIGHT.computeIfAbsent(key, k -> new AtomicBoolean()).compareAndSet(false, true)) {
                synchronized (QUEUE) {
                    QUEUE.add(new GenTask(chunk));
                }
                wakeConsumer();
            }
            // 连续任务：ticket 保持不 remove，下一批 fill 仍在途；重新入队不走 enqueue，
            // 依赖 ticket 未移除；主线程并发 enqueue 时 CAS 竞争无论胜负 ticket 保持或已移除
        } else {
            removeGenTicket(chunk); // fill 工作结束 → 释放保加载，该 chunk 可正常卸载
        }
    }

    /** 该 chunk 在窗口并集内是否仍有未 NOISE section，clamp 段顶防溢出。 */
    private static boolean hasUnprocessed(LevelChunk chunk) {
        boolean[] found = new boolean[1];
        EntitySectionWindow.forEachSectionInAnyWindow(sy -> {
            if (sy > Constants.MAX_CHUNK - 1 || sy < -Constants.MAX_CHUNK) {
                return;
            }
            if (!FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.NOISE)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /** onServerTick 每 tick 唤醒，submit 一批。 */
    public static void tick() {
        wakeConsumer();
    }

    /** 每 tick 扫描入队预算，渐进：视距内未生成 chunk 分批补，不 burst。 */
    private static final int SCAN_BUDGET = 32;

    /**
     * 动态扫描治本：每 tick 从每个玩家当前位置螺旋向外扫描视距及外圈内的
     * chunk，未生成的按当前距离近先入队，生成顺序天然跟随玩家位置：
     * 覆盖"入队快照 priority 旧"与"一次性触发错过"两个缺陷——前者是队列顺序
     * 不随玩家移动更新，后者是 enqueueChunk 只在加载时、Beardifier 远载/时序
     * 错过无补，玩家停空 chunk 5 分钟不生成、飞出去才触发。
     * budget 限量防风暴：per-player 配额（总量 SCAN_BUDGET 均分），
     * 多人/多维度下后遍历玩家不饥饿（全局共享会被第一个玩家耗尽）。
     */
    public static void scanAndEnqueue(MinecraftServer server) {
        int totalPlayers = 0;
        for (ServerLevel level : server.getAllLevels()) {
            totalPlayers += level.players().size();
        }
        int perPlayer = Math.max(1, SCAN_BUDGET / Math.max(1, totalPlayers));
        for (ServerLevel level : server.getAllLevels()) {
            for (ServerPlayer p : level.players()) {
                int budget = perPlayer;
                ChunkPos pc = p.chunkPosition();
                ChunkTrackingView viewObj = p.getChunkTrackingView();
                int view = viewObj instanceof ChunkTrackingView.Positioned pos ? pos.viewDistance() : 8;
                for (int d = 0; d <= view + 1 && budget > 0; d++) {
                    if (d == 0) {
                        if (scanChunk(level, pc.x, pc.z)) {
                            budget--;
                        }
                        continue;
                    }
                    for (int x = -d; x <= d && budget > 0; x++) {
                        if (scanChunk(level, pc.x + x, pc.z - d) && --budget <= 0) {
                            break;
                        }
                        if (scanChunk(level, pc.x + x, pc.z + d) && --budget <= 0) {
                            break;
                        }
                    }
                    for (int z = -d + 1; z <= d - 1 && budget > 0; z++) {
                        if (scanChunk(level, pc.x - d, pc.z + z) && --budget <= 0) {
                            break;
                        }
                        if (scanChunk(level, pc.x + d, pc.z + z) && --budget <= 0) {
                            break;
                        }
                    }
                }
            }
        }
    }

    /** 扫描单个 chunk：已加载 LevelChunk + 窗口并集内有未 NOISE section 或 surface/carvers
     * 待处理（已 NOISE 未 SURFACE / 已 SURFACE 未 CARVERS，读回迁移/失败残留）→ enqueueChunk，幂等。 */
    private static boolean scanChunk(ServerLevel level, int cx, int cz) {
        ChunkAccess ca = level.getChunk(cx, cz, ChunkStatus.FULL, false);
        if (!(ca instanceof LevelChunk lc)) {
            return false; // 未加载，ticket 驱动中：下 tick 再扫
        }
        long key = lc.getPos().toLong();
        AtomicBoolean inflight = CHUNK_IN_FLIGHT.get(key);
        if (inflight != null && inflight.get()) {
            return false; // 在途：等当前任务完成，completeTask 会再检查
        }
        if (!hasUnprocessed(lc)
                && !com.inf.farlands.terrain.surfaceFiller.SurfaceFiller.hasSurfacePending(lc)
                && !com.inf.farlands.terrain.carverFiller.CarverFiller.hasCarversPending(lc)) {
            return false; // 窗口并集内全已生成且无 surface/carvers 待处理
        }
        enqueueChunk(lc); // CAS + ticket + QUEUE，幂等
        return true;
    }

    // ---- 光照衔接：chunk 级去重 ----

    /** 该 chunk 是否有生成或光照任务在途，供 fsa 清理判定——在途则不清理该 chunk，保守。 */
    public static boolean isChunkBusy(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        AtomicBoolean gen = CHUNK_IN_FLIGHT.get(key);
        if (gen != null && gen.get()) {
            return true;
        }
        AtomicBoolean light = LIGHT_IN_FLIGHT.get(key);
        return light != null && light.get();
    }

    /** 报告某 section 已变 GEN → 触发光照，该 chunk 无在途光照时一次。 */
    public static void notifyGenerated(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        if (LIGHT_IN_FLIGHT.computeIfAbsent(key, k -> new AtomicBoolean()).compareAndSet(false, true)) {
            triggerLight(chunk);
        }
    }

    /** 该 chunk 是否有光照任务在途，§5 发包前检查——在途则留队列，等播种完成带正确光照。 */
    public static boolean isLightInFlight(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        AtomicBoolean inFlight = LIGHT_IN_FLIGHT.get(key);
        return inFlight != null && inFlight.get();
    }

    /** 一次 fillFrom + lightChunk；完成回调 promoteAll + 释放 + hasAnyGen 再检查。 */
    private static void triggerLight(LevelChunk chunk) {
        long key = chunk.getPos().toLong();
        Level level = chunk.getLevel();
        if (level instanceof ServerLevel serverLevel
                && serverLevel.getChunkSource().getLightEngine() instanceof ThreadedLevelLightEngine lightEngine) {
            try {
                chunk.initializeLightSources();
            } catch (Exception e) {
                InfFarlands.LOGGER.error("TRIGGER fillFrom ex chunk={},{} {}",
                        chunk.getPos().x, chunk.getPos().z, e.toString());
                throw e;
            }
            if (lightEngine instanceof FarLandsLightEngine fle) {
                // fillFrom 完成 → 触发播种时登记等待本 chunk 的邻居重播，修正边界方向位
                fle.onChunkSkySourcesReady(chunk.getPos());
            }
            lightEngine.lightChunk(chunk, false).whenComplete((c, t) -> {
                if (t == null) {
                    FarLandsGenState.promoteAllGenToLighted(chunk);
                    // A+B：光照完成 → 该 chunk 脏 section 入队 PERSIST，lightPool 线程 add，
                    // pendingEncode 线程安全——fill+光照已完成，数据完整，不再落盘半成品/旧光照；
                    // 卸载时在途的 chunk 也由此兜底写盘，数据在强引用 chunk 内存，完整。
                    com.inf.farlands.serialize.SectionLifecycle.persistChunkDirty(chunk);
                    // 播种完成 → 主动广播该 chunk 全部光照，含空 sec 的 15：
                    // 增量包链即 sectionLightChanged 收集 + broadcastChanges 发送，依赖 chunk 达
                    // ENTITY_TICKING，而空壳先发+光照后补的管线里播种时往往未达 → 客户端
                    // 永远收不到播种后的光照，保持邻居传播写入的渐黑 → 空 sec 黑。主动广播
                    // 绕开 ticking 依赖，播种完成即发，lightPool 线程 → 回主线程。
                    com.inf.farlands.serialize.SectionIO.runOnMainThread(
                            () -> InfFarlands.broadcastChunkLight(
                                    (ServerLevel) chunk.getLevel(), chunk),
                            (ServerLevel) chunk.getLevel());
                } else {
                    InfFarlands.LOGGER.error("farlands: light failed chunk={}", chunk.getPos(), t);
                }
                LIGHT_IN_FLIGHT.remove(key);
                if (FarLandsGenState.hasAnyGen(chunk)) {
                    notifyGenerated(chunk); // 光照期间新 NOISE 未被播种覆盖 → 下一批
                }
            });
        } else {
            // 防御：非服务端/非我们的光引擎——释放标志防卡死，理论上不走
            LIGHT_IN_FLIGHT.remove(key);
        }
    }

    // ---- 关服等待：A+B ----

    /** 全局是否仍有生成/光照在途，CHUNK_IN_FLIGHT/LIGHT_IN_FLIGHT 任一 true 或生成队列非空。 */
    public static boolean hasInflightWork() {
        for (AtomicBoolean b : CHUNK_IN_FLIGHT.values()) {
            if (b.get()) {
                return true;
            }
        }
        for (AtomicBoolean b : LIGHT_IN_FLIGHT.values()) {
            if (b.get()) {
                return true;
            }
        }
        synchronized (QUEUE) {
            return !QUEUE.isEmpty();
        }
    }

    /** 关服等待：等全局生成/光照在途收敛，有界。每轮唤醒消费，drainGen budget 消费不完
     * 不续唤醒——滞留任务可能空等；超时由 shutdownSyncFlush 的 isChunkBusy 跳过兜底。 */
    public static void awaitIdle(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            wakeConsumer(); // 消费 QUEUE 滞留任务，CAS 单例，已在跑则跳过，无开销
            if (!hasInflightWork()) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    // ---- 调度 ----

    private static void wakeConsumer() {
        if (consumerActive.compareAndSet(false, true)) {
            try {
                POOL.submit(GenQueue::drainGen);
            } catch (RejectedExecutionException e) {
                consumerActive.set(false); // 池已关闭，回滚激活标志
            }
        }
    }

    private static void drainGen() {
        try {
            int budget = Config.maxGenTasksPerTick;
            for (int i = 0; i < budget; i++) {
                GenTask task;
                synchronized (QUEUE) {
                    task = QUEUE.poll();
                }
                if (task == null) {
                    break;
                }
                POOL.submit(task::execute); // 任务并发执行，4 worker
            }
        } catch (RejectedExecutionException e) {
            // 池关闭时剩余任务丢弃，daemon 不主动关，理论不触发
        } finally {
            consumerActive.set(false);
        }
    }
}