package com.inf.farlands.mixin.axisYOverflowFix;

import com.inf.farlands.Constants;

import net.minecraft.world.level.dimension.DimensionType;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {

    @Shadow
    @Final
    @Mutable
    private static int MIN_Y;

    @Shadow
    @Final
    @Mutable
    private static int MAX_Y;

    @Shadow
    @Final
    @Mutable
    private static int Y_SIZE;

    @Shadow
    @Final
    @Mutable
    private static int WAY_ABOVE_MAX_Y;

    @Shadow
    @Final
    @Mutable
    private static int WAY_BELOW_MIN_Y;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void onClinit(CallbackInfo ci) {
        // Set to values where * 2 = ±MAX_BLOCK without overflow
        MIN_Y = -Constants.MAX_BLOCK / 2;
        MAX_Y = Constants.MAX_BLOCK / 2;
        Y_SIZE = Integer.MAX_VALUE;
        WAY_ABOVE_MAX_Y = Integer.MAX_VALUE;
        WAY_BELOW_MIN_Y = Integer.MIN_VALUE;
    }
}
