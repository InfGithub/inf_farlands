package com.inf.farlands.mixin.worldOverflowFix;

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
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.world.level.ChunkPos;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.lang.reflect.Method;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {

    @Shadow
    @Final
    private ChunkAccess center;

    @Shadow
    private ServerLevel level;

    @Shadow
    @Final
    private StaticCache2D<?> cache;

    @Shadow
    @Final
    private ChunkStep generatingStep;

    private static final Method DIFF_METHOD;
    private static final Method DAYTIME_METHOD;
    private static final Method MOON_METHOD;

    static {
        try {
            DIFF_METHOD = ServerLevel.class.getMethod("getDifficulty");
            DAYTIME_METHOD = ServerLevel.class.getMethod("getDayTime");
            MOON_METHOD = ServerLevel.class.getMethod("getMoonBrightness");
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private Object getHolder(int x, int z) {
        try {
            return this.cache.get(x, z);
        } catch (Exception e) {
            return ((ServerChunkCache) this.level.getChunkSource()).chunkMap
                    .getVisibleChunkIfPresent(ChunkPos.asLong(x, z));
        }
    }

    @SuppressWarnings("deprecation")
    private Difficulty getDiff() {
        try {
            return (Difficulty) DIFF_METHOD.invoke(((WorldGenRegion) (Object) this).getLevel());
        } catch (Exception e) {
            return Difficulty.NORMAL;
        }
    }

    @SuppressWarnings("deprecation")
    private long getTime() {
        try {
            return (long) DAYTIME_METHOD.invoke(((WorldGenRegion) (Object) this).getLevel());
        } catch (Exception e) {
            return 0L;
        }
    }

    @SuppressWarnings("deprecation")
    private float getMoon() {
        try {
            return (float) MOON_METHOD.invoke(((WorldGenRegion) (Object) this).getLevel());
        } catch (Exception e) {
            return 0F;
        }
    }

    @Overwrite
    public ChunkAccess getChunk(
            int x,
            int z,
            ChunkStatus chunkStatus,
            boolean requireChunk) {
        int i = this.center.getPos().getChessboardDistance(x, z);
        ChunkStatus chunkstatus = i >= this.generatingStep.directDependencies().size()
                ? null
                : this.generatingStep.directDependencies().get(i);

        if (chunkstatus == null) {
            return this.center;
        }

        Object holder = getHolder(x, z);

        if (holder != null && chunkStatus.isOrBefore(chunkstatus)) {
            ChunkAccess ca = ((GenerationChunkHolder) holder).getChunkIfPresentUnchecked(chunkstatus);
            if (ca != null) {
                return ca;
            }
        }

        return this.center;
    }

    @SuppressWarnings("null")
    @Overwrite
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return new DifficultyInstance(
                getDiff(),
                getTime(),
                0L,
                getMoon());
    }
}
