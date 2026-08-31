package com.inf.farlands.mixin.expand.xz.border;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.world.level.dimension.DimensionType;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {

    @ModifyConstant(method = "lambda$createDirectCodec$0", constant = @Constant(doubleValue = 3.0E7))
    private static double maxValue(double value) {
        return FarlandsConstant.MAX_BLOCK;
    }
}
