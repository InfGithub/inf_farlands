package com.inf.farlands.terrain.biomeFiller;

import com.inf.farlands.terrain.BiomeSystem;
import com.inf.farlands.terrain.pipeline.FarLandsGenState;
import com.inf.farlands.terrain.system.BiomeSystemRegistry;
import com.inf.farlands.window.EntitySectionWindow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * biome 阶段编排：何时填（触发）/ 状态（BIOMES 态）/ 按维度分派 BiomeSystem。
 *
 * biome 是独立阶段（vanilla BIOMES 先于 NOISE），不依赖地形内容——由触发链驱动：
 * 短路完成时 fillChunkBiomes（后台线程，窗口并集 section 填 + setStage(BIOMES)），
 * 窗口滑入新 section 时 fillSectionBiomes（主线程，stage &lt; BIOMES 才填）。
 * fill（NoiseFiller）不再管 biome；读回 section 的磁盘 stage 恢复后自动跳过。
 *
 * setStage 线程安全：attachment 容器已由短路主线程 initialize（FarLandsGenState
 * 既有约束），此后任意线程只读容器 + CHM.put。
 */
public final class BiomeFiller {

    private BiomeFiller() {
    }

    /** 短路完成：窗口并集内每 section 填 biome + setStage(BIOMES)。后台线程（backgroundExecutor）。 */
    public static void fillChunkBiomes(ServerLevel level, LevelChunk chunk) {
        BiomeSystem sys = systemFor(level);
        EntitySectionWindow.forEachSectionInAnyWindow(sy -> seedSection(level, chunk, sys, sy));
    }

    /** 窗口滑入补触发：单 section，stage &lt; BIOMES 才填（读回 section 的磁盘 stage 跳过）。主线程。 */
    public static void fillSectionBiomes(ServerLevel level, LevelChunk chunk, int sectionY) {
        if (FarLandsGenState.getStage(chunk, sectionY) < FarLandsGenState.BIOMES) {
            seedSection(level, chunk, systemFor(level), sectionY);
        }
    }

    private static void seedSection(ServerLevel level, LevelChunk chunk, BiomeSystem sys, int sectionY) {
        sys.fillBiomes(level, chunk, sectionY, sectionY); // getSection 懒创建 + 4×4×4
        FarLandsGenState.setStage(chunk, sectionY, FarLandsGenState.BIOMES);
    }

    /** 按 vanilla 维度 id 分派（Level.NETHER/END 是 ResourceKey 非枚举，不能用 switch）。 */
    private static BiomeSystem systemFor(ServerLevel level) {
        if (level.dimension() == Level.NETHER) {
            return BiomeSystemRegistry.getTheNether();
        }
        if (level.dimension() == Level.END) {
            return BiomeSystemRegistry.getTheEnd();
        }
        return BiomeSystemRegistry.getOverworld();
    }
}
