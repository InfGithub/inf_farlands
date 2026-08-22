package com.inf.farlands.network;

import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * 窗口滑动 section 包（window.md §5）。
 *
 * 服务端管线（§4.2）在玩家 Y 窗口变化时发送：包级 windowMinY（持有边界
 * 中心）+ 变长 section 列表。section 数据以 byte[] 中转（LevelChunkSection.write
 * 含 biomes；解码需 biomeRegistry，StreamCodec 无 level 上下文 → handler 里
 * new LevelChunkSection(biomeRegistry) + read）。
 *
 * 光照 X2 双态编码（§6）：null = 无数据（客户端不 queueSectionData，保持
 * lightOnInSection fallback 语义）；长度 1 = 均匀层（DataLayer.get(0,0,0)，
 * data==null 返回 defaultValue）；长度 2049 = 0xFF + 2048 原始字节。
 *
 * A'（sectionCount=0 空包）不做：持有边界是 per-chunk 字段，空包无 chunk
 * 信息无法更新；MIN_VALUE 守卫（WindowedChunk.lastPacketMinY）已覆盖其
 * 动机（未收包不丢弃），首个 section 包自然初始化边界。
 */
public record FarLandsChunkDataPacket(
        int windowMinY,
        List<SectionEntry> sections) implements CustomPacketPayload {

    public record SectionEntry(
            int chunkX,
            int chunkZ,
            int sectionY,
            byte[] sectionData,
            byte[] blockLight,
            byte[] skyLight) {
    }

    @SuppressWarnings("null")
    public static final Type<FarLandsChunkDataPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("inf_farlands", "chunk_data"));

    public static final StreamCodec<FriendlyByteBuf, FarLandsChunkDataPacket> STREAM_CODEC = StreamCodec.of(
            FarLandsChunkDataPacket::writeTo,
            FarLandsChunkDataPacket::readFrom);

    @SuppressWarnings("null")
    public static void writeTo(FriendlyByteBuf buffer, FarLandsChunkDataPacket pkt) {
        buffer.writeInt(pkt.windowMinY());
        buffer.writeVarInt(pkt.sections().size());
        for (SectionEntry e : pkt.sections()) {
            buffer.writeInt(e.chunkX());
            buffer.writeInt(e.chunkZ());
            buffer.writeVarInt(e.sectionY());
            buffer.writeByteArray(e.sectionData());
            writeLight(buffer, e.blockLight());
            writeLight(buffer, e.skyLight());
        }
    }

    private static void writeLight(FriendlyByteBuf buffer, byte[] light) {
        buffer.writeBoolean(light != null);
        if (light != null) {
            buffer.writeByteArray(light);
        }
    }

    public static FarLandsChunkDataPacket readFrom(FriendlyByteBuf buffer) {
        int windowMinY = buffer.readInt();
        int count = buffer.readVarInt();
        List<SectionEntry> entries = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int cx = buffer.readInt();
            int cz = buffer.readInt();
            int sy = buffer.readVarInt();
            byte[] sectionData = buffer.readByteArray();
            byte[] blockLight = readLight(buffer);
            byte[] skyLight = readLight(buffer);
            entries.add(new SectionEntry(cx, cz, sy, sectionData, blockLight, skyLight));
        }
        return new FarLandsChunkDataPacket(windowMinY, entries);
    }

    private static byte[] readLight(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        return buffer.readByteArray();
    }

    // ---- 服务端编码（§4.2 发送端用）----

    /** null → null（无数据）；均匀 → [v]；非均匀 → [0xFF, 2048 bytes] */
    public static byte[] encodeLight(DataLayer layer) {
        if (layer == null) {
            return null;
        }
        if (layer.isDefinitelyHomogenous()) {
            return new byte[] { (byte) layer.get(0, 0, 0) };
        }
        byte[] data = layer.getData();
        byte[] out = new byte[2049];
        out[0] = (byte) 0xFF;
        System.arraycopy(data, 0, out, 1, 2048);
        return out;
    }

    // ---- 客户端解码（handler 用）----

    public static DataLayer decodeLight(byte[] enc) {
        if (enc == null) {
            return null;
        }
        if (enc.length == 1) {
            return new DataLayer(enc[0] & 255);
        }
        byte[] data = new byte[2048];
        System.arraycopy(enc, 1, data, 0, 2048);
        return new DataLayer(data);
    }

    @Override
    public Type<FarLandsChunkDataPacket> type() {
        return TYPE;
    }
}
