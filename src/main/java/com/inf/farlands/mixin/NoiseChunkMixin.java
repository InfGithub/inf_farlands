package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import com.inf.farlands.terrain.BetaDensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {

    @Shadow
    protected abstract DensityFunction wrap(DensityFunction df);

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/DensityFunction;"
        )
    )
    private DensityFunction replaceFinalDensity(NoiseRouter router) {
        DensityFunction original = router.finalDensity();
        if (!Config.betaTerrain) return original;
        return new BetaDensityFunction();
    }
}
