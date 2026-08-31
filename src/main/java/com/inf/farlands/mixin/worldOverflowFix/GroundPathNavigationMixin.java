package com.inf.farlands.mixin.worldOverflowFix;

import com.google.common.collect.ImmutableSet;

import com.inf.farlands.Config;

import java.lang.reflect.Field;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.pathfinder.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * 地面实体寻路位置目标 createPath(BlockPos) 的目标规范化扫描。
 *
 * vanilla 用 overworld 维度高度范围 -64/320 作为空气目标的向下/向上扫描
 * 边界——本 mod 允许方块存在于 ±2.14B 任意 Y，极端 Y 空气目标向下扫 2.14B
 * 格到 -64 找不到支撑方块 → 目标被改写为 -63 → 寻路到地底/失败。
 *
 * 修复：扫描边界 = Config.worldGenMinY/MaxY 与目标 Y ±maxCapIter 的交集。
 *
 * priority = 100：@Overwrite 先应用，PathNavigationMixin 默认 1000 的
 * @Inject HEAD 后注入到新方法体，XZ 超界时返回 null。
 */
@Mixin(value = GroundPathNavigation.class, priority = 100)
public abstract class GroundPathNavigationMixin {

    /** 扫描上限：maxFallDistance=3 意味着实际可达目标的支撑方块必在 3 格内 */
    private static final int SCAN_LIMIT = Config.maxCapIter;

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
        return (int) Math.max((long) Config.worldGenMinY, (long) targetY - SCAN_LIMIT);
    }

    @Unique
    private int scanCeil(int targetY) {
        return (int) Math.min((long) Config.worldGenMaxY, (long) targetY + SCAN_LIMIT);
    }

    @SuppressWarnings({ "deprecation", "null" })
    @Overwrite
    public Path createPath(BlockPos pos, int accuracy) {
        Level level = refLevel();
        LevelChunk levelchunk = level
                .getChunkSource()
                .getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        if (levelchunk == null) {
            return null;
        } else {
            BlockPos originalPos = pos;
            if (levelchunk.getBlockState(pos).isAir()) {
                BlockPos blockpos = pos.below();
                int floorLimit = scanFloor(pos.getY());

                while (blockpos.getY() > floorLimit && levelchunk.getBlockState(blockpos).isAir()) {
                    blockpos = blockpos.below();
                }

                if (blockpos.getY() > floorLimit) {
                    return ((PathNavigation) (Object) this).createPath(ImmutableSet.of(blockpos.above()), accuracy);
                }

                int ceilLimit = scanCeil(pos.getY());
                while (blockpos.getY() < ceilLimit && levelchunk.getBlockState(blockpos).isAir()) {
                    blockpos = blockpos.above();
                }

                // 扫描区间内完全无方块 → 目标原样交给 A*
                if (blockpos.getY() >= ceilLimit && levelchunk.getBlockState(blockpos).isAir()) {
                    pos = originalPos;
                } else {
                    pos = blockpos;
                }
            }

            if (!levelchunk.getBlockState(pos).isSolid()) {
                return ((PathNavigation) (Object) this).createPath(ImmutableSet.of(pos), accuracy);
            } else {
                BlockPos blockpos1 = pos.above();
                int ceilLimit = scanCeil(pos.getY());

                while (blockpos1.getY() < ceilLimit && levelchunk.getBlockState(blockpos1).isSolid()) {
                    blockpos1 = blockpos1.above();
                }

                return ((PathNavigation) (Object) this).createPath(ImmutableSet.of(blockpos1), accuracy);
            }
        }
    }
}
