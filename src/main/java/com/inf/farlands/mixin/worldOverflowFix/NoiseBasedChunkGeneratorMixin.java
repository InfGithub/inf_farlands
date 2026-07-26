package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Constants;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.NaturalSpawner;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.GenerationStep;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

    @Shadow
    private Holder<NoiseGeneratorSettings> settings;

    @SuppressWarnings("deprecation")
    @Overwrite
    public void spawnOriginalMobs(WorldGenRegion level) {
        if (settings.value().disableMobGeneration()) {
            return;
        }

        ChunkPos chunkpos = level.getCenter();
        int cx = chunkpos.x;
        int cz = chunkpos.z;
        if (cx >= Constants.MAX_CHUNK ||
                cx < ~Constants.MAX_CHUNK ||
                cz >= Constants.MAX_CHUNK ||
                cz < ~Constants.MAX_CHUNK) {
            return;
        }

        ChunkAccess chunk = level.getChunk(cx, cz);
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        Holder<Biome> holder = null;
        for (int y = maxY - 1; y >= minY; y -= 16) {
            holder = chunk.getNoiseBiome(
                    QuartPos.fromBlock(8),
                    QuartPos.fromBlock(y),
                    QuartPos.fromBlock(8));
            if (holder != null) {
                break;
            }
        }
        if (holder == null) {
            return;
        }

        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(level.getSeed(), chunkpos.getMinBlockX(), chunkpos.getMinBlockZ());
        NaturalSpawner.spawnMobsForChunkGeneration(level, holder, chunkpos, random);
    }

    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void skipApplyCarvers(
            WorldGenRegion level,
            long seed,
            RandomState random,
            BiomeManager biomeManager,
            StructureManager structureManager,
            ChunkAccess chunk,
            GenerationStep.Carving step,
            CallbackInfo ci) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        if (cx >= Constants.MAX_CHUNK || cx < ~Constants.MAX_CHUNK ||
                cz >= Constants.MAX_CHUNK || cz < ~Constants.MAX_CHUNK) {
            ci.cancel();
        }
    }
}
