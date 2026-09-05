package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.inf.farlands.util.world.WorldBounds;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * 缓冲带/超 chunk 域 chunk 跳过 applyCarvers 与 spawnOriginalMobs。
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    @Inject(method = "applyCarvers", at = @At("HEAD"), cancellable = true)
    private void skipApplyCarvers(WorldGenRegion level, long seed, RandomState randomState,
            BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, CallbackInfo ci) {
        int cx = chunk.getPos().x();
        int cz = chunk.getPos().z();
        if (!WorldBounds.inChunkRange(cx, cz)) {
            ci.cancel();
        }
    }

    @Inject(method = "spawnOriginalMobs", at = @At("HEAD"), cancellable = true)
    private void skipSpawnOriginalMobs(WorldGenRegion level, CallbackInfo ci) {
        int cx = level.getCenter().x();
        int cz = level.getCenter().z();
        if (!WorldBounds.inChunkRange(cx, cz)) {
            ci.cancel();
        }
    }
}
