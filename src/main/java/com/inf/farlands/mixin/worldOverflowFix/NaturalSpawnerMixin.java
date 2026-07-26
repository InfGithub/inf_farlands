package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
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
}
