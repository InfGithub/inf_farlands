package com.inf.farlands.mixin.axisY;

import com.inf.farlands.WindowSendState;
import com.inf.farlands.WindowedChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow
    private ServerLevel level;

    @Shadow
    protected abstract List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyPlayersWithChunkTracked);

    // 服务端窗口死状态（window.md §4.4）：不再在 mark 时 moveWindowTo，
    // windowMinY 保持构造默认 -4——发送范围由 per-player ThreadLocal 决定。

    /**
     * per-player 发送：每个玩家设自己的窗口 ThreadLocal 后发包，
     * extractChunkData 据此只发该玩家窗口内的非空 section。
     */
    @SuppressWarnings("null")
    @Overwrite
    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap<>();

        for (ChunkAccess chunkaccess : chunks) {
            ChunkPos chunkpos = chunkaccess.getPos();
            LevelChunk levelchunk;
            if (chunkaccess instanceof LevelChunk levelchunk1) {
                levelchunk = levelchunk1;
            } else {
                levelchunk = this.level.getChunk(chunkpos.x, chunkpos.z);
            }

            for (ServerPlayer serverplayer : this.getPlayers(chunkpos, false)) {
                map.computeIfAbsent(serverplayer, p -> new ArrayList<>()).add(levelchunk);
            }
        }

        map.forEach((serverplayer, list) -> {
            int centerY = Mth.floorDiv(serverplayer.getBlockY(), 16);
            WindowSendState.setWindowMinY(centerY - WindowedChunk.WINDOW_HALF_BELOW);
            try {
                serverplayer.connection.send(ClientboundChunksBiomesPacket.forChunks(list));
            } finally {
                WindowSendState.clear();
            }
        });
    }
}
