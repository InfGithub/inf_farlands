package com.inf.farlands.tool.clamp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 客户端 F3+K 触发的钳制模式 toggle 请求：playToServer，无参数。 */
public record FarLandsClampTogglePacket() implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<FarLandsClampTogglePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("inf_farlands", "clamp_toggle"));

    public static final StreamCodec<FriendlyByteBuf, FarLandsClampTogglePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
            }, buffer -> new FarLandsClampTogglePacket());

    @Override
    public Type<FarLandsClampTogglePacket> type() {
        return TYPE;
    }
}