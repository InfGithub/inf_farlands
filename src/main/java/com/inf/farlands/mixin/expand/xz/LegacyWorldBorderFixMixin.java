package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.util.datafix.fixes.LegacyWorldBorderFix;

@Mixin(LegacyWorldBorderFix.class)
public class LegacyWorldBorderFixMixin {
    @ModifyConstant(method = "lambda$makeRule$1", constant = @Constant(doubleValue = 5.9999968E7D))
    private static double maxSize(double value) {
        return (FarlandsConstant.MAX_BLOCK - 16.0) * 2.0;
    }
}
