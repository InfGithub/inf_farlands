package com.inf.farlands.mixin;

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

    @ModifyConstant(method = "isInWorldBoundsHorizontal", constant = @Constant(intValue = 30000000))
    private static int modifyWorldBounds(int original) {
        return Config.borderAbsoluteMax;
    }
}
