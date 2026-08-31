package com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3;

import com.inf.farlands.terrain.NoiseSystem;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * beta 1.7.3 噪声系统：BetaDensityFunction 注入 finalDensity，噪声单例按 seed 懒建。
 *
 * onChunkFillStart 预留：biome temp/hum 断链未修，CURRENT_CHUNK 无接线方，
 * 保持现状恒 0，地形不变（接口默认空实现）。
 */
public final class Beta173NoiseSystem implements NoiseSystem {

    @Override
    public DensityFunction createFinalDensity(NoiseRouter router) {
        // per-NoiseChunk 新实例：BetaDensityFunction 有 8 角/网格点缓存字段，禁止共享
        return new BetaDensityFunction();
    }

    @Override
    public void onLevelLoad(long seed) {
        BetaTerrain.initialize(seed);
    }
}
