package com.inf.farlands.mixin.expand.xz.border;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.inf.farlands.FarlandsConfig;

import net.minecraft.server.dedicated.DedicatedServerProperties;

@Mixin(DedicatedServerProperties.class)
public class DedicatedServerPropertiesMixin {

    @Shadow
    @Final
    @Mutable
    private int maxWorldSize;

    private void setMaxWorldSize(int value) {
        maxWorldSize = value;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void OnInit(CallbackInfo ci) {
        setMaxWorldSize(FarlandsConfig.borderAbsoluteMax - 16);
    }
}
