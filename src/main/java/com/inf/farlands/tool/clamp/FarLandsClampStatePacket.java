package com.inf.farlands.tool.clamp;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端钳制模式状态同步（toggle 后发送，客户端据此钳制预测位置）。 */
public record FarLandsClampStatePacket(boolean enabled) implements CustomPacketPayload {
    @SuppressWarnings("null")
    public static final Type<FarLandsClampStatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("inf_farlands", "clamp_state"));

    public static final StreamCodec<FriendlyByteBuf, FarLandsClampStatePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeBoolean(payload.enabled()),
            buffer -> new FarLandsClampStatePacket(buffer.readBoolean()));

    @Override
    public Type<FarLandsClampStatePacket> type() {
        return TYPE;
    }
}