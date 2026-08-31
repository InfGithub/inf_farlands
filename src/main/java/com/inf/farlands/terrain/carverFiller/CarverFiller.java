package com.inf.farlands.terrain.carverFiller;

import com.inf.farlands.Constants;
import com.inf.farlands.light.IColumnMasks;
import com.inf.farlands.mixin.noise.HeightmapInvoker;
import com.inf.farlands.serialize.SectionIO;
import com.inf.farlands.terrain.CarverSystem;
import com.inf.farlands.terrain.pipeline.FarLandsGenState;
import com.inf.farlands.terrain.system.CarverSystemRegistry;
import com.inf.farlands.window.WindowedChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * CARVERS 阶段编排：何时跑（触发）/ 状态推进（CARVERS 态）/ 按维度分派 CarverSystem。
 *
 * <p>carver 依赖目标 chunk 的 fill+surface 产物（只替换 config.replaceable 的方块），
 * 必须紧跟 surface、同 chunk 串行（GenTask 内，CHUNK_IN_FLIGHT CAS 保证）。目标 chunk
 * 为中心 ±8 起点网格的雕刻不依赖邻居 chunk 生成状态（biomeSource 查询 + 确定性随机，
 * 见 VanillaCarverSystem）——按需生成下每 fill+surface 完的 chunk 可立即雕刻。
 *
 * <p>触发：GenTask surface 完成后调 applyCarversIfNeeded（新 surface 的 section 必
 * SURFACE 未 CARVERS）；补触发：surface 成功 carvers 失败（section 停留 SURFACE）由
 * GenQueue.scanAndEnqueue 的 hasCarversPending 检查入队重试。
 *
 * <p>失败策略：applyCarvers 异常由 GenTask catch，不抛——section 停留 SURFACE 态由补
 * 触发重试；且不触发光照——promoteAllGenToLighted 会把 SURFACE 升 LIGHTED，抹掉 carvers
 * 待处理标志（同 SurfaceFiller 失败策略）。
 *
 * <p>高度图 prime（自研）：carve 改方块后最终高度图失效。vanilla primeHeightmaps 扫描
 * [getHighestSectionPosition+16, minBuildHeight]——mod 窗口语义下 getHighestSectionPosition
 * 在极端 Y 可达 2.14B → 21 亿次扫描炸弹，不能直接调。自研：非空 section 降序 + 列掩码
 * 位跳定位非空行，按 type.isOpaque() 判定每列每类型的最高匹配行，@Invoker setHeight
 * 写入——O(非空 section × 256)，与 findLowestSourceYAll 同构。仅在本次有升段（实际
 * 雕刻可能发生）时执行。
 */
public final class CarverFiller {

    private CarverFiller() {
    }

    /** 该 chunk 是否已有 SURFACE 未 CARVERS 且非读回的 section（carvers 待处理）。
     * 全 chunk 检测（预加载场景窗口空也能触发）。供 GenQueue.scanAndEnqueue 补触发检查。 */
    public static boolean hasCarversPending(LevelChunk chunk) {
        for (Integer sy : ((WindowedChunk) chunk).windowedAllSections().keySet()) {
            if (sy > Constants.MAX_CHUNK - 1 || sy < -Constants.MAX_CHUNK) {
                continue;
            }
            if (FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.SURFACE)
                    && !FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.CARVERS)
                    && !SectionIO.isReading(chunk.getPos().toLong(), sy)) {
                return true;
            }
        }
        return false;
    }

    /** 编排：有 carvers 待处理才跑（维度系统应用 + 升 CARVERS + 标脏 + 高度图 prime）。
     * 返回本次升 CARVERS 的 sectionY 数组。 */
    public static int[] applyCarversIfNeeded(ServerLevel level, LevelChunk chunk) {
        if (!hasCarversPending(chunk)) {
            return new int[0];
        }
        systemFor(level).applyCarvers(level, chunk);
        // 状态推进 + 标脏：carve 修改方块（stone→air/water/lava），必须标脏（读回 section
        // 未标脏，不标则写盘丢雕刻结果；fill/surface 已标脏的重复标无害）。
        List<Integer> list = new ArrayList<>();
        for (Integer sy : ((WindowedChunk) chunk).windowedAllSections().keySet()) {
            if (FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.SURFACE)
                    && !FarLandsGenState.isOrAfter(chunk, sy, FarLandsGenState.CARVERS)) {
                list.add(sy);
            }
        }
        int[] carved = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            int sy = list.get(i);
            carved[i] = sy;
            FarLandsGenState.setStage(chunk, sy, FarLandsGenState.CARVERS);
            ((WindowedChunk) chunk).markSectionDirty(sy);
        }
        if (carved.length > 0) {
            primeFinalHeightmaps(chunk);
        }
        return carved;
    }

    /** 按 vanilla 维度 id 分派（Level.NETHER/END 是 ResourceKey 非枚举，不能用 switch）。 */
    private static CarverSystem systemFor(ServerLevel level) {
        if (level.dimension() == Level.NETHER) {
            return CarverSystemRegistry.getTheNether();
        }
        if (level.dimension() == Level.END) {
            return CarverSystemRegistry.getTheEnd();
        }
        return CarverSystemRegistry.getOverworld();
    }

    // ---- 自研最终高度图 prime（规避 vanilla primeHeightmaps 极端 Y 扫描炸弹）----

    /** CARVERS 后 prime 的四种最终高度图（ChunkStatus CARVERS 的 heightmapsAfter）。 */
    private static final Heightmap.Types[] FINAL_TYPES = {
            Heightmap.Types.OCEAN_FLOOR,
            Heightmap.Types.WORLD_SURFACE,
            Heightmap.Types.MOTION_BLOCKING,
            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
    };

    /**
     * 自研 prime：每列从非空 section 顶部向下找各类型的最高匹配行。
     * 对齐 vanilla primeHeightmaps 语义（L38-72）：OCEAN_FLOOR/WORLD_SURFACE/
     * MOTION_BLOCKING/MOTION_BLOCKING_NO_LEAVES，type.isOpaque() 判定，首个匹配行
     * 设 height = y+1；全列无匹配 → 不设（保持高度图默认）。列掩码位跳只访问非空行。
     */
    private static void primeFinalHeightmaps(LevelChunk chunk) {
        WindowedChunk wc = (WindowedChunk) chunk;
        List<Integer> nonEmpty = new ArrayList<>();
        for (Integer sy : wc.windowedAllSections().keySet()) {
            LevelChunkSection s = wc.windowedAllSections().get(sy);
            if (s != null && !s.hasOnlyAir()) {
                nonEmpty.add(sy);
            }
        }
        nonEmpty.sort(Collections.reverseOrder()); // 降序：从顶向下
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Set<Heightmap.Types> remaining = EnumSet.of(
                        Heightmap.Types.OCEAN_FLOOR,
                        Heightmap.Types.WORLD_SURFACE,
                        Heightmap.Types.MOTION_BLOCKING,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);
                for (int sy : nonEmpty) {
                    if (remaining.isEmpty()) {
                        break;
                    }
                    LevelChunkSection s = wc.windowedAllSections().get(sy);
                    short[] masks = ((IColumnMasks) s).farlands$ensureColumnMasks();
                    int m = masks[x + z * 16] & 0xFFFF;
                    while (m != 0 && !remaining.isEmpty()) {
                        int k = 31 - Integer.numberOfLeadingZeros(m);
                        m ^= 1 << k;
                        int y = sy * 16 + k;
                        BlockState state = s.getBlockState(x, k, z);
                        Iterator<Heightmap.Types> it = remaining.iterator();
                        while (it.hasNext()) {
                            Heightmap.Types type = it.next();
                            if (type.isOpaque().test(state)) {
                                ((HeightmapInvoker) chunk.getOrCreateHeightmapUnprimed(type))
                                        .farlands$setHeight(x, z, y + 1);
                                it.remove();
                            }
                        }
                    }
                }
            }
        }
    }
}
