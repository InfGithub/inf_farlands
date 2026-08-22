package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biome;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

    @Overwrite
    static Biome getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        if (chunk instanceof LevelChunk lc) {
            return lc.getLevel().getNoiseBiome(
                    QuartPos.fromBlock(pos.getX()),
                    QuartPos.fromBlock(pos.getY()),
                    QuartPos.fromBlock(pos.getZ())).value();
        }
        return chunk.getNoiseBiome(
                QuartPos.fromBlock(pos.getX()),
                QuartPos.fromBlock(pos.getY()),
                QuartPos.fromBlock(pos.getZ())).value();
    }

    @SuppressWarnings("null")
    @Overwrite
    private static BlockPos getRandomPosWithin(Level level, LevelChunk chunk) {
        ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX() + level.random.nextInt(16);
        int j = chunkpos.getMinBlockZ() + level.random.nextInt(16);
        int k = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, j) + 1;
        int minY = level.getMinBuildHeight();
        if (k < minY)
            k = minY;
        int maxSafeK = minY + Integer.MAX_VALUE - 1;
        if (k > maxSafeK)
            k = maxSafeK;
        int l = Mth.randomBetweenInclusive(level.random, minY, k);
        return new BlockPos(i, l, j);
    }
}
