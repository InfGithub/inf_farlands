package com.inf.farlands.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {

    @Shadow @Final private ChunkAccess center;
    @Shadow private ServerLevel level;
    @Shadow @Final private StaticCache2D<?> cache;
    @Shadow @Final private ChunkStep generatingStep;

    private static final java.lang.reflect.Method DIFF_METHOD;
    private static final java.lang.reflect.Method DAYTIME_METHOD;
    private static final java.lang.reflect.Method MOON_METHOD;

    static {
        try {
            DIFF_METHOD = ServerLevel.class.getMethod("getDifficulty");
            DAYTIME_METHOD = ServerLevel.class.getMethod("getDayTime");
            MOON_METHOD = ServerLevel.class.getMethod("getMoonBrightness");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Overwrite
    public ChunkAccess getChunk(int x, int z, ChunkStatus chunkStatus, boolean requireChunk) {
        int i = this.center.getPos().getChessboardDistance(x, z);
        ChunkStatus chunkstatus = i >= this.generatingStep.directDependencies().size()
            ? null : this.generatingStep.directDependencies().get(i);

        if (chunkstatus == null) {
            return this.center;
        }

        Object holder;
        try {
            holder = this.cache.get(x, z);
        } catch (IllegalArgumentException e) {
            holder = ((ServerChunkCache) this.level.getChunkSource())
                .chunkMap.getVisibleChunkIfPresent(net.minecraft.world.level.ChunkPos.asLong(x, z));
        }

        if (holder != null && chunkStatus.isOrBefore(chunkstatus)) {
            ChunkAccess ca = ((net.minecraft.server.level.GenerationChunkHolder) holder)
                .getChunkIfPresentUnchecked(chunkstatus);
            if (ca != null) return ca;
        }

        return this.center;
    }

    @Overwrite
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        try {
            Difficulty diff = (Difficulty) DIFF_METHOD.invoke(
                ((WorldGenRegion)(Object)this).getLevel());
            long time = (long) DAYTIME_METHOD.invoke(
                ((WorldGenRegion)(Object)this).getLevel());
            float moon = (float) MOON_METHOD.invoke(
                ((WorldGenRegion)(Object)this).getLevel());
            return new DifficultyInstance(diff, time, 0L, moon);
        } catch (Exception e) {
            return new DifficultyInstance(Difficulty.NORMAL, 0L, 0L, 0F);
        }
    }
}
