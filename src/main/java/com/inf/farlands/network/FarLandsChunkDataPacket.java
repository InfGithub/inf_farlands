package com.inf.farlands.network;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.DataLayer;

/**
 * 窗口滑动 section 包。
 *
 * 服务端管线在玩家 Y 窗口变化时发送：包级 windowMinY，持有边界中心 +
 * 变长 section 列表。section 数据以 byte[] 中转：LevelChunkSection.write
 * 含 biomes；解码需 biomeRegistry，StreamCodec 无 level 上下文 → handler 里
 * new LevelChunkSection(biomeRegistry) + read。
 *
 * 维度字段：客户端 handler 按当前 level 应用数据，tp 跨维度瞬间在途的旧维度
 * 包会污染新维度——发送端填维度，接收端校验不匹配即丢弃（在途旧包本就不该应用）。
 *
 * 光照双态编码：null = 无数据，客户端不 queueSectionData，保持
 * lightOnInSection fallback 语义；长度 1 = 均匀层，DataLayer.get(0,0,0)，
 * data==null 返回 defaultValue；长度 2049 = 0xFF + 2048 原始字节。
 *
 * sectionCount=0 空包不做：持有边界是 per-chunk 字段，空包无 chunk 信息
 * 无法更新；MIN_VALUE 守卫 WindowedChunk.lastPacketMinY 已覆盖未收包不丢弃，
 * 首个 section 包自然初始化边界。
 */
public record FarLandsChunkDataPacket(
        ResourceKey<Level> dimension,
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
        buffer.writeResourceKey(pkt.dimension());
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
        ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
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
        return new FarLandsChunkDataPacket(dimension, windowMinY, entries);
    }

    private static byte[] readLight(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        return buffer.readByteArray();
    }

    // ---- 服务端发送编码 ----

    /** null → null；均匀 → [v]；非均匀 → [0xFF, 2048 bytes] */
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

    // ---- 客户端 handler 解码 ----

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
