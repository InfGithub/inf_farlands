package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Field;

import com.inf.farlands.FarlandsConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 地面实体寻路目标位置扫描修复。
 */
@Mixin(GroundPathNavigation.class)
public abstract class GroundPathNavigationMixin {

    @Unique
    private static final Field F_LEVEL;

    static {
        try {
            F_LEVEL = PathNavigation.class.getDeclaredField("level");
            F_LEVEL.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private Level refLevel() {
        try {
            return (Level) F_LEVEL.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private int scanFloor(int targetY) {
        return (int) Math.max((long) FarlandsConfig.worldGenMinY,
                (long) targetY - FarlandsConfig.maxCapIter);
    }

    @Unique
    private int scanCeil(int targetY) {
        return (int) Math.min((long) FarlandsConfig.worldGenMaxY,
                (long) targetY + FarlandsConfig.maxCapIter);
    }

    @SuppressWarnings("deprecation")
    @Overwrite
    final BlockPos findSurfacePosition(final LevelChunk chunk, BlockPos pos, final int reachRange) {
        if (chunk.getBlockState(pos).isAir()) {
            BlockPos.MutableBlockPos columnPos = pos.mutable().move(Direction.DOWN);
            int floorLimit = scanFloor(pos.getY());

            while (columnPos.getY() >= floorLimit && chunk.getBlockState(columnPos).isAir()) {
                columnPos.move(Direction.DOWN);
            }

            if (columnPos.getY() >= floorLimit) {
                return columnPos.above();
            }

            columnPos.setY(pos.getY() + 1);
            int ceilLimit = scanCeil(pos.getY());

            while (columnPos.getY() <= ceilLimit && chunk.getBlockState(columnPos).isAir()) {
                columnPos.move(Direction.UP);
            }

            pos = columnPos;
        }

        if (!chunk.getBlockState(pos).isSolid()) {
            return pos;
        }

        BlockPos.MutableBlockPos columnPos = pos.mutable().move(Direction.UP);
        int ceilLimit = scanCeil(pos.getY());

        while (columnPos.getY() <= ceilLimit && chunk.getBlockState(columnPos).isSolid()) {
            columnPos.move(Direction.UP);
        }

        return columnPos.immutable();
    }
}
