package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.world.level.border.WorldBorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldBorder.class)
public class WorldBorderMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void raiseBorderLimitInit(CallbackInfo ci) {
        WorldBorder self = (WorldBorder) (Object) this;
        int absMax = Config.borderAbsoluteMax;
        self.setAbsoluteMaxSize(absMax);
        self.setSize((double) absMax * 2.0);
    }

    @Inject(method = "setSize", at = @At("HEAD"), cancellable = true)
    private void forceMaxSize(double size, CallbackInfo ci) {
        double target = (double) Config.borderAbsoluteMax * 2.0;
        if (size < target) {
            ((WorldBorder) (Object) this).setSize(target);
            ci.cancel();
        }
    }
}
