package com.inf.farlands.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiConsumer;

/**
 * 批量方块变化包（vanilla ClientboundSectionBlocksUpdatePacket 的自定义替代）。
 *
 * vanilla 包用 SectionPos.asLong()（3int 哈希）编码，side-channel 是进程内的：
 * 服务端 putSection 在服务端进程，客户端解码 miss → 回退位解码 → 极端 Y 截断。
 * 本包用 3int（writeInt×3）编码。
 *
 * 维度字段：同 FarLandsChunkDataPacket——tp 跨维度在途旧包由接收端按维度丢弃。
 */
public record FarLandsSectionBlocksUpdatePacket(
        ResourceKey<Level> dimension,
        SectionPos sectionPos,
        short[] positions,
        BlockState[] states) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<FarLandsSectionBlocksUpdatePacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("inf_farlands", "section_blocks"));

    public static final StreamCodec<FriendlyByteBuf, FarLandsSectionBlocksUpdatePacket> STREAM_CODEC = StreamCodec.of(
            (buffer, pkt) -> pkt.writeTo(buffer),
            FarLandsSectionBlocksUpdatePacket::readFrom);

    public void writeTo(FriendlyByteBuf buffer) {
        buffer.writeResourceKey(dimension);
        buffer.writeInt(sectionPos.x());
        buffer.writeInt(sectionPos.y());
        buffer.writeInt(sectionPos.z());
        buffer.writeVarInt(positions.length);
        for (int i = 0; i < positions.length; i++) {
            buffer.writeVarLong((long) Block.getId(states[i]) << 12 | (long) positions[i]);
        }
    }

    public static FarLandsSectionBlocksUpdatePacket readFrom(FriendlyByteBuf buffer) {
        ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
        SectionPos sp = SectionPos.of(buffer.readInt(), buffer.readInt(), buffer.readInt());
        int count = buffer.readVarInt();
        short[] pos = new short[count];
        BlockState[] st = new BlockState[count];
        for (int i = 0; i < count; i++) {
            long k = buffer.readVarLong();
            pos[i] = (short) ((int) (k & 4095L));
            st[i] = Block.BLOCK_STATE_REGISTRY.byId((int) (k >>> 12));
        }
        return new FarLandsSectionBlocksUpdatePacket(dimension, sp, pos, st);
    }

    @Override
    public Type<FarLandsSectionBlocksUpdatePacket> type() {
        return TYPE;
    }

    public void runUpdates(BiConsumer<BlockPos, BlockState> consumer) {
        for (int i = 0; i < positions.length; i++) {
            BlockPos bp = sectionPos.relativeToBlockPos(positions[i]);
            consumer.accept(bp, states[i]);
        }
    }
}
