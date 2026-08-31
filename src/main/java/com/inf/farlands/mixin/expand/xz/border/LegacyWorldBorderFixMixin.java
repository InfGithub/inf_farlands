package com.inf.farlands.mixin.expand.xz.border;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConfig;

import net.minecraft.util.datafix.fixes.LegacyWorldBorderFix;

@Mixin(LegacyWorldBorderFix.class)
public class LegacyWorldBorderFixMixin {
    @ModifyConstant(method = "lambda$makeRule$1", constant = @Constant(doubleValue = 5.9999968E7D))
    private static double maxSize(double value) {
        return (FarlandsConfig.borderAbsoluteMax - 16.0) * 2.0;
    }
}
