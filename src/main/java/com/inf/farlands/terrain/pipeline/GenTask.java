package com.inf.farlands.terrain.pipeline;

import com.inf.farlands.Constants;
import com.inf.farlands.window.EntitySectionWindow;
import com.inf.farlands.InfFarlands;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * chunk 级生成任务，状态转换批：一个任务 = 一个 chunk，execute 时收集玩家窗口
 * 并集内该 chunk 未生成的 section，按连续段分组，每段一个 NoiseChunk 填充，
 * 批量 fill 的固定成本从 per-section ×34 降到每段 1 次。
 *
 * 瞬态：触发即建、执行即毁。持 LevelChunk 强引用以防 GC。fill 在途由
 * GenQueue.GEN_WORK_TICKET 保加载，ticketLevel 33 = FULL，chunk 不卸载，
 * light 播种对象始终正确，竞态根治，见 GenQueue 注释。
 *
 * 并发：同 chunk 至多一个任务在途，GenQueue.CHUNK_IN_FLIGHT CAS 去重，因此
 * fill 无并发写 heightmap，无需 synchronized(chunk)；不同 chunk 并行，4 worker。
 *
 * 执行后 GenQueue.completeTask 清在途 + 再检查窗口并集内未 NOISE，覆盖 execute 期间
 * 新入队的 section，保证窗口并集内未生成最终被清空。
 */
public final class GenTask {

    private final LevelChunk chunk;
    /** 预加载模式：指定 section 集合；null = 窗口模式，collectSegments 用玩家窗口并集。 */
    private final int[] preloadSections;
    /** 该 chunk 到最近玩家的 XZ Chebyshev 距离，来自入队快照，P2 玩家移动时 refreshPriority 重算。
     * GenQueue PriorityQueue 按此排序，近先生成。 */
    private int priority;

    public GenTask(LevelChunk chunk) {
        this(chunk, null);
    }

    /** 预加载构造：指定 section 范围，出生点预加载用，绕过窗口/tracking view。 */
    public GenTask(LevelChunk chunk, int[] preloadSections) {
        this.chunk = chunk;
        this.preloadSections = preloadSections;
        this.priority = computePriority(chunk);
    }

    /** 距最近玩家距离：无玩家时返回 0，退化为 FIFO。 */
    int priority() {
        return this.priority;
    }

    /** P2：玩家位置变化时由 GenQueue.rebuildQueue 调用重算 priority，修复快照旧。 */
    void refreshPriority() {
        this.priority = computePriority(this.chunk);
    }

    private static int computePriority(LevelChunk chunk) {
        if (chunk.getLevel() instanceof ServerLevel sl) {
            ChunkPos cp = chunk.getPos();
            int best = Integer.MAX_VALUE;
            for (ServerPlayer p : sl.players()) {
                ChunkPos pp = p.chunkPosition();
                int d = Math.max(Math.abs(cp.x - pp.x), Math.abs(cp.z - pp.z));
                if (d < best) {
                    best = d;
                }
            }
            return best == Integer.MAX_VALUE ? 0 : best;
        }
        return 0;
    }

    public void execute() {
        try {
            Level level = chunk.getLevel();
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            List<int[]> segments = collectSegments();
            if (!segments.isEmpty()) {
                for (int[] seg : segments) {
                    try {
                        GenQueue.filler(serverLevel).fill(serverLevel, chunk, seg[0], seg[1]);
                    } catch (Exception e) {
                        com.inf.farlands.InfFarlands.LOGGER.error(
                                "GENTASK fill ex chunk={},{} {}", chunk.getPos().x, chunk.getPos().z, e.toString());
                        throw e;
                    }
                    for (int sy = seg[0]; sy <= seg[1]; sy++) {
                        FarLandsGenState.setStage(chunk, sy, FarLandsGenState.NOISE);
                        // fsa 脏标记：fill 在 genPool 线程写 section 内容，CHM 安全
                        ((com.inf.farlands.window.WindowedChunk) chunk).markSectionDirty(sy);
                    }
                }
            }
            // SURFACE 阶段独立于 segments（补触发/失败重试入口）：fill 后紧跟（heightmap 依赖，
            // 同 chunk 串行），也覆盖读回 stage=NOISE 与 surface 失败残留。失败不抛——section
            // 停留 NOISE 由 scanAndEnqueue 补触发重试；不触发光照——promoteAllGenToLighted
            // 会把 NOISE 升 LIGHTED，抹掉 surface 待处理标志。
            try {
                // 返回值不消费：surface 升段已含在后续 carvers 升段（fill→surface→carvers 链），
                // 光照/发送只依赖 carved
                com.inf.farlands.terrain.surfaceFiller.SurfaceFiller.applySurfaceIfNeeded(
                        serverLevel, chunk);
            } catch (Exception e) {
                com.inf.farlands.InfFarlands.LOGGER.error(
                        "GENTASK surface ex chunk={},{} {}", chunk.getPos().x, chunk.getPos().z, e.toString());
            }
            // CARVERS 阶段独立于 segments/surface（补触发/失败重试入口）：surface 后紧跟
            // （carver 只替换 fill 产物方块），17×17 起点网格不依赖邻居生成状态。失败不抛——
            // section 停留 SURFACE 由 scanAndEnqueue 补触发重试；不触发光照——
            // promoteAllGenToLighted 会把 SURFACE 升 LIGHTED，抹掉 carvers 待处理标志。
            int[] carved = new int[0];
            try {
                carved = com.inf.farlands.terrain.carverFiller.CarverFiller.applyCarversIfNeeded(
                        serverLevel, chunk);
            } catch (Exception e) {
                com.inf.farlands.InfFarlands.LOGGER.error(
                        "GENTASK carvers ex chunk={},{} {}", chunk.getPos().x, chunk.getPos().z, e.toString());
            }
            // 光照触发条件 = carvers 完成（carvers 是光照前最后阶段）：carved 覆盖新 fill 的
            // section（fill→surface→carvers 升段链），发送/光照都只依赖 carved。
            if (carved.length > 0) {
                GenQueue.notifyGenerated(chunk); // 光照触发：chunk 级去重，LIGHT_IN_FLIGHT 置位，见 GenQueue
                for (int sy : carved) {
                    // fill/surface/carvers 直接写 section 无 vanilla 广播 → 补入发送队列，下 tick flush 发 §5 包。
                    // 移到 notifyGenerated 后：flush 检查 LIGHT_IN_FLIGHT 必 true → 留队列等光照
                    // 完成 → §5 带正确光照：方块+光照同到，防客户端 0 层黑。
                    InfFarlands.enqueueSectionSend(chunk, sy);
                }
            }
        } finally {
            // fill 异常也清理：try-finally 中清在途 + 释放 fill ticket。
            // 既有 CHUNK_IN_FLIGHT 泄漏修复：异常路径此前 completeTask 不执行 → 标志残留 → 加 ticket 后放大为 chunk 永不卸载
            GenQueue.completeTask(chunk);
        }
    }

    /** 收集该 chunk 的待生成 section 连续段，来源为窗口并集或预加载指定集合。
     * 过滤已生成 isOrAfter NOISE 的 section 与可玩范围，clamp 段顶防 cellStartBlockY 溢出。
     * 窗口模式额外跳过读回在途 isReading 的 section，读回完成回调会入队。
     * 预加载模式不跳，isOrAfter 兜底加 applyDecoded 幂等覆盖，语义安全。 */
    private List<int[]> collectSegments() {
        List<int[]> segments = new ArrayList<>();
        int[] curMin = {0};
        int[] curMax = {-1};
        boolean[] open = {false};
        java.util.function.IntConsumer consider = sy -> {
            if (sy > Constants.MAX_CHUNK - 1 || sy < -Constants.MAX_CHUNK) {
                return;
            }
            if (FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.NOISE)) {
                return;
            }
            if (preloadSections == null
                    && com.inf.farlands.serialize.SectionIO.isReading(chunk.getPos().toLong(), sy)) {
                return; // 窗口模式：读回在途跳过；预加载模式不跳，isOrAfter 兜底
            }
            if (open[0] && sy == curMax[0] + 1) {
                curMax[0] = sy;
            } else {
                if (open[0]) {
                    segments.add(new int[]{curMin[0], curMax[0]});
                }
                curMin[0] = sy;
                curMax[0] = sy;
                open[0] = true;
            }
        };
        if (preloadSections != null) {
            for (int sy : preloadSections) {
                consider.accept(sy);
            }
        } else {
            EntitySectionWindow.forEachSectionInAnyWindow(consider);
        }
        if (open[0]) {
            segments.add(new int[]{curMin[0], curMax[0]});
        }
        return segments;
    }
}