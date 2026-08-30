package com.inf.farlands.client.mixin.network;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.inf.farlands.client.network.ClientPacketHandlers;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@Mixin(ClientboundCustomPayloadPacket.class)
public class ClientboundCustomPayloadPacketMixin {

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private void onHandle(ClientCommonPacketListener listener, CallbackInfo ci) {
        if (listener instanceof ClientPacketListener clientListener) {
            CustomPacketPayload payload = ((ClientboundCustomPayloadPacket) (Object) this).payload();
            if (ClientPacketHandlers.handle(payload, clientListener)) {
                ci.cancel();
            }
        }
    }
}