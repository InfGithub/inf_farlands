package com.inf.farlands.mixin.worldBorder;

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
    private static double modifyHorizontalClampP(double original) {
        return Config.borderAbsoluteMax;
    }

    @ModifyConstant(method = "clampHorizontal", constant = @Constant(doubleValue = -3.0E7))
    private static double modifyHorizontalClampN(double original) {
        return ~Config.borderAbsoluteMax;
    }

    // 方法：
    // private static double clampVertical(double value) {
    //     return Mth.clamp(value, -2.0E7, 2.0E7);
    // }

    @ModifyConstant(method = "clampVertical", constant = @Constant(doubleValue = 2.0E7))
    private static double modifyVerticalClampP(double original) {
        return Config.borderAbsoluteMax;
    }

    @ModifyConstant(method = "clampVertical", constant = @Constant(doubleValue = -2.0E7))
    private static double modifyVerticalClampN(double original) {
        return ~Config.borderAbsoluteMax;
    }
}