package com.inf.farlands.client.network;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ClientPacketHandlers {
    private static final Map<CustomPacketPayload.Type<?>, BiConsumer<? extends CustomPacketPayload, ClientPacketListener>> HANDLERS = new HashMap<>();

    public static <T extends CustomPacketPayload> void register(
            CustomPacketPayload.Type<T> type,
            BiConsumer<T, ClientPacketListener> handler) {
        HANDLERS.put(type, handler);
    }

    @SuppressWarnings("unchecked")
    public static boolean handle(CustomPacketPayload payload, ClientPacketListener listener) {
        BiConsumer<CustomPacketPayload, ClientPacketListener> handler = (BiConsumer<CustomPacketPayload, ClientPacketListener>) HANDLERS
                .get(payload.type());
        if (handler != null) {
            handler.accept(payload, listener);
            return true;
        }
        return false;
    }
}