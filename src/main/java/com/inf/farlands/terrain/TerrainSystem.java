package com.inf.farlands.terrain;

import javax.annotation.Nullable;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * 地形系统抽象基接口：两类互斥的地形填充范式共享的公共部分——
 * 生命周期回调 + 流体/aquifer 提供。
 *
 * 两类互斥子接口（一个实现类只实现其一，分派点 instanceof BlockSystem 优先）：
 *  - {@link NoiseSystem}：密度链范式——finalDensity 注入，cell 角点采样 + 三线性插值
 *  - {@link BlockSystem}：逐块几何范式——fillBlock 每格直接判定，绕过密度链
 *
 * 线程模型：onLevelLoad 在主线程世界加载时调用；createFluidPicker/createAquifer 在
 * NoiseChunk 构造/填充时调用，跑 genPool/主线程。onChunkFillStart 为预留接口
 * （当前无调用方）——beta 的 biome temp/hum 断链修复时接线，默认空实现。
 */
public interface TerrainSystem {

    /**
     * 管线 fill 的 fluidPicker；null = 管线默认：lava y&lt;-54、water seaLevel。
     * 全空气系统（VOID/逐块几何）覆盖为空气 picker——密度恒负时 aquifer 按
     * fluidPicker 填流体，不空气化会得到全水世界。
     */
    default Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        return null;
    }

    /**
     * 管线 fill 的 aquifer；null = 默认 vanilla NoiseBasedAquifer。
     * 全空气系统覆盖为空气 aquifer——computeSubstance 恒返回非 null 的 AIR，
     * 使 MaterialRuleList 短路、矿脉规则不执行，得到全空气且 O(1)。
     */
    @Nullable
    default Aquifer createAquifer(NoiseChunk chunk, ChunkPos pos, NoiseRouter router,
            PositionalRandomFactory random, int minY, int height, Aquifer.FluidPicker picker) {
        return null;
    }

    /** 主世界加载时由 InfFarlands.onOverworldLoad 调用：按 seed 初始化系统噪声。 */
    void onLevelLoad(long seed);

    /** 每次 fill 开始时在 OverworldNoiseFiller.fill 入口调用：实现设置自己的 chunk 上下文。预留，当前无调用方。 */
    default void onChunkFillStart(ChunkAccess chunk) {
    }
}
