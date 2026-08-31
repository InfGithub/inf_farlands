package com.inf.farlands.terrain.system.skyDimension.noiseBeta1_7_3;

import com.inf.farlands.terrain.NoiseSystem;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * 天域（Sky Dimension）地形系统：b1.7.3 {@code ChunkProviderSky} 密度公式移植（浮空岛 + 边境之地）。
 *
 * - 密度：五通道八度（lower/upper XZ 1368.824·Y 684.412、blend XZ 17.11·Y 4.28、temp 1.121、
 *   humidity 200）→ lerp − 8.0 + 上下渐消——无高度衰减 → 3D 噪声正区域悬浮成岛
 * - 网格：noiseSize {2,1} → cellW=8、cellH=4（b1.7.3 与主世界互换的"竖过来"网格——Y 密采样
 *   保浮空岛垂直形态）
 * - 边境之地：LegacyPerlinNoise 无 2^24 取模，(int) 强转在 |XZ|≈12.55M 溢出（XZ 频率 ×2 与
 *   cell 8 归一化抵消，与主世界 beta 同位置）
 * - 无海平面水：空气 fluidPicker（b1.7.3 generateTerrain 无 waterStill 填充）
 * - 无表面/装饰/雪线（与 mod beta 主世界现状一致）
 */
public final class SkyDimensionNoiseSystem implements NoiseSystem {

    private volatile SkyDimensionNoise noise;

    @Override
    public DensityFunction createFinalDensity(NoiseRouter router) {
        // per-NoiseChunk 新实例：SkyDimensionDensityFunction 有 cell 缓存字段，禁止共享
        return new SkyDimensionDensityFunction(noise);
    }

    @Override
    public int[] noiseSize() {
        // b1.7.3 天域采样网格：XZ 8 格 / Y 4 格（主世界为 4×8，正好互换）
        return new int[] { 2, 1 };
    }

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // b1.7.3 天域无海平面水：density≤0 全空气
        return (x, y, z) -> new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());
    }

    @Override
    public Aquifer createAquifer(NoiseChunk chunk, ChunkPos pos, NoiseRouter router,
            PositionalRandomFactory random, int minY, int height, Aquifer.FluidPicker picker) {
        // b1.7.3 语义：density≤0 纯空气。vanilla NoiseBasedAquifer 在 pressure 路径对 substance≤0
        // 返回 null → MaterialRuleList 链 null → fillCellColumn 落 settings.defaultBlock（stone）
        // → 地下/岛空洞浮空石头块（b1.7.3 天域 generateTerrain 只写 density>0 → stone，其余默认空气）。
        // 自定义 aquifer：density>0 → null（走 OreVeinifier → defaultBlock 石头）；density≤0 → AIR
        // （非 null 短路 MaterialRuleList → 纯空气）。不读 fluidPicker——density≤0 恒空气，无岩浆/水。
        return new Aquifer() {
            @Override
            public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
                if (substance > 0.0) {
                    return null;
                }
                return Blocks.AIR.defaultBlockState();
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    @Override
    public void onLevelLoad(long seed) {
        if (noise == null) {
            synchronized (this) {
                if (noise == null) {
                    noise = new SkyDimensionNoise(seed);
                }
            }
        }
    }
}
