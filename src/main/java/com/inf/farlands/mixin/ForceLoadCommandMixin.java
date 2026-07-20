package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.server.commands.ForceLoadCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(ForceLoadCommand.class)
public class ForceLoadCommandMixin {

    @ModifyConstant(method = "changeForceLoad", constant = @Constant(intValue = 30000000))
    private static int replaceMaxBound(int constant) {
        return Config.borderAbsoluteMax;
    }

    @ModifyConstant(method = "changeForceLoad", constant = @Constant(intValue = -30000000))
    private static int replaceMinBound(int constant) {
        return -Config.borderAbsoluteMax;
    }
}
