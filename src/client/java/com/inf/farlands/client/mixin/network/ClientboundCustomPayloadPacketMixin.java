package com.inf.farlands.client.mixin.network;

import java.util.ArrayList;
import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.inf.farlands.client.network.ClientPacketHandlers;
import com.inf.farlands.client.network.Clientbounds;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

@Mixin(ClientboundCustomPayloadPacket.class)
public class ClientboundCustomPayloadPacketMixin {

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 0))
    private static StreamCodec<RegistryFriendlyByteBuf, CustomPacketPayload> addGameplayTypes(
            CustomPacketPayload.FallbackProvider<RegistryFriendlyByteBuf> fallback,
            List<CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, ?>> types) {
        List<CustomPacketPayload.TypeAndCodec<? super RegistryFriendlyByteBuf, ?>> extended = new ArrayList<>(types);
        for (int i = 0; i < Clientbounds.gameplayBounds.size(); i++) {
            extended.add(Clientbounds.gameplayBounds.get(i));
        }
        return CustomPacketPayload.codec(fallback, extended);
    }

    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;codec(Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload$FallbackProvider;Ljava/util/List;)Lnet/minecraft/network/codec/StreamCodec;", ordinal = 1))
    private static StreamCodec<FriendlyByteBuf, CustomPacketPayload> addConfigTypes(
            CustomPacketPayload.FallbackProvider<FriendlyByteBuf> fallback,
            List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> types) {
        List<CustomPacketPayload.TypeAndCodec<? super FriendlyByteBuf, ?>> extended = new ArrayList<>(types);
        for (int i = 0; i < Clientbounds.configBounds.size(); i++) {
            extended.add(Clientbounds.configBounds.get(i));
        }
        return CustomPacketPayload.codec(fallback, extended);
    }

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