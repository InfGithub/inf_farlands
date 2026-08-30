package com.inf.farlands.register.packet;

import com.inf.farlands.debug.tool.clamp.ClampStatePacket;

import com.inf.farlands.network.Commonbounds;
import com.inf.farlands.network.Serverbounds;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec;

public class ClampStatePacketRegister {
    public static void registerType() {
        Serverbounds.register(new TypeAndCodec<>(
                ClampStatePacket.TYPE,
                ClampStatePacket.STREAM_CODEC));
        Commonbounds.register(new TypeAndCodec<>(
                ClampStatePacket.TYPE,
                ClampStatePacket.STREAM_CODEC));
    }
}
