package com.inf.farlands.light;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongPriorityQueue;
import it.unimi.dsi.fastutil.longs.LongPriorityQueues;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;

/**
 * 服务端光照传播任务队列，per-chunk 并行。
 *
 * 方块变化/section 变化/chunk 点亮按 chunk 合并为 {@link ChunkWork}，主线程只入队，
 * 后台传播线程池按 chunk 取任务执行。{@code onComplete} 供生成管线/发送前等待。
 *
 * 线程安全：{@code workMap} 用 ConcurrentHashMap，computeIfAbsent 原子创建，
 * 每个 ChunkWork 的 isQueued/isExecuting 状态用该任务对象自身同步；
 * {@code pendingWork} 用 fastutil synchronized 队列。入队/取可在任意线程。
 */
public final class FarLandsLightQueue {

    /** 任务期间保持 chunk 加载的 ticket，半径 0，只保加载不卸载，不强制生成。 */
    public static final TicketType<Unit> CHUNK_WORK_TICKET =
            TicketType.create("inf_farlands_light", (a, b) -> 0, 0);

    /** chunk key → 未完成任务。任务执行时被 takeTask 移除；重新入队经 requeue 放回。 */
    private final com.inf.farlands.util.Long2ObjectStripedMap<ChunkWork> workMap =
            new com.inf.farlands.util.Long2ObjectStripedMap<>(1 << 12);
    private final LongPriorityQueue pendingWork = LongPriorityQueues.synchronize(new LongArrayFIFOQueue());
    /** chunk key → 未完成 light 任务引用计数，决定 ticket 生命周期，见 addWorkRef/removeWorkRef。 */
    private final com.inf.farlands.util.Long2ObjectStripedMap<AtomicInteger> workRefs =
            new com.inf.farlands.util.Long2ObjectStripedMap<>(1 << 10);

    public boolean isEmpty() {
        return workMap.isEmpty();
    }

    public boolean hasWork() {
        return !pendingWork.isEmpty();
    }

    // ==================== enqueue：任意线程，线程安全 ====================

    public ChunkWork queueBlockChange(BlockPos pos) {
        return mergeWork(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4),
                tasks -> tasks.blockChanges.add(pos.immutable()));
    }

    public ChunkWork queueSectionChange(SectionPos pos, boolean isEmpty) {
        return mergeWork(ChunkPos.asLong(pos.x(), pos.z()), tasks -> {
            if (tasks.sectionChanges == null) {
                tasks.sectionChanges = new HashMap<>();
            }
            tasks.sectionChanges.put(pos.y(), isEmpty);
        });
    }

    public ChunkWork queueChunkLighting(ChunkPos pos, Runnable lightTask) {
        return mergeWork(ChunkPos.asLong(pos.x, pos.z), tasks -> {
            if (tasks.seedTasks == null) {
                tasks.seedTasks = new ArrayList<>();
            }
            tasks.seedTasks.add(lightTask);
        });
    }

    public ChunkWork queueChunkUnload(ChunkPos pos) {
        return mergeWork(ChunkPos.asLong(pos.x, pos.z), tasks -> tasks.isUnload = true);
    }

    /**
     * 合并入队：同 chunk 已存在任务则并入。正在执行的任务已被 takeTask 移除，
     * computeIfAbsent 会新建——retry 循环不冲突，因为旧任务 isExecuting 时重试取到新任务。
     */
    private ChunkWork mergeWork(long key, Consumer<ChunkWork> action) {
        while (true) {
            ChunkWork tasks = workMap.computeIfAbsent(key, ChunkWork::new);
            synchronized (tasks) {
                if (tasks.isExecuting) {
                    continue;
                }
                action.accept(tasks);
                if (!tasks.isQueued) {
                    tasks.isQueued = true;
                    pendingWork.enqueue(key);
                }
                return tasks;
            }
        }
    }

    // ==================== consume：调度，主线程串行 ====================

    /** 取下一个有任务的 chunk key；无则 Long.MIN_VALUE。 */
    public long nextDirty() {
        synchronized (pendingWork) {
            return pendingWork.isEmpty() ? Long.MIN_VALUE : pendingWork.dequeueLong();
        }
    }

    /** P2：玩家位置变化 → 队列按距本维度最近玩家距离重排，近的先处理；原 FIFO 远处先入队先处理，
     * 玩家附近光照延后。无分配 comparator，ChunkPos.getX/Z 为静态。与 nextDirty 同锁互斥。
     * 动态收集用 LongArrayList——不用 size() 预分配数组，并发入队时 size 竞态 → 越界/
     * 少放回 → 任务丢 → inFlight 永久 true → §5 滞留 → 空缺。
     * 优先级 = 距任务所在维度（本 engine 绑定 level）最近玩家的 Chebyshev 距离——多玩家
     * 语义与 GenTask.computePriority 一致（去单玩家快照，多人兼容追踪）。 */
    public void rebuildQueue(ServerLevel level) {
        synchronized (pendingWork) {
            if (pendingWork.isEmpty()) {
                return;
            }
            LongArrayList keys = new LongArrayList();
            while (!pendingWork.isEmpty()) {
                keys.add(pendingWork.dequeueLong());
            }
            long[] arr = keys.toLongArray();
            LongArrays.quickSort(arr, 0, arr.length, (a, b) ->
                    Integer.compare(nearestPlayerDist(level, a), nearestPlayerDist(level, b)));
            for (long k : arr) {
                pendingWork.enqueue(k);
            }
        }
    }

    /** 任务 chunk 到本维度最近玩家的 Chebyshev 距离；无玩家 → 0（FIFO 退化）。 */
    private static int nearestPlayerDist(ServerLevel level, long chunkKey) {
        int cx = ChunkPos.getX(chunkKey);
        int cz = ChunkPos.getZ(chunkKey);
        int best = Integer.MAX_VALUE;
        for (ServerPlayer p : level.players()) {
            ChunkPos pc = p.chunkPosition();
            int d = Math.max(Math.abs(cx - pc.x), Math.abs(cz - pc.z));
            if (d < best) {
                best = d;
            }
        }
        return best == Integer.MAX_VALUE ? 0 : best;
    }

    /** 从 map 取走任务并标记执行中。 */
    public ChunkWork takeTask(long key) {
        ChunkWork tasks = workMap.remove(key);
        if (tasks == null) {
            return null;
        }
        synchronized (tasks) {
            tasks.isExecuting = true;
        }
        return tasks;
    }

    /** 锁冲突时放回队列，下轮重试。若执行期间已有同 chunk 新任务，合并内容，不覆盖。 */
    public void requeue(ChunkWork tasks) {
        synchronized (tasks) {
            tasks.isExecuting = false;
            tasks.isQueued = true;
        }
        ChunkWork existing = workMap.putIfAbsent(tasks.chunkKey, tasks);
        if (existing != null) {
            synchronized (existing) {
                existing.blockChanges.addAll(tasks.blockChanges);
                if (tasks.sectionChanges != null) {
                    if (existing.sectionChanges == null) {
                        existing.sectionChanges = new HashMap<>();
                    }
                    existing.sectionChanges.putAll(tasks.sectionChanges);
                }
                if (tasks.seedTasks != null) {
                    if (existing.seedTasks == null) {
                        existing.seedTasks = new ArrayList<>();
                    }
                    existing.seedTasks.addAll(tasks.seedTasks);
                }
                existing.isUnload |= tasks.isUnload;
                if (!existing.isQueued) {
                    existing.isQueued = true;
                    pendingWork.enqueue(existing.chunkKey);
                }
                // 修复 requeue 合并丢 onComplete：tasks 即本任务被合并进 existing 后丢弃——
                // 但等 tasks.onComplete 的 lightChunk future 经 fillFrom→lightChunk→
                // whenComplete→LIGHTDONE→inFlight 释放→§5 放行链，没人 complete → 永久卡
                // → §5 滞留 → 空缺
                // "最后只剩几个"。existing 执行完成时链式 complete tasks.onComplete。
                existing.onComplete.whenComplete((v, t) -> tasks.onComplete.complete(null));
            }
        } else {
            pendingWork.enqueue(tasks.chunkKey);
        }
    }

    /** 按 chunk 查询未完成任务，发送前等待光照完成用。 */
    public CompletableFuture<Void> getChunkSyncFuture(int chunkX, int chunkZ) {
        ChunkWork tasks = workMap.get(ChunkPos.asLong(chunkX, chunkZ));
        return tasks == null ? CompletableFuture.completedFuture(null) : tasks.onComplete;
    }

    // ==================== ticket 引用计数 ====================

    /** 任务入队后 +1，返回新计数，==1 表示首个任务，应加 ticket。 */
    public int addWorkRef(long key) {
        return workRefs.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }

    /** 任务完成后 -1，返回新计数，<=0 表示无剩余任务，应移除 ticket。 */
    public int removeWorkRef(long key) {
        AtomicInteger refs = workRefs.get(key);
        if (refs == null) {
            return 0;
        }
        int v = refs.decrementAndGet();
        if (v <= 0) {
            workRefs.remove(key);
        }
        return v;
    }

    // ==================== ChunkWork ====================

    public static final class ChunkWork {
        public final long chunkKey;
        public final Set<BlockPos> blockChanges = new ObjectOpenHashSet<>();
        /** 绝对 sectionY → 是否空。null = 无变化。 */
        public Map<Integer, Boolean> sectionChanges;
        public List<Runnable> seedTasks;
        public boolean isUnload;
        public boolean isQueued;
        public boolean isExecuting;
        public boolean isTicketAdded;
        public final CompletableFuture<Void> onComplete = new CompletableFuture<>();

        public ChunkWork(long chunkKey) {
            this.chunkKey = chunkKey;
        }

        public int chunkX() {
            return ChunkPos.getX(this.chunkKey);
        }

        public int chunkZ() {
            return ChunkPos.getZ(this.chunkKey);
        }
    }
}