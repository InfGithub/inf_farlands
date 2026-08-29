package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.world.level.LevelReader;

@Mixin(LevelReader.class)
public class LevelReaderMixin {
    @ModifyConstant(method = "getMaxLocalRawBrightness", constant = @Constant(intValue = 30000000))
    private int maxBlock(int max) {
        return FarlandsConstant.MAX_BLOCK;
    }

    @ModifyConstant(method = "getMaxLocalRawBrightness", constant = @Constant(intValue = -30000000))
    private int minBlock(int min) {
        return -FarlandsConstant.MAX_BLOCK;
    }
}
