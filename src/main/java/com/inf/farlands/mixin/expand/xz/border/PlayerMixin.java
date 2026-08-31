package com.inf.farlands.mixin.expand.xz.border;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.world.entity.player.Player;

@Mixin(Player.class)
public class PlayerMixin {
    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = 2.9999999E7))
    private static double maxPos(double value) {
        return FarlandsConstant.MAX_BLOCK - 1;
    }

    @ModifyConstant(method = "tick", constant = @Constant(doubleValue = -2.9999999E7))
    private static double minPos(double value) {
        return ~FarlandsConstant.MAX_BLOCK + 1;
    }
}
