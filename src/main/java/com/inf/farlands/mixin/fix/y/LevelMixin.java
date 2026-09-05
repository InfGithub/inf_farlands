package com.inf.farlands.mixin.fix.y;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConfig;

import net.minecraft.world.level.Level;

/**
 * 实体出生 Y 带放宽。
 */
@Mixin(Level.class)
public class LevelMixin {

    @ModifyConstant(method = "isOutsideSpawnableHeight", constant = @Constant(intValue = -20000000))
    private static int expandMinSpawnableY(int original) {
        return FarlandsConfig.worldGenMinY;
    }

    @ModifyConstant(method = "isOutsideSpawnableHeight", constant = @Constant(intValue = 20000000))
    private static int expandMaxSpawnableY(int original) {
        return FarlandsConfig.worldGenMaxY;
    }
}
