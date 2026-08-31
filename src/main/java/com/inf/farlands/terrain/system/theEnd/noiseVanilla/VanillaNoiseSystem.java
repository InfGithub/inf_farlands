package com.inf.farlands.terrain.system.theEnd.noiseVanilla;

import com.inf.farlands.terrain.NoiseSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * 末地 vanilla 1.21.1 噪声系统：finalDensity 原样放行（router 即末地链）。
 *
 * 末地 NoiseRouterData.end() = postProcess(slideEnd(slopedCheeseEnd))，
 * slopedCheeseEnd = endIslands(0) + BlendedNoise(0.25,0.25,80,160,4)——浮岛 +
 * slide 链，1.21.1 数据注册现成。router.finalDensity() 即该链；aquifer 由
 * NoiseChunk 自动 createDisabled（isAquifersEnabled=false）；surface 纯末地石。
 */
@SuppressWarnings("null")
public final class VanillaNoiseSystem implements NoiseSystem {

    @Override
    public DensityFunction createFinalDensity(NoiseRouter router) {
        return router.finalDensity();
    }

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // 末地：无流体（defaultFluid=air，seaLevel=0）。
        Aquifer.FluidStatus air = new Aquifer.FluidStatus(0, Blocks.AIR.defaultBlockState());
        return (x, y, z) -> air;
    }

    @Override
    public void onLevelLoad(long seed) {
        // vanilla 密度链从 RandomState 派生，无需按 seed 预初始化
    }
}
