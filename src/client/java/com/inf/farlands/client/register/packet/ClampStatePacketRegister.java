package com.inf.farlands.client.register.packet;

import com.inf.farlands.client.network.ClientPacketHandlers;
import com.inf.farlands.client.network.Clientbounds;
import com.inf.farlands.debug.tool.clamp.ClampMode;
import com.inf.farlands.debug.tool.clamp.ClampStatePacket;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.TypeAndCodec;

public class ClampStatePacketRegister {
    public static void registerType() {
        Clientbounds.register(new TypeAndCodec<>(
                ClampStatePacket.TYPE,
                ClampStatePacket.STREAM_CODEC));
    }

    public static void registerHanlder() {
        ClientPacketHandlers.register(
                ClampStatePacket.TYPE,
                (payload, context) -> {
                    if (Minecraft.getInstance().player instanceof ClampMode clamp) {
                        clamp.setClampEnabled(payload.enabled());
                    }
                });
    }
}
