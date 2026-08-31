package com.inf.farlands.terrain;

import javax.annotation.Nullable;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * 密度链噪声系统：决定地形密度函数来源。
 *
 * 由 {@link NoiseSystemRegistry} 按 {@link Config#terrainSystem} 选择实现并持有单例。
 * 线程模型：createFinalDensity 在 NoiseChunk 构造器调用，跑 genPool/主线程，
 * 实现必须返回新实例——DensityFunction 有状态缓存，禁止共享。
 */
public interface NoiseSystem extends TerrainSystem {

    /** 每次 NoiseChunk 构造时调用，返回该系统的 finalDensity：vanilla 链或 beta 密度函数。 */
    DensityFunction createFinalDensity(NoiseRouter router);

    /**
     * 自定义 cell 网格（noiseSize 单位，×4 = cell 格数，Codec 范围 1-4）。
     * 返回 {noiseSizeHorizontal, noiseSizeVertical}；null = 用维度 settings 默认。
     *
     * <p>b1.7.3 天域采样网格与主世界正好互换（"竖过来"）：XZ 8 格 / Y 4 格
     * （noiseSize {2, 1}）——浮空岛的垂直形状依赖 Y 密采样，XZ 大尺度即可。
     * 天域 {@code createFinalDensity} 的 cell 换算必须与此网格一致
     * （XZ floorDiv 8、Y floorDiv 4）。
     */
    default @Nullable int[] noiseSize() {
        return null;
    }
}
