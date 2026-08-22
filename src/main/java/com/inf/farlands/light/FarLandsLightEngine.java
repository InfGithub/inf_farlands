package com.inf.farlands.light;

import com.inf.farlands.IntSectionPos;
import com.inf.farlands.WindowedChunk;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LayerLightEventListener;

/**
 * FarLands light engine replacing vanilla {@code LevelLightEngine} and
 * {@code ThreadedLevelLightEngine}.
 *
 * <p>Extends {@code ThreadedLevelLightEngine} for field-type compatibility
 * with {@code ChunkMap} and {@code ServerChunkCache}. Vanilla sky/block
 * engines created by {@code super()} are unused — all public methods are
 * overridden to delegate to our {@link FarLandsSkyLightEngine} and
 * {@link FarLandsBlockLightEngine}.
 */
@SuppressWarnings({ "null"})
public class FarLandsLightEngine extends ThreadedLevelLightEngine {

    final FarLandsSkyLightEngine skyEngine;
    final FarLandsBlockLightEngine blockEngine;
    final LightChunkGetter chunkSource;

    /** Server-side constructor called via {@code ChunkMap}. */
    public FarLandsLightEngine(
            LightChunkGetter chunkSource,
            net.minecraft.server.level.ChunkMap chunkMap,
            boolean skyLight,
            net.minecraft.util.thread.ProcessorMailbox<Runnable> taskMailbox,
            net.minecraft.util.thread.ProcessorHandle<
                net.minecraft.server.level.ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox) {
        super(chunkSource, chunkMap, skyLight, taskMailbox, sorterMailbox);
        this.chunkSource = chunkSource;
        this.skyEngine = skyLight ? new FarLandsSkyLightEngine(chunkSource) : null;
        this.blockEngine = new FarLandsBlockLightEngine(chunkSource);
    }

    /** Client-side constructor called via {@code ClientChunkCache}. */
    public FarLandsLightEngine(LightChunkGetter chunkSource, boolean hasSkyLight) {
        super(chunkSource, null, hasSkyLight,
                net.minecraft.util.thread.ProcessorMailbox.create(
                        net.minecraft.Util.backgroundExecutor(), "farlands-light"),
                null);
        this.chunkSource = chunkSource;
        this.skyEngine = hasSkyLight ? new FarLandsSkyLightEngine(chunkSource) : null;
        this.blockEngine = new FarLandsBlockLightEngine(chunkSource);
    }

    @Override
    public void close() {
        // no-op — vanilla engines created by super() are unused
    }

    @Override
    protected void updateChunkStatus(ChunkPos pos) {
        // 服务端 chunk 卸载（ChunkMap L552，唯一调用点）：清理该 chunk 全部光照层。
        // 同步直调（不走 vanilla addTask——任务会入 lightTasks 无人消费，24cca1b4 启动挂起教训）；
        // 卸载在 thenRunAsync 异步线程，storage 为 ConcurrentHashMap 线程安全。
        blockEngine.removeChunk(pos);
        if (skyEngine != null) skyEngine.removeChunk(pos);
    }

    @Override
    public CompletableFuture<ChunkAccess> lightChunk(ChunkAccess chunk, boolean isLighted) {
        if (chunk != null && !isLighted) propagateLightSources(chunk.getPos());
        chunk.setLightCorrect(true);
        return CompletableFuture.completedFuture(chunk);
    }

    // === LightEventListener ===

    @Override
    public void checkBlock(BlockPos pos) {
        blockEngine.checkBlock(pos);
        if (skyEngine != null) skyEngine.checkBlock(pos);
    }

    @Override
    public boolean hasLightWork() {
        return (skyEngine != null && skyEngine.hasLightWork())
                || blockEngine.hasLightWork();
    }

    @Override
    public int runLightUpdates() {
        int i = 0;
        i += blockEngine.runLightUpdates();
        if (skyEngine != null) i += skyEngine.runLightUpdates();
        return i;
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        blockEngine.updateSectionStatus(pos, isEmpty);
        if (skyEngine != null) skyEngine.updateSectionStatus(pos, isEmpty);
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enabled) {
        blockEngine.setLightEnabled(pos, enabled);
        if (skyEngine != null) skyEngine.setLightEnabled(pos, enabled);
    }

    @Override
    public void propagateLightSources(ChunkPos pos) {
        blockEngine.propagateLightSources(pos);
        if (skyEngine != null) skyEngine.propagateLightSources(pos);
    }

    // ==================== LevelLightEngine overrides ====================

    @Override
    public int getRawBrightness(BlockPos pos, int skyDarken) {
        int sky = skyEngine == null ? 0 : skyEngine.getLightValue(pos) - skyDarken;
        int block = blockEngine.getLightValue(pos);
        return Math.max(block, sky);
    }

    @Override
    public LayerLightEventListener getLayerListener(LightLayer layer) {
        if (layer == LightLayer.BLOCK) return blockEngine;
        return skyEngine != null ? skyEngine : LayerLightEventListener.DummyLightLayerEventListener.INSTANCE;
    }

    @Override
    public void queueSectionData(LightLayer layer, SectionPos pos, DataLayer data) {
        if (layer == LightLayer.BLOCK) {
            if (data != null) blockEngine.setDataLayer(pos.asLong(), data);
            else blockEngine.updateSectionStatus(pos, true);
        } else if (skyEngine != null) {
            if (data != null) skyEngine.setDataLayer(pos.asLong(), data);
            else skyEngine.updateSectionStatus(pos, true);
        }
    }

    @Override
    public boolean lightOnInSection(SectionPos pos) {
        long key = pos.asLong();
        if (blockEngine.getDataLayer(key) != null) return true;
        if (skyEngine != null && skyEngine.getDataLayer(key) != null) return true;
        // Fallback: chunk loaded = compilable (phantom block fix)
        IntSectionPos sp = IntSectionPos.getSectionPos(key);
        return chunkSource.getChunkForLighting(sp.x, sp.z) != null;
    }

    @Override
    public void retainData(ChunkPos pos, boolean retain) {
        // no-op — single-layer storage has no retention concept
    }

    // ==================== new public API ====================

    /** Direct access for persistence (§5 packets, ChunkSerializer). */
    public DataLayer getSkyDataLayer(SectionPos pos) {
        return skyEngine == null ? null : skyEngine.getDataLayer(pos.asLong());
    }

    /** Direct access for persistence (§5 packets, ChunkSerializer). */
    public DataLayer getBlockDataLayer(SectionPos pos) {
        return blockEngine.getDataLayer(pos.asLong());
    }

    // ==================== initializeLight (async) ====================

    @Override
    public CompletableFuture<ChunkAccess> initializeLight(ChunkAccess chunk, boolean lightEnabled) {
        return CompletableFuture.supplyAsync(() -> {
            if (chunk instanceof WindowedChunk wc) {
                for (var e : wc.windowedAllSections().entrySet()) {
                    LevelChunkSection s = e.getValue();
                    if (s == null || s.hasOnlyAir()) continue;
                    SectionPos sp = SectionPos.of(chunk.getPos(), e.getKey());
                    blockEngine.updateSectionStatus(sp, false);
                    if (skyEngine != null) skyEngine.updateSectionStatus(sp, false);
                }
            }
            return chunk;
        });
    }

    public FarLandsLightPacketData buildLightPacket(ChunkPos pos) {
        var sky = new Int2ObjectOpenHashMap<byte[]>();
        var block = new Int2ObjectOpenHashMap<byte[]>();
        var lc = chunkSource.getChunkForLighting(pos.x, pos.z);
        // getChunkForLighting 可能返回 ChunkSerializer.read 的 ImposterProtoChunk——它的
        // allSections 只含窗口 section（构造时从窗口视图转移），极端 Y section 数据在 wrapped
        // LevelChunk（loadWindowSections 写入 ipc.getWrapped()）——必须解包取真实数据，否则
        // 打包 0 层（重进极端 Y 光照黑）。
        if (lc instanceof ImposterProtoChunk ipc) {
            lc = ipc.getWrapped();
        }
        if (!(lc instanceof WindowedChunk wc)) return new FarLandsLightPacketData(sky, block);
        for (var e : wc.windowedAllSections().entrySet()) {
            int sy = e.getKey();
            LevelChunkSection sec = e.getValue();
            if (sec == null || sec.hasOnlyAir()) continue;
            SectionPos sp = SectionPos.of(pos, sy);
            if (skyEngine != null) {
                DataLayer sl = skyEngine.getDataLayer(sp.asLong());
                if (sl != null && !sl.isEmpty()) sky.put(sy, sl.copy().getData());
            }
            DataLayer bl = blockEngine.getDataLayer(sp.asLong());
            if (bl != null && !bl.isEmpty()) block.put(sy, bl.copy().getData());
        }
        return new FarLandsLightPacketData(sky, block);
    }

    // ==================== ChunkMap compatibility ====================

    private boolean runningLightUpdates;

    public void tryScheduleUpdate() {
        if (!runningLightUpdates && hasLightWork()) {
            runningLightUpdates = true;
            try {
                runLightUpdates();
            } finally {
                runningLightUpdates = false;
            }
        }
    }

    public CompletableFuture<?> waitForPendingTasks(int x, int z) {
        return CompletableFuture.completedFuture(null);
    }
}
