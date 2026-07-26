package com.inf.farlands.mixin.worldBorder;

import com.inf.farlands.Config;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

import net.minecraft.world.level.border.WorldBorder;

@Mixin(WorldBorder.Settings.class)
public class WorldBorder$SettingsMixin {
    // double d0 =
    // Mth.clamp(dynamic.get("BorderCenterX").asDouble(defaultValue.centerX),
    // -2.9999984E7, 2.9999984E7);
    // double d1 =
    // Mth.clamp(dynamic.get("BorderCenterZ").asDouble(defaultValue.centerZ),
    // -2.9999984E7, 2.9999984E7);
    @ModifyConstant(method = "read", constant = @Constant(doubleValue = 2.9999984E7))
    private static double modifyMaxCoordP(double original) {
        return Config.borderAbsoluteMax - 16.0;
    }
    
    @ModifyConstant(method = "read", constant = @Constant(doubleValue = -2.9999984E7))
    private static double modifyMaxCoordN(double original) {
        return ~Config.borderAbsoluteMax + 16.0;
    }
}