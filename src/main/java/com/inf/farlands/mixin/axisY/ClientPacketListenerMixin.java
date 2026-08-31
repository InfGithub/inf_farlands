package com.inf.farlands.mixin.axisY;

import com.inf.farlands.InfFarlands;
import com.inf.farlands.window.WindowedChunk;
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
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.PacketUtils;

/**
 * priority = 100：Mixin 的 priority 数字越小越先应用，默认 1000。本类
 * 的 @Overwrite 必须先于 sodium 的 core.world.map.ClientPacketListenerMixin
 * 应用——sodium 的 handleForgetLevelChunk @Inject RETURN 注入
 * ChunkTracker.onChunkStatusRemoved 到我们的新方法体，卸载清理钩子保留；
 * 若 sodium 先应用，其注入会被我们的 @Overwrite 整体覆盖而丢失。
 */
@Mixin(value = ClientPacketListener.class, priority = 100)
public abstract class ClientPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    /**
     * 修复：标记实际有数据的 section，即 allSections，而非窗口数组。窗口是
     * repositionCamera 维护的视图，对应渲染帧；新 chunk 在此之前保持构造窗口，
     * 只有 1 个 section，按窗口迭代会漏掉大部分 section。
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
     * 修复：卸载前捕获 chunk 的 section Y，然后清理实际 section 的光照数据，
     * 而非 level 的最小/最大范围。±2.14B 下 chunk section 在 Y ~134M，不是 -4..19。
     */
    @SuppressWarnings("null")
    @Overwrite
    public void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket packet) {
        PacketUtils.ensureRunningOnSameThread(packet,
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

        // §5 缓存清理：chunk 卸载 → 丢弃未应用的 §5 数据，防残留堆积（按当前维度 key）
        InfFarlands.discardPendingSectionData(this.level.dimension(), cpos);

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
     * 修复：标记实际有数据的 section，即 allSections，而非窗口数组。窗口是
     * repositionCamera 维护的视图，对应渲染帧；新 chunk 在此之前保持构造窗口，
     * 只有 1 个 section，按窗口迭代会漏掉大部分 section。
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
            // 不静默吞错：catch ignored 会掩盖 farlandsLightData 应用失败
            InfFarlands.LOGGER.error("FLPKT apply EXCEPTION chunk={},{}", packet.getX(), packet.getZ(), e);
        }
        // §5 补应用：chunk 加载完成，即 replaceWithPacketData 之后 → 应用此前因 chunk 未加载
        // 而缓存的 §5 section 数据，防方块数据永久缺失——空缺/双端不同步。
        InfFarlands.applyPendingSectionData(this.level.dimension(), packet.getX(), packet.getZ());
    }
}
