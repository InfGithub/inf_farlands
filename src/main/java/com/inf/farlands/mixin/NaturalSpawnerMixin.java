package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(NaturalSpawner.class)
public class NaturalSpawnerMixin {

    @Overwrite
    static Biome getRoughBiome(BlockPos pos, ChunkAccess chunk) {
        // Route through Level's getNoiseBiome to hit LevelReaderMixin override
        if (chunk instanceof net.minecraft.world.level.chunk.LevelChunk lc) {
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
