package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.server.commands.WorldBorderCommand;

@Mixin(WorldBorderCommand.class)
public class WorldBorderCommandMixin {
    @ModifyConstant(method = "<clinit>", constant = @Constant(doubleValue = 5.9999968E7d))
    private static double onClassInitA(double value) {
        return (FarlandsConstant.MAX_BLOCK - 16.0) * 2.0;
    }

    @ModifyConstant(method = "<clinit>", constant = @Constant(doubleValue = 2.9999984E7d))
    private static double onClassInitB(double value) {
        return FarlandsConstant.MAX_BLOCK - 16.0;
    }

    @ModifyConstant(method = "register", constant = @Constant(doubleValue = 5.9999968E7d))
    private static double maxBlock(double value) {
        return (FarlandsConstant.MAX_BLOCK - 16.0) * 2.0;
    }

    @ModifyConstant(method = "register", constant = @Constant(doubleValue = -5.9999968E7d))
    private static double minBlock(double value) {
        return (FarlandsConstant.MAX_BLOCK - 16.0) * -2.0;
    }

    @ModifyConstant(method = "setSize", constant = @Constant(doubleValue = 5.9999968E7d))
    private static double maxSize(double value) {
        return (FarlandsConstant.MAX_BLOCK - 16.0) * 2.0;
    }

    @ModifyConstant(method = "setCenter", constant = @Constant(doubleValue = 2.9999984E7))
    private static double maxBlockA(double value) {
        return FarlandsConstant.MAX_BLOCK - 16.0;
    }
}
