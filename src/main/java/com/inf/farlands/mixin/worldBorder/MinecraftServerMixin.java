package com.inf.farlands.mixin.worldBorder;

import com.inf.farlands.Config;
import com.inf.farlands.Constants;
import com.inf.farlands.InfFarlands;
import com.inf.farlands.terrain.pipeline.FarLandsGenState;
import com.inf.farlands.terrain.pipeline.GenQueue;

import it.unimi.dsi.fastutil.longs.LongIterator;

import java.util.Map;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;

import org.slf4j.Logger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@SuppressWarnings("null")
@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    // 方法：
    // public int getAbsoluteMaxWorldSize() {
    // return 29999984;
    // }

    @Overwrite
    public int getAbsoluteMaxWorldSize() {
        return Config.borderAbsoluteMax - 16; // chunk
    }

    // ---- fsa：禁用 spawn chunk 预生成等待 ----
    // 原版 prepareLevels 用 while (getTickingGenerated() < j) 阻塞等待出生点 spawn chunk
    // 预生成完成。跳过等待：地形由 GenQueue 按需驱动；forced chunks 恢复保留。

    @Shadow
    private static Logger LOGGER;

    @Shadow
    private Map<ResourceKey<Level>, ServerLevel> levels;

    @Shadow
    protected long nextTickTimeNanos;

    @Shadow
    private static long PREPARE_LEVELS_DEFAULT_DELAY_NANOS;

    @Shadow
    protected void waitUntilNextTick() {
    }

    @Shadow
    public final ServerLevel overworld() {
        return null;
    }

    @Shadow
    private void updateMobSpawningFlags() {
    }

    /**
     * 预加载出生点 7×7×7：XZ ±3 chunk × Y ±3 section，中心出生点 y——阻塞等待完成，
     * Preparing start region 进度可见。不恢复 START 常驻：ticket 保留到第一个玩家
     * 加入，InfFarlands.onPlayerJoin 移除，视距 ticket 接管——chunk 不卸载不重载，
     * 避免卸载-重载读回/写盘竞态，预加载退化 + 掉虚空；玩家离开后仍按需卸载落盘。
     *
     * 驱动链：ticket → chunk 调度依赖 ServerChunkCache.tick 的
     * runDistanceManagerUpdates，prepareLevels 期间主循环未开始 → 手动调 cache.tick 驱动；
     * 短路链 scheduleChunkLoad 的 thenApplyAsync mainThreadExecutor 由 waitUntilNextTick
     * runAllTasks 驱动；生成 GenQueue.preload 指定 section 由 genPool 异步推进。
     * 每轮 sleep(2ms) 让出 CPU，否则 Server thread 忙循环饿死同 JVM 渲染线程——画面动画不动。
     */
    @Unique
    private void preloadSpawnArea(ServerLevel level, BlockPos spawnPos, ChunkProgressListener listener) {
        int spawnCx = SectionPos.blockToSectionCoord(spawnPos.getX());
        int spawnCz = SectionPos.blockToSectionCoord(spawnPos.getZ());
        int spawnSy = SectionPos.blockToSectionCoord(spawnPos.getY());
        int minSy = Math.max(spawnSy - 3, -Constants.MAX_CHUNK);
        int maxSy = Math.min(spawnSy + 3, Constants.MAX_CHUNK - 1);
        ServerChunkCache cache = level.getChunkSource();
        int total = (maxSy - minSy + 1) * 49;
        long deadline = Util.getNanos() + 60_000_000_000L; // 超时 60s 兜底，不阻塞进世界

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                ChunkPos cp = new ChunkPos(spawnCx + dx, spawnCz + dz);
                cache.addRegionTicket(InfFarlands.PRELOAD_TICKET, cp, 0, Unit.INSTANCE);
                InfFarlands.registerPreloadChunk(cp); // 玩家加入后由 onPlayerJoin 移除
            }
        }

        int ready = 0;
        while (Util.getNanos() < deadline) {
            cache.tick(() -> false, false); // 手动驱动 ticket → chunk 调度 runDistanceManagerUpdates
            this.waitUntilNextTick(); // 驱动短路链 mainThreadExecutor thenApplyAsync → 空壳 + fsa 读回
            ready = 0;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    ChunkAccess ca = cache.getChunk(spawnCx + dx, spawnCz + dz, ChunkStatus.FULL, false);
                    if (!(ca instanceof LevelChunk lc)) {
                        continue; // 未加载：ticket 驱动中，下轮
                    }
                    GenQueue.preload(lc, minSy, maxSy);
                    boolean allDone = true;
                    for (int sy = minSy; sy <= maxSy; sy++) {
                        if (FarLandsGenState.isOrAfter(lc, sy, FarLandsGenState.LIGHTED)) {
                            ready++;
                        } else {
                            allDone = false;
                        }
                    }
                    if (allDone) {
                        // 画面动画：该 chunk 7 section 全 LIGHTED → 网格该色块变白，固定 spawnChunk
                        // 只变中心一次 → 动画静止；chunk 级逐个变白 → 动画恢复；重复调无害——
                        // StoringChunkProgressListener 按 chunkPos map put
                        listener.onStatusChange(new ChunkPos(spawnCx + dx, spawnCz + dz), ChunkStatus.FULL);
                    }
                }
            }
            if (ready >= total) {
                break;
            }
            this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
            this.waitUntilNextTick();
        }
        LOGGER.info("Preload spawn 7x7x7 done: {}/{} {}", ready, total, ready < total ? "TIMEOUT" : "");
    }

    @Overwrite
    private void prepareLevels(ChunkProgressListener listener) {
        ServerLevel serverlevel = this.overworld();
        LOGGER.info("Preparing start region for dimension {}", serverlevel.dimension().location());
        BlockPos blockpos = serverlevel.getSharedSpawnPos();
        listener.updateSpawnPos(new ChunkPos(blockpos));
        this.nextTickTimeNanos = Util.getNanos();
        serverlevel.setDefaultSpawnPos(blockpos, serverlevel.getSharedSpawnAngle());
        for (ServerLevel serverlevel1 : this.levels.values()) {
            ForcedChunksSavedData forcedchunkssaveddata = serverlevel1.getDataStorage()
                    .get(ForcedChunksSavedData.factory(), "chunks");
            if (forcedchunkssaveddata != null) {
                LongIterator longiterator = forcedchunkssaveddata.getChunks().iterator();
                while (longiterator.hasNext()) {
                    long k = longiterator.nextLong();
                    ChunkPos chunkpos = new ChunkPos(k);
                    serverlevel1.getChunkSource().updateChunkForced(chunkpos, true);
                }
                ForcedChunkManager.reinstatePersistentChunks(serverlevel1, forcedchunkssaveddata);
            }
        }
        preloadSpawnArea(serverlevel, blockpos, listener);
        this.nextTickTimeNanos = Util.getNanos() + PREPARE_LEVELS_DEFAULT_DELAY_NANOS;
        this.waitUntilNextTick();
        listener.stop();
        this.updateMobSpawningFlags();
    }
}
