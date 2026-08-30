package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.world.level.border.WorldBorder;

@Mixin(WorldBorder.Settings.class)
public class WorldBorder$SettingsMixin {

    @ModifyConstant(method = "<clinit>", constant = @Constant(doubleValue = 5.9999968E7D))
    private static double onClassInit(double value) {
        return (FarlandsConstant.MAX_BLOCK - 16.0) * 2.0;
    }
}
