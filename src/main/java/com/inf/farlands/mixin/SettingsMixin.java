package com.inf.farlands.mixin;

import com.inf.farlands.Config;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

import net.minecraft.world.level.border.WorldBorder;

@Mixin(WorldBorder.Settings.class)
public class SettingsMixin {
    @ModifyConstant(method = "read", constant = @Constant(doubleValue = 2.9999984E7))
    private static double modifyMaxCoord(double original) {
        return Config.borderAbsoluteMax - 16.0;
    }
}