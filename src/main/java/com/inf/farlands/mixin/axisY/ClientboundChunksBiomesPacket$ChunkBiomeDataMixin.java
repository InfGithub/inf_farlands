package com.inf.farlands.mixin.axisY;

import com.inf.farlands.WindowSendState;

import java.util.List;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ClientboundChunksBiomesPacket.ChunkBiomeData.class)
public class ClientboundChunksBiomesPacket$ChunkBiomeDataMixin {

    @Overwrite
    private static int calculateChunkSize(LevelChunk chunk) {
        int size = 5;
        for (Map.Entry<Integer, LevelChunkSection> e : WindowSendState.sendableSections(chunk)) {
            size += 5;
            size += e.getValue().getBiomes().getSerializedSize();
        }
        return size;
    }

    @Overwrite
    public static void extractChunkData(FriendlyByteBuf buffer, LevelChunk chunk) {
        List<Map.Entry<Integer, LevelChunkSection>> toSend = WindowSendState.sendableSections(chunk);
        buffer.writeVarInt(toSend.size());
        for (Map.Entry<Integer, LevelChunkSection> e : toSend) {
            buffer.writeVarInt(e.getKey());
            e.getValue().getBiomes().write(buffer);
        }
    }
}
