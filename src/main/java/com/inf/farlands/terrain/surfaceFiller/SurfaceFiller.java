package com.inf.farlands.terrain.surfaceFiller;

import com.inf.farlands.Constants;
import com.inf.farlands.serialize.SectionIO;
import com.inf.farlands.terrain.SurfaceSystem;
import com.inf.farlands.terrain.pipeline.FarLandsGenState;
import com.inf.farlands.terrain.system.SurfaceSystemRegistry;
import com.inf.farlands.window.WindowedChunk;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * SURFACE 阶段编排：何时跑（触发）/ 状态推进（SURFACE 态）/ 按维度分派 SurfaceSystem。
 *
 * <p>surface 依赖 fill 的高度图产物（WG 高度图由 fill 更新，surface 从高度图顶向下列扫描），
 * 必须紧跟 fill、同 chunk 串行（GenTask 内，CHUNK_IN_FLIGHT CAS 保证）——不能像 BIOMES
 * 那样完全异步后台。
 *
 * <p>触发：GenTask fill 完成后调 applySurfaceIfNeeded（新 fill 的 section 必 NOISE 未
 * SURFACE）；补触发：读回 stage=NOISE（旧档迁移/失败残留）的 section 由
 * GenQueue.scanAndEnqueue 的 hasSurfacePending 检查入队 → GenTask 内补跑。
 *
 * <p>失败策略：applySurface 异常由 GenTask catch，不抛——section 停留 NOISE 态由补触发
 * 重试；且不触发光照——promoteAllGenToLighted 会把 NOISE 升 LIGHTED，抹掉 surface 待
 * 处理标志，导致该 section 永远无 surface。
 *
 * <p>状态推进与标脏：applySurfaceIfNeeded 对升 SURFACE 的 section 标脏——surface 修改
 * 方块（stone→草皮），读回 section 未标脏（applyDecoded 不标脏），不标则写盘丢 surface
 * 结果；fill 已标脏的重复标无害（幂等）。
 */
public final class SurfaceFiller {

    private SurfaceFiller() {
    }

    /** 该 chunk 是否已有 NOISE 未 SURFACE 且非读回的 section（surface 待处理）。
     * 全 chunk 检测（非窗口并集）——预加载模式（玩家未加入、窗口 ranges 空）下 fill 的
     * section 也必须能触发 surface，否则 surfaced 空 → 光照不触发 → 预加载卡 LIGHTED 前
     * （进度卡 0%）。读回在途 section 不在 allSections keySet（applyDecoded 才 put），
     * isReading 为双保险。供 GenQueue.scanAndEnqueue 补触发检查。 */
    public static boolean hasSurfacePending(LevelChunk chunk) {
        for (Integer sy : ((WindowedChunk) chunk).windowedAllSections().keySet()) {
            if (sy > Constants.MAX_CHUNK - 1 || sy < -Constants.MAX_CHUNK) {
                continue;
            }
            if (FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.NOISE)
                    && !FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.SURFACE)
                    && !SectionIO.isReading(chunk.getPos().toLong(), sy)) {
                return true;
            }
        }
        return false;
    }

    /** 编排：有 surface 待处理才跑（维度系统应用 + 升 SURFACE + 标脏）。返回本次升 SURFACE 的 sectionY 数组。 */
    public static int[] applySurfaceIfNeeded(ServerLevel level, LevelChunk chunk) {
        if (!hasSurfacePending(chunk)) {
            return new int[0];
        }
        systemFor(level).applySurface(level, chunk);
        // 状态推进 + 标脏：surface 修改方块（stone→草皮），读回 section 未标脏 → 必须标脏，
        // 否则写盘丢 surface 结果；fill 已标脏的重复标无害（幂等）。
        List<Integer> list = new ArrayList<>();
        for (Integer sy : ((WindowedChunk) chunk).windowedAllSections().keySet()) {
            if (FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.NOISE)
                    && !FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.SURFACE)) {
                list.add(sy);
            }
        }
        int[] surfaced = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            int sy = list.get(i);
            surfaced[i] = sy;
            FarLandsGenState.setStage(chunk, sy, FarLandsGenState.SURFACE);
            ((WindowedChunk) chunk).markSectionDirty(sy);
        }
        return surfaced;
    }

    /** 按 vanilla 维度 id 分派（Level.NETHER/END 是 ResourceKey 非枚举，不能用 switch）。 */
    private static SurfaceSystem systemFor(ServerLevel level) {
        if (level.dimension() == Level.NETHER) {
            return SurfaceSystemRegistry.getTheNether();
        }
        if (level.dimension() == Level.END) {
            return SurfaceSystemRegistry.getTheEnd();
        }
        return SurfaceSystemRegistry.getOverworld();
    }
}
