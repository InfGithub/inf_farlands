package com.inf.farlands.terrain.system.theNether.noiseVanilla;

import com.inf.farlands.terrain.NoiseSystem;

import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;

/**
 * 下界 vanilla 1.21.1 噪声系统：finalDensity 原样放行（router 即下界链）。
 *
 * 下界 NoiserRouterData.nether() = noNewCaves(slideNetherLike(BlendedNoise
 * (0.25,0.375,80,60,8), 0,128))——BlendedNoise + slide 链，1.21.1 数据注册现成。
 * router.finalDensity() 即该链；aquifer 由 NoiseChunk 自动 createDisabled
 * （isAquifersEnabled=false）；surface 规则不走管线（裸地形）。
 */
@SuppressWarnings("null")
public final class VanillaNoiseSystem implements NoiseSystem {

    @Override
    public DensityFunction createFinalDensity(NoiseRouter router) {
        return router.finalDensity();
    }

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // 下界：全熔岩海（seaLevel=32），无 lava 分层。
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(settings.seaLevel(), settings.defaultFluid());
        return (x, y, z) -> lava;
    }

    @Override
    public void onLevelLoad(long seed) {
        // vanilla 密度链从 RandomState 派生，无需按 seed 预初始化
    }
}
