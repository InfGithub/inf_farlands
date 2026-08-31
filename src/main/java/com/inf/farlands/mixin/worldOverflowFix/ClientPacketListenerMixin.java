package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.window.WindowedChunk;
import com.inf.farlands.util.WorldBounds;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    // BlockUpdate 包接收校验——XZ 超界或 sectionY 不在客户端视图窗口 → 拒绝。
    // 窗口外数据写入后会被丢弃，源头拒绝更干净。
    // vanilla 批量包 + 自定义包等批量路径不经过此处，由丢弃兜底，无循环风险。
    @SuppressWarnings("null")
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void filterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        int x = pos.getX();
        int z = pos.getZ();
        if (!WorldBounds.inBlockXZ(x, z)) {
            ci.cancel();
            return;
        }
        if (this.level == null) {
            return;
        }
        int sy = SectionPos.blockToSectionCoord(pos.getY());
        ChunkAccess chunk = this.level.getChunkSource().getChunk(
                SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z), ChunkStatus.FULL, false);
        if (chunk instanceof LevelChunk lc) {
            WindowedChunk wc = (WindowedChunk) lc;
            if (sy < wc.getWindowMinY() || sy > wc.getWindowMaxY()) {
                ci.cancel();
                return;
            }
        }
    }
}
