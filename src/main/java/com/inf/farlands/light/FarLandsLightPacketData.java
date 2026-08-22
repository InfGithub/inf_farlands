package com.inf.farlands.light;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * Light data packet payload replacing vanilla
 * {@code ClientboundLightUpdatePacketData}.
 *
 * <p>Encodes absolute section Y per layer (VarInt + 2048-byte array)
 * instead of vanilla's {@code getMinLightSection() + index} scheme.
 */
@SuppressWarnings({ "null" })
public class FarLandsLightPacketData {

    final Int2ObjectMap<byte[]> skyLayers;   // sectionY → encoded DataLayer bytes
    final Int2ObjectMap<byte[]> blockLayers;

    /** Server-side: constructed by {@link FarLandsLightEngine#buildLightPacket}. */
    FarLandsLightPacketData(Int2ObjectMap<byte[]> sky, Int2ObjectMap<byte[]> block) {
        this.skyLayers = sky;
        this.blockLayers = block;
    }

    /** Client-side: decode from buffer. */
    private FarLandsLightPacketData(FriendlyByteBuf buf) {
        skyLayers = readLayerMap(buf);
        blockLayers = readLayerMap(buf);
    }

    // ==================== wire format ====================

    /**
     * Format: VarInt count → for each: VarInt sectionY + byte[2048].
     */
    public void write(FriendlyByteBuf buf) {
        writeLayerMap(buf, skyLayers);
        writeLayerMap(buf, blockLayers);
    }

    public static FarLandsLightPacketData read(FriendlyByteBuf buf, int cx, int cz) {
        return new FarLandsLightPacketData(buf);
    }

    private static void writeLayerMap(FriendlyByteBuf buf, Int2ObjectMap<byte[]> map) {
        // Sort by sectionY ascending for deterministic ordering
        List<Integer> keys = new ArrayList<>(map.keySet());
        keys.sort(Comparator.naturalOrder());
        buf.writeVarInt(keys.size());
        for (int sy : keys) {
            buf.writeVarInt(sy);
            buf.writeByteArray(map.get(sy));
        }
    }

    private static Int2ObjectMap<byte[]> readLayerMap(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Int2ObjectMap<byte[]> map = new Int2ObjectOpenHashMap<>(count);
        for (int i = 0; i < count; i++) {
            int sy = buf.readVarInt();
            byte[] data = buf.readByteArray();
            map.put(sy, data);
        }
        return map;
    }

    // ==================== client-side apply ====================

    /**
     * Apply received light data to the client engine. Called from
     * the client packet handler after decoding.
     */
    public void apply(FarLandsLightEngine engine, int cx, int cz) {
        for (var e : skyLayers.int2ObjectEntrySet()) {
            int sy = e.getIntKey();
            engine.queueSectionData(LightLayer.SKY,
                    SectionPos.of(cx, sy, cz), new DataLayer(e.getValue()));
        }
        for (var e : blockLayers.int2ObjectEntrySet()) {
            int sy = e.getIntKey();
            engine.queueSectionData(LightLayer.BLOCK,
                    SectionPos.of(cx, sy, cz), new DataLayer(e.getValue()));
        }
    }
}
