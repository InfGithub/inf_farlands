package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {

    // 方法：
    // private static double clampHorizontal(double value) {
    //     return Mth.clamp(value, -3.0E7, 3.0E7);
    // }

    @ModifyConstant(method = "clampHorizontal", constant = @Constant(doubleValue = 3.0E7))
    private static double modifyHorizontalClamp(double original) {
        return Config.borderAbsoluteMax;
    }

    // 方法：
    // private static double clampVertical(double value) {
    //     return Mth.clamp(value, -2.0E7, 2.0E7);
    // }

    @ModifyConstant(method = "clampVertical", constant = @Constant(doubleValue = 2.0E7))
    private static double modifyVerticalClamp(double original) {
        return Config.borderAbsoluteMax;
    }
}