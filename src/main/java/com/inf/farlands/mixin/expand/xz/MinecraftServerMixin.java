package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.server.MinecraftServer;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @ModifyConstant(method = "getAbsoluteMaxWorldSize", constant = @Constant(intValue = 29999984))
    private int absoluteMaxWorldSize(int value) {
        return FarlandsConstant.MAX_BLOCK;
    }
}
