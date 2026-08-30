package com.inf.farlands.mixin.tick;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

import com.inf.farlands.util.tick.TickHead;
import com.inf.farlands.util.tick.TickEnd;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Shadow
    private int tickCount;

    @Inject(method = "tickServer", at = @At("HEAD"))
    private void onServerTickStart(CallbackInfo ci) {
        TickHead.head(tickCount);
    }

    @Inject(method = "tickServer", at = @At("RETURN"))
    private void onServerTickEnd(CallbackInfo ci) {
        TickEnd.end(tickCount);
    }
}
