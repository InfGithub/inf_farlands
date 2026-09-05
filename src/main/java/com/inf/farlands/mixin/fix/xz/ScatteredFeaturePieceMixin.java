package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Overwrite;

import com.inf.farlands.util.world.WorldBounds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * 极端 XZ 的 ScatteredFeature 结构高度定位循环溢出修复。
 */
@Mixin(ScatteredFeaturePiece.class)
public abstract class ScatteredFeaturePieceMixin {

    @Shadow
    protected int heightPosition;

    @SuppressWarnings("deprecation")
    @Overwrite
    protected boolean updateHeightPositionToLowestGroundHeight(final LevelAccessor level, final int offset) {
        if (this.heightPosition >= 0) {
            return true;
        }
        int lowestGroundHeight = level.getMaxY() + 1;
        boolean foundPositionWithinBoundingBox = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BoundingBox bb = ((StructurePiece) (Object) this).getBoundingBox();
        int minZ = Math.max(bb.minZ(), WorldBounds.MIN_PLAYABLE_BLOCK);
        int maxZ = Math.min(bb.maxZ(), WorldBounds.MAX_PLAYABLE_BLOCK);
        int minX = Math.max(bb.minX(), WorldBounds.MIN_PLAYABLE_BLOCK);
        int maxX = Math.min(bb.maxX(), WorldBounds.MAX_PLAYABLE_BLOCK);
        for (int z = minZ; z <= maxZ; z++) {
            for (int x = minX; x <= maxX; x++) {
                pos.set(x, 0, z);
                lowestGroundHeight = Math.min(lowestGroundHeight,
                        level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY());
                foundPositionWithinBoundingBox = true;
            }
        }
        if (!foundPositionWithinBoundingBox) {
            return false;
        }
        this.heightPosition = lowestGroundHeight;
        bb.move(0, this.heightPosition - bb.minY() + offset, 0);
        return true;
    }
}
