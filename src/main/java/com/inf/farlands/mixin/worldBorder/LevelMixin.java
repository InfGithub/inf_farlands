package com.inf.farlands.mixin.worldBorder;

import com.inf.farlands.Config;

import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
// import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(Level.class)
public class LevelMixin {
    // 字段：
    // public static final int MAX_LEVEL_SIZE = 30000000;
    @Shadow
    @Final
    @Mutable
    private static int MAX_LEVEL_SIZE;

    private static void set_MAX_LEVEL_SIZE(int size) {
        MAX_LEVEL_SIZE = size;
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void onClassInit(CallbackInfo ci) {
        set_MAX_LEVEL_SIZE(Config.borderAbsoluteMax);
    }

    // private static boolean isInWorldBoundsHorizontal(BlockPos pos) {
    // return pos.getX() >= -30000000 && pos.getZ() >= -30000000 && pos.getX() <
    // 30000000 && pos.getZ() < 30000000;
    // }

    @ModifyConstant(method = "isInWorldBoundsHorizontal", constant = @Constant(intValue = 30000000))
    private static int modifyWorldBoundsP(int original) {
        return Config.borderAbsoluteMax;
    }

    @ModifyConstant(method = "isInWorldBoundsHorizontal", constant = @Constant(intValue = -30000000))
    private static int modifyWorldBoundsN(int original) {
        return ~Config.borderAbsoluteMax;
    }

    // private static boolean isOutsideSpawnableHeight(int y) {
    // return y < -20000000 || y >= 20000000;
    // }

    @ModifyConstant(method = "isOutsideSpawnableHeight", constant = @Constant(intValue = 20000000))
    private static int modifyWorldBoundsHeightP(int original) {
        return Config.borderAbsoluteMax;
    }

    @ModifyConstant(method = "isOutsideSpawnableHeight", constant = @Constant(intValue = -20000000))
    private static int modifyWorldBoundsHeightN(int original) {
        return ~Config.borderAbsoluteMax;
    }
}
