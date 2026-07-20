package com.inf.farlands.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class PlayerMixin {

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(DDD)D",
            ordinal = 0
        )
    )
    private double skipXClamp(double value, double min, double max) {
        return value;
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(DDD)D",
            ordinal = 1
        )
    )
    private double skipZClamp(double value, double min, double max) {
        return value;
    }
}
