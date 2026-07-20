package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LevelReader.class)
public interface LevelReaderMixin {

    @Shadow
    Holder<Biome> getUncachedNoiseBiome(int x, int y, int z);

    @Overwrite
    default int getMaxLocalRawBrightness(BlockPos pos, int amount) {
        int absX = Math.abs(pos.getX());
        int absZ = Math.abs(pos.getZ());
        if (absX <= FarLandsConstants.MAX_BLOCK && absZ <= FarLandsConstants.MAX_BLOCK) {
            return ((LevelReader) this).getRawBrightness(pos, amount);
        }
        return 15;
    }

    @Overwrite
    default Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return ((LevelReader) this).getUncachedNoiseBiome(x, y, z);
    }
}
