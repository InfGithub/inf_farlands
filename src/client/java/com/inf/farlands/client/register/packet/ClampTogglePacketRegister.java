package com.inf.farlands.client.register.packet;

import com.inf.farlands.client.network.Clientbounds;
import com.inf.farlands.debug.tool.clamp.ClampTogglePacket;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec;

public class ClampTogglePacketRegister {
    public static void register() {
        Clientbounds.register(new TypeAndCodec<>(
                ClampTogglePacket.TYPE,
                ClampTogglePacket.STREAM_CODEC));
    }
}
