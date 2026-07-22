package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import com.inf.farlands.terrain.BetaDensityFunction;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin {

    @Redirect(method = "<init>", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/DensityFunction;"
    ))
    private DensityFunction replaceFinalDensity(NoiseRouter router) {
        if (!Config.betaTerrain) return router.finalDensity();
        return new BetaDensityFunction();
    }
}
