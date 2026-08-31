package com.inf.farlands.mixin.worldOverflowFix;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Inject;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import com.inf.farlands.util.WorldBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlacedFeature.class)
public class PlacedFeatureMixin {
    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void skipExtremeCoords(
            WorldGenLevel level,
            ChunkGenerator gen,
            RandomSource rng,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        int x = pos.getX(), z = pos.getZ();
        if (!WorldBounds.inBlockXZ(x, z)) {
            cir.setReturnValue(false);
        }
    }
}
