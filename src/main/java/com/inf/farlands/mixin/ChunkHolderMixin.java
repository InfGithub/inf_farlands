package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import com.inf.farlands.network.FarLandsSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin {

    @Redirect(
        method = "broadcastChanges",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkHolder;broadcast(Ljava/util/List;Lnet/minecraft/network/protocol/Packet;)V"
        )
    )
    private void redirectBroadcast(ChunkHolder self, List<ServerPlayer> players, Packet<?> packet) {
        if (packet instanceof ClientboundSectionBlocksUpdatePacket original) {
            FarLandsSectionBlocksUpdatePacket replacement = new FarLandsSectionBlocksUpdatePacket(original);
            int sx = replacement.sectionPos().x();
            int sz = replacement.sectionPos().z();
            int max = FarLandsConstants.MAX_CHUNK;
            if (Math.abs(sx) > max || Math.abs(sz) > max) {
                return;
            }
            for (ServerPlayer player : players) {
                PacketDistributor.sendToPlayer(player, replacement);
            }
        } else {
            players.forEach(player -> player.connection.send(packet));
        }
    }
}
