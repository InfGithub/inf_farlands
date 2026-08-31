package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.util.WorldBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 极端 XZ 的 ScatteredFeature 结构 postProcess 时
 * updateHeightPositionToLowestGroundHeight 循环溢出——STRUCTURE_STARTS 阶段
 * 结构 start 可在缓冲带 chunk 生成，createStructures 无 WorldBounds 拦截，
 * makeBoundingBox 创建的 boundingBox maxX 可达 int max → for (k = minX;
 * k <= maxX; k++) 的 k++ 溢出回绕 → 无限循环，worldgen 满核冻结，
 * fromCorners clamp 不覆盖 makeBoundingBox 创建的 box。
 *
 * 修复：循环边界 clamp 到 WorldBounds 可玩范围——循环有限不溢出；box 完全
 * 在缓冲带 → min > max → 循环 0 次 → flag false → 返回 false，postProcess
 * 跳过放置，结构不生成——缓冲带无地形，合理；正常坐标 clamp 恒等零变化。
 * 覆盖共用此方法的所有 ScatteredFeature 结构，包括沙漠神殿/丛林神庙/女巫小屋。
 */
@Mixin(ScatteredFeaturePiece.class)
public abstract class ScatteredFeaturePieceMixin {

    @Shadow
    protected int heightPosition;

    @SuppressWarnings("deprecation")
    @Overwrite
    protected boolean updateHeightPositionToLowestGroundHeight(LevelAccessor level, int height) {
        if (this.heightPosition >= 0) {
            return true;
        }
        int i = level.getMaxBuildHeight();
        boolean flag = false;
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        BoundingBox bb = ((StructurePiece) (Object) this).getBoundingBox();
        int minX = Math.max(bb.minX(), WorldBounds.MIN_PLAYABLE_BLOCK);
        int maxX = Math.min(bb.maxX(), WorldBounds.MAX_PLAYABLE_BLOCK);
        int minZ = Math.max(bb.minZ(), WorldBounds.MIN_PLAYABLE_BLOCK);
        int maxZ = Math.min(bb.maxZ(), WorldBounds.MAX_PLAYABLE_BLOCK);
        for (int j = minZ; j <= maxZ; j++) {
            for (int k = minX; k <= maxX; k++) {
                blockpos$mutableblockpos.set(k, 0, j);
                i = Math.min(i,
                        level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockpos$mutableblockpos).getY());
                flag = true;
            }
        }
        if (!flag) {
            return false;
        }
        this.heightPosition = i;
        bb.move(0, this.heightPosition - bb.minY() + height, 0);
        return true;
    }
}
