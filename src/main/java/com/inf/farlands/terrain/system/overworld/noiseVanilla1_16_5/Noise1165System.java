package com.inf.farlands.terrain.system.overworld.noiseVanilla1_16_5;

import com.inf.farlands.terrain.NoiseSystem;

import java.util.stream.IntStream;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * 1.16.5 噪声系统：密度链范式，finalDensity = Noise1165DensityFunction。
 *
 * 主世界 5 组噪声中只移植地形链实际消费的 4 组（NoiseBasedChunkGenerator L114-121）：
 * - minLimitPerlinNoise（16 八度 range(-15,0)）→ 下限噪声
 * - maxLimitPerlinNoise（16 八度 range(-15,0)）→ 上限噪声
 * - mainPerlinNoise（8 八度 range(-7,0)）→ 混合噪声
 * - depthNoise（16 八度 range(-15,0)）→ randomDensityOffset
 * 不移植：surfaceNoise（仅 buildSurfaceAndBedrock 用，我们管线不走 vanilla surface
 * builder）、islandNoise（仅末地 islandNoiseOverride）。
 *
 * 随机源：直接用 1.21.1 RandomSource + PerlinNoise.create（forkPositional 派生）——
 * 与 1.6.4 移植同立场：种子链无原版对照，稳定一致即可（note #85 之后 1.6.4 先例）。
 *
 * fluidPicker（1.16.5 语义）：无 lava 层；水判定 y < seaLevel(63)——与 1.16.5
 * generateBaseState 的 "d<seaLevel → water" 天然对齐（aquifer 在 density≤0 时调）。
 */
@SuppressWarnings("null")
public final class Noise1165System implements NoiseSystem {

    private volatile PerlinNoise minLimit;
    private volatile PerlinNoise maxLimit;
    private volatile PerlinNoise main;
    private volatile PerlinNoise depth;

    @Override
    public DensityFunction createFinalDensity(NoiseRouter router) {
        return new Noise1165DensityFunction(this.minLimit, this.maxLimit, this.main, this.depth);
    }

    @Override
    public void onLevelLoad(long seed) {
        // 1.16.5 构造顺序（L113-121）：minLimit→maxLimit→main→depth。
        // 跳过 surfaceNoise/consumeCount(2620)/islandNoise（不移植）。
        RandomSource random = RandomSource.create(seed);
        this.minLimit = PerlinNoise.create(random, IntStream.rangeClosed(-15, 0));
        this.maxLimit = PerlinNoise.create(random, IntStream.rangeClosed(-15, 0));
        this.main = PerlinNoise.create(random, IntStream.rangeClosed(-7, 0));
        this.depth = PerlinNoise.create(random, IntStream.rangeClosed(-15, 0));
    }

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // 1.16.5：仅水（y < seaLevel），无 lava 层。seaLevel 来自 settings（主世界 = 63）。
        // FluidStatus.at(y) = y < fluidLevel ? fluidType : AIR——恒返回 water 即完成判定。
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus water = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> water;
    }
}
