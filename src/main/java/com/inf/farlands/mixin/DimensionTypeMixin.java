package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {
    // 方法调用：
    // Codec.doubleRange(1.0E-5F, 3.0E7).fieldOf("coordinate_scale").forGetter(DimensionType::coordinateScale),
    @Redirect(
        method = "*",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/serialization/Codec;doubleRange(DD)Lcom/mojang/serialization/Codec;"
        ),
        remap = false
    )
    private static Codec<Double> redirectDoubleRange(double min, double max) {
        return Codec.doubleRange(min, Config.borderAbsoluteMax);
    }
}