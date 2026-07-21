package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LevelReader.class)
public interface LevelReaderMixin {

    @Overwrite
    default int getMaxLocalRawBrightness(BlockPos pos, int amount) {
        int absX = Math.abs(pos.getX());
        int absZ = Math.abs(pos.getZ());
        if (absX <= FarLandsConstants.MAX_BLOCK && absZ <= FarLandsConstants.MAX_BLOCK) {
            return ((LevelReader) this).getRawBrightness(pos, amount);
        }
        return 15;
    }
}
