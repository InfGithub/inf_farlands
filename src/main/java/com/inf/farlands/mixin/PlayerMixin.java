package com.inf.farlands.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class PlayerMixin {

    // 方法：
    // double d0 = Mth.clamp(this.getX(), -2.9999999E7, 2.9999999E7);

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 0))
    private double skipXClamp(double value, double min, double max) {
        return value;
    }

    // 方法：
    // double d1 = Mth.clamp(this.getZ(), -2.9999999E7, 2.9999999E7);
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 1))
    private double skipZClamp(double value, double min, double max) {
        return value;
    }
}
