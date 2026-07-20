package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(OreFeature.class)
public class OreFeatureMixin {

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void skipIfOutOfBounds(
        FeaturePlaceContext<OreConfiguration> context,
        CallbackInfoReturnable<Boolean> cir
    ) {
        BlockPos origin = context.origin();
        int maxBlock = FarLandsConstants.MAX_BLOCK;
        if (origin.getX() > maxBlock || origin.getX() < -maxBlock ||
            origin.getZ() > maxBlock || origin.getZ() < -maxBlock) {
            cir.setReturnValue(false);
        }
    }
}
