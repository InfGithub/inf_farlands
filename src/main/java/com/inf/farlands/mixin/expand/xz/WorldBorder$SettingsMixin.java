package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.world.level.border.WorldBorder;

@Mixin(WorldBorder.Settings.class)
public class WorldBorder$SettingsMixin {

    @ModifyConstant(method = "<clinit>", constant = @Constant(doubleValue = 5.9999968E7D))
    private static double onClassInitA(double value) {
        return (FarlandsConstant.MAX_BLOCK - 16.0) * 2.0;
    }

    @ModifyConstant(method = "lambda$static$0", constant = @Constant(doubleValue = 2.9999984E7))
    private static double onClassInitB(double value) {
        return FarlandsConstant.MAX_BLOCK - 16.0;
    }

    @ModifyConstant(method = "lambda$static$0", constant = @Constant(doubleValue = -2.9999984E7))
    private static double onClassInitC(double value) {
        return -FarlandsConstant.MAX_BLOCK + 16.0;
    }
}
