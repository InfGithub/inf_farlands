package com.inf.farlands.mixin.axisY;

import com.inf.farlands.window.WindowSendState;

import java.util.List;
import java.util.Map;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(ClientboundLevelChunkPacketData.class)
public class ClientboundLevelChunkPacketDataMixin {

    @Overwrite
    private static int calculateChunkSize(LevelChunk chunk) {
        int i = 5;
        for (Map.Entry<Integer, LevelChunkSection> e : WindowSendState.sendableSections(chunk)) {
            i += 5 + e.getValue().getSerializedSize();
        }
        return i;
    }

    @Overwrite
    public static void extractChunkData(FriendlyByteBuf buffer, LevelChunk chunk) {
        List<Map.Entry<Integer, LevelChunkSection>> toSend = WindowSendState.sendableSections(chunk);
        buffer.writeVarInt(toSend.size());
        for (Map.Entry<Integer, LevelChunkSection> e : toSend) {
            buffer.writeVarInt(e.getKey());
            e.getValue().write(buffer);
        }
    }
}
