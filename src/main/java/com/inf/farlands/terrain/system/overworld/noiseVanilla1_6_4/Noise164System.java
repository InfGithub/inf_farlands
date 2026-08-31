package com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4;

import com.inf.farlands.terrain.NoiseSystem;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * 1.6.4 噪声系统：密度链范式，finalDensity = Noise164DensityFunction。
 *
 * 1.6.4 原版 7 组 octaves（noiseGen1-6 + mobSpawnerNoise），其中 noiseGen4
 * （replaceBlocksForBiome 的 stoneNoise）、noiseGen5（无消费死字段）、
 * mobSpawnerNoise（mob 用）不移植——surface 替换层不在本系统范围；
 * 只构造实际消费的 4 组：noise1(16)/noise2(16)/noise3(8)/noise6(16)。
 *
 * fluidPicker 覆盖（1.6.4 语义）：无 lava 层（lava 仅原版地物 WorldGenLakes，
 * 不在地形链）；水判定 y < seaLevel(63)——现代 aquifer 在 density≤0 时才调
 * fluidPicker，与 1.6.4 generateTerrain 的 "density≤0 且 y<63 → water" 天然对齐。
 */
@SuppressWarnings("null")
public final class Noise164System implements NoiseSystem {

    private volatile Noise164Octaves noise1;
    private volatile Noise164Octaves noise2;
    private volatile Noise164Octaves noise3;
    private volatile Noise164Octaves noise6;

    @Override
    public DensityFunction createFinalDensity(NoiseRouter router) {
        return new Noise164DensityFunction(this.noise1, this.noise2, this.noise3, this.noise6);
    }

    @Override
    public void onLevelLoad(long seed) {
        // 1.6.4 构造顺序（L61-66）：noiseGen1(16)→noiseGen2(16)→noiseGen3(8)→noiseGen6(16)
        // 跳过 noiseGen4/5/mobSpawner（不移植，见类注释）。RandomSource 序列与
        // 1.6.4 的 java.util.Random 不同——种子链无原版对照，稳定一致即可。
        RandomSource random = RandomSource.create(seed);
        this.noise1 = new Noise164Octaves(random, 16);
        this.noise2 = new Noise164Octaves(random, 16);
        this.noise3 = new Noise164Octaves(random, 8);
        this.noise6 = new Noise164Octaves(random, 16);
    }

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // 1.6.4：仅水（y < seaLevel），无 lava 层。seaLevel 来自 settings（主世界 = 63）。
        // FluidStatus.at(y) = y < fluidLevel ? fluidType : AIR——恒返回 water 即完成判定。
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus water = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> water;
    }
}
