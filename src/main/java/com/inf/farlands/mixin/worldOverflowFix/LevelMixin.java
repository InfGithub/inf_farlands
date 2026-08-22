package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
// import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {

    // @Overwrite
    // private static boolean isInWorldBoundsHorizontal(BlockPos pos) {
    // return (pos.getX() > ~Config.borderAbsoluteMax &&
    // pos.getZ() > ~Config.borderAbsoluteMax &&
    // pos.getX() < Config.borderAbsoluteMax &&
    // pos.getZ() < Config.borderAbsoluteMax);
    // }

    @Inject(method = "isInSpawnableBounds", at = @At("HEAD"), cancellable = true)
    private static void onIsInSpawnableBounds(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }

    @ModifyConstant(method = "getHeight", constant = @Constant(intValue = -30000000))
    private int expandMinHeightCheck(int original) {
        return ~Config.borderAbsoluteMax;
    }

    @ModifyConstant(method = "getHeight", constant = @Constant(intValue = 30000000))
    private int expandMaxHeightCheck(int original) {
        return Config.borderAbsoluteMax;
    }
}
