package com.inf.farlands.mixin.axisY;

import com.inf.farlands.InfFarlands;
import com.inf.farlands.WindowedChunk;
import com.inf.farlands.light.FarLandsLightEngine;
import com.inf.farlands.light.FarLandsLightPacketData;

import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.protocol.PacketUtils;

/**
 * priority = 100：Mixin 的 priority 数字越小越先应用（默认 1000）。本类
 * 的 @Overwrite 必须先于 sodium 的 core.world.map.ClientPacketListenerMixin
 * 应用——sodium 的 handleForgetLevelChunk @Inject RETURN（ChunkTracker.
 * onChunkStatusRemoved）注入到我们的新方法体，卸载清理钩子保留（若 sodium
 * 先应用，其注入会被我们的 @Overwrite 整体覆盖而丢失）。
 */
@Mixin(value = ClientPacketListener.class, priority = 100)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private net.minecraft.client.multiplayer.ClientLevel level;

    /**
     * Fix: mark sections that actually have data (allSections) instead of
     * window array. The window is a view maintained by repositionCamera
     * (render frame); a fresh chunk keeps its constructor window (1 section)
     * until then, so window-based iteration misses most sections.
     */
    @SuppressWarnings("null")
    @Overwrite
    public void handleChunksBiomes(ClientboundChunksBiomesPacket packet) {
        PacketUtils.ensureRunningOnSameThread(
                packet,
                (ClientPacketListener) (Object) this,
                Minecraft.getInstance());

        for (ClientboundChunksBiomesPacket.ChunkBiomeData data : packet.chunkBiomeData()) {
            this.level
                    .getChunkSource()
                    .replaceBiomes(data.pos().x, data.pos().z, data.getReadBuffer());
        }

        for (ClientboundChunksBiomesPacket.ChunkBiomeData data : packet.chunkBiomeData()) {
            this.level.onChunkLoaded(new ChunkPos(data.pos().x, data.pos().z));
        }

        for (ClientboundChunksBiomesPacket.ChunkBiomeData data : packet.chunkBiomeData()) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    ChunkAccess ca = this.level.getChunkSource().getChunk(
                            data.pos().x + i, data.pos().z + j, ChunkStatus.FULL, false);
                    if (ca instanceof LevelChunk c) {
                        for (Integer sectionY : ((WindowedChunk) c).windowedAllSections().keySet()) {
                            Minecraft.getInstance().levelRenderer.setSectionDirty(
                                    data.pos().x + i, sectionY, data.pos().z + j);
                        }
                    }
                }
            }
        }
    }

    /**
     * Fix: capture chunk section Ys before drop, then clean light
     * data for actual sections instead of level min/max range.
     * At ±2.14B chunk sections are at Y ~134M, not -4..19.
     */
    @SuppressWarnings("null")
    @Overwrite
    public void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket packet) {
        net.minecraft.network.protocol.PacketUtils.ensureRunningOnSameThread(packet,
                (ClientPacketListener) (Object) this, Minecraft.getInstance());
        ChunkPos cpos = packet.pos();

        int[] sectionYs = null;
        ChunkAccess ca = this.level.getChunkSource().getChunk(cpos.x, cpos.z, ChunkStatus.FULL, false);
        if (ca instanceof LevelChunk c) {
            java.util.Set<Integer> keys = ((WindowedChunk) c).windowedAllSections().keySet();
            sectionYs = new int[keys.size()];
            int idx = 0;
            for (Integer sy : keys) {
                sectionYs[idx++] = sy;
            }
        }
        final int[] ys = sectionYs;

        this.level.getChunkSource().drop(cpos);

        this.level.queueLightUpdate(() -> {
            LevelLightEngine le = this.level.getLightEngine();
            le.setLightEnabled(cpos, false);
            if (ys != null) {
                for (int sy : ys) {
                    le.queueSectionData(LightLayer.BLOCK, SectionPos.of(cpos, sy), null);
                    le.queueSectionData(LightLayer.SKY, SectionPos.of(cpos, sy), null);
                }
                for (int sy : ys) {
                    le.updateSectionStatus(SectionPos.of(cpos, sy), true);
                }
            } else {
                for (int i = le.getMinLightSection(); i < le.getMaxLightSection(); i++) {
                    le.queueSectionData(LightLayer.BLOCK, SectionPos.of(cpos, i), null);
                    le.queueSectionData(LightLayer.SKY, SectionPos.of(cpos, i), null);
                }
                for (int j = this.level.getMinSection(); j < this.level.getMaxSection(); j++) {
                    le.updateSectionStatus(SectionPos.of(cpos, j), true);
                }
            }
        });
    }

    /**
     * Fix: mark sections that actually have data (allSections) instead of
     * the window array. The window is a view maintained by repositionCamera
     * (render frame); a fresh chunk keeps its constructor window (1 section)
     * until then, so window-based iteration misses most sections.
     */
    @SuppressWarnings("null")
    @Overwrite
    private void enableChunkLight(LevelChunk chunk, int x, int z) {
        LevelLightEngine lightEngine = this.level.getChunkSource().getLightEngine();
        ChunkPos chunkPos = chunk.getPos();
        for (Map.Entry<Integer, LevelChunkSection> e : ((WindowedChunk) chunk).windowedAllSections().entrySet()) {
            LevelChunkSection section = e.getValue();
            if (section == null) {
                continue;
            }
            int sectionY = e.getKey();
            SectionPos secPos = SectionPos.of(chunkPos, sectionY);
            boolean air = section.hasOnlyAir();
            lightEngine.updateSectionStatus(secPos, air);
            this.level.setSectionDirtyWithNeighbors(x, sectionY, z);
        }
    }

    private static final java.lang.reflect.Field FARLANDS_LIGHT_FIELD;
    static {
        try {
            FARLANDS_LIGHT_FIELD = ClientboundLevelChunkWithLightPacket.class
                    .getDeclaredField("farlandsLightData");
            FARLANDS_LIGHT_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Inject(method = "handleLevelChunkWithLight", at = @At("RETURN"))
    private void onLevelChunkWithLight(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        if (!(this.level.getLightEngine() instanceof FarLandsLightEngine fle)) return;
        try {
            FarLandsLightPacketData fd = (FarLandsLightPacketData) FARLANDS_LIGHT_FIELD.get(packet);
            if (fd != null) fd.apply(fle, packet.getX(), packet.getZ());
        } catch (Exception e) {
            // 不静默吞错（历史教训：catch ignored 掩盖 farlandsLightData 应用失败）
            InfFarlands.LOGGER.error("FLPKT apply EXCEPTION chunk={},{}", packet.getX(), packet.getZ(), e);
        }
    }
}
