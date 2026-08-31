package com.inf.farlands.mixin.expand.xz.border;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConfig;

import net.minecraft.world.level.LevelReader;

@Mixin(LevelReader.class)
public interface LevelReaderMixin {
    @ModifyConstant(method = "getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;I)I", constant = @Constant(intValue = 30000000))
    private int maxBlock(int max) {
        return FarlandsConfig.borderAbsoluteMax;
    }

    @ModifyConstant(method = "getMaxLocalRawBrightness(Lnet/minecraft/core/BlockPos;I)I", constant = @Constant(intValue = -30000000))
    private int minBlock(int min) {
        return ~FarlandsConfig.borderAbsoluteMax;
    }
}
