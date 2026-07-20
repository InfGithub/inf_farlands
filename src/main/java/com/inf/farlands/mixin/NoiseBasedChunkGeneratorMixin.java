package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

    @Shadow
    private net.minecraft.core.Holder<net.minecraft.world.level.levelgen.NoiseGeneratorSettings> settings;

    @Overwrite
    public void spawnOriginalMobs(WorldGenRegion level) {
        if (settings.value().disableMobGeneration()) return;

        ChunkPos chunkpos = level.getCenter();
        int cx = chunkpos.x;
        int cz = chunkpos.z;
        int max = FarLandsConstants.MAX_CHUNK;
        if (cx > max || cx < -max || cz > max || cz < -max) return;

        ChunkAccess chunk = level.getChunk(cx, cz);
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        Holder<Biome> holder = null;
        for (int y = maxY - 1; y >= minY; y -= 16) {
            holder = chunk.getNoiseBiome(
                net.minecraft.core.QuartPos.fromBlock(8),
                net.minecraft.core.QuartPos.fromBlock(y),
                net.minecraft.core.QuartPos.fromBlock(8));
            if (holder != null) break;
        }
        if (holder == null) return;

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), chunkpos.getMinBlockX(), chunkpos.getMinBlockZ());
        net.minecraft.world.level.NaturalSpawner.spawnMobsForChunkGeneration(level, holder, chunkpos, random);
    }
}
