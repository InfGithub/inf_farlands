package com.inf.farlands.mixin.worldBorder;

import com.inf.farlands.Config;
import com.inf.farlands.WorldBounds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LevelReader.class)
public interface LevelReaderMixin {

    @SuppressWarnings("deprecation")
    @Overwrite
    default boolean hasChunksAt(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        return WorldBounds.inBuildHeightRange(toY, fromY)
                ? ((LevelReader) this).hasChunksAt(fromX, fromZ, toX, toZ)
                : false;
    }

    @Overwrite
    default int getMaxLocalRawBrightness(BlockPos pos, int amount) {
        int max = Config.borderAbsoluteMax;
        return pos.getX() >= ~max && pos.getZ() >= ~max
                && pos.getX() < max && pos.getZ() < max
                        ? ((LevelReader) this).getRawBrightness(pos, amount)
                        : 15;
    }
}
