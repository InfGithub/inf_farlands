package com.inf.farlands.mixin.expand.xz.border;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

import net.minecraft.server.commands.ForceLoadCommand;

import com.inf.farlands.FarlandsConfig;

@Mixin(ForceLoadCommand.class)
public class ForceLoadCommandMixin {

    @ModifyConstant(method = "changeForceLoad", constant = @Constant(intValue = 30000000))
    private static int maxBlock(int max) {
        return FarlandsConfig.borderAbsoluteMax;
    }

    @ModifyConstant(method = "changeForceLoad", constant = @Constant(intValue = -30000000))
    private static int minBlock(int min) {
        return ~FarlandsConfig.borderAbsoluteMax;
    }
}
