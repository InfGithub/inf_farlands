package com.inf.farlands.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.BiConsumer;

public record FarLandsSectionBlocksUpdatePacket(
    SectionPos sectionPos,
    short[] positions,
    BlockState[] states
) implements CustomPacketPayload {

    public static final Type<FarLandsSectionBlocksUpdatePacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("inf_farlands", "section_blocks"));

    public static final StreamCodec<FriendlyByteBuf, FarLandsSectionBlocksUpdatePacket> STREAM_CODEC =
        StreamCodec.of(
            (buffer, pkt) -> pkt.writeTo(buffer),
            FarLandsSectionBlocksUpdatePacket::readFrom
        );

    public FarLandsSectionBlocksUpdatePacket(ClientboundSectionBlocksUpdatePacket original) {
        this(
            reflectSectionPos(original),
            reflectPositions(original),
            reflectStates(original)
        );
    }

    private static SectionPos reflectSectionPos(ClientboundSectionBlocksUpdatePacket pkt) {
        try {
            java.lang.reflect.Field f = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("sectionPos");
            f.setAccessible(true);
            return (SectionPos) f.get(pkt);
        } catch (Exception e) {
            return SectionPos.of(0, 0, 0);
        }
    }

    private static short[] reflectPositions(ClientboundSectionBlocksUpdatePacket pkt) {
        try {
            java.lang.reflect.Field f = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("positions");
            f.setAccessible(true);
            return (short[]) f.get(pkt);
        } catch (Exception e) {
            return new short[0];
        }
    }

    private static BlockState[] reflectStates(ClientboundSectionBlocksUpdatePacket pkt) {
        try {
            java.lang.reflect.Field f = ClientboundSectionBlocksUpdatePacket.class.getDeclaredField("states");
            f.setAccessible(true);
            return (BlockState[]) f.get(pkt);
        } catch (Exception e) {
            return new BlockState[0];
        }
    }

    public void writeTo(FriendlyByteBuf buffer) {
        buffer.writeInt(sectionPos.x());
        buffer.writeInt(sectionPos.y());
        buffer.writeInt(sectionPos.z());
        buffer.writeVarInt(positions.length);
        for (int i = 0; i < positions.length; i++) {
            buffer.writeVarLong((long)Block.getId(states[i]) << 12 | (long)positions[i]);
        }
    }

    public static FarLandsSectionBlocksUpdatePacket readFrom(FriendlyByteBuf buffer) {
        SectionPos sp = SectionPos.of(buffer.readInt(), buffer.readInt(), buffer.readInt());
        int count = buffer.readVarInt();
        short[] pos = new short[count];
        BlockState[] st = new BlockState[count];
        for (int i = 0; i < count; i++) {
            long k = buffer.readVarLong();
            pos[i] = (short)((int)(k & 4095L));
            st[i] = Block.BLOCK_STATE_REGISTRY.byId((int)(k >>> 12));
        }
        return new FarLandsSectionBlocksUpdatePacket(sp, pos, st);
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
