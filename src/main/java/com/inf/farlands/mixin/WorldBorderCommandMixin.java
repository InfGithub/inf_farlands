package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.server.commands.WorldBorderCommand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(WorldBorderCommand.class)
public class WorldBorderCommandMixin {

    @ModifyConstant(method = "*", constant = @Constant(doubleValue = 5.999997E7F))
    private static double modifyFiveNine(double original) {
        return Config.borderAbsoluteMax * 2.0 - 30.0;
    }

    @ModifyConstant(method = "*", constant = @Constant(doubleValue = 2.9999984E7))
    private static double modifyTwoNine(double original) {
        return Config.borderAbsoluteMax - 16.0;
    }
}