package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.inf.farlands.util.world.WorldBounds;

import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * MineShaftPiece.isInInvalidLocation 中点 int 溢出修复。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftPiece")
public abstract class MineshaftPieces$MineShaftPieceMixin {

    @Overwrite
    @SuppressWarnings("deprecation")
    protected boolean isInInvalidLocation(LevelAccessor level, BoundingBox chunkBB) {
        BoundingBox self = ((StructurePiece) (Object) this).getBoundingBox();

        if (!WorldBounds.inBlock((long) self.minX())
                || !WorldBounds.inBlock((long) self.maxX())
                || !WorldBounds.inBlock((long) self.minZ())
                || !WorldBounds.inBlock((long) self.maxZ())) {
            return true;
        }

        int b1 = Math.max(self.minX() - 1, chunkBB.minX());
        int b2 = Math.max(self.minY() - 1, chunkBB.minY());
        int b3 = Math.max(self.minZ() - 1, chunkBB.minZ());
        int b4 = Math.min(self.maxX() + 1, chunkBB.maxX());
        int b5 = Math.min(self.maxY() + 1, chunkBB.maxY());
        int b6 = Math.min(self.maxZ() + 1, chunkBB.maxZ());

        long midX = (long) b1 + (long) b4;
        long midZ = (long) b3 + (long) b6;
        if (!WorldBounds.inBlock(midX)
                || !WorldBounds.inBlock(midZ)
                || b1 > b4
                || b3 > b6) {
            return true;
        }

        MutableBlockPos blockPos = new MutableBlockPos(
                (b1 + b4) / 2,
                (b2 + b5) / 2,
                (b3 + b6) / 2);
        if (level.getBiome(blockPos).is(BiomeTags.MINESHAFT_BLOCKING)) {
            return true;
        }

        for (int x = b1; x <= b4; x++) {
            for (int z = b3; z <= b6; z++) {
                if (level.getBlockState(blockPos.set(x, b2, z)).liquid()
                        || level.getBlockState(blockPos.set(x, b5, z)).liquid()) {
                    return true;
                }
            }
        }
        for (int x = b1; x <= b4; x++) {
            for (int y = b2; y <= b5; y++) {
                if (level.getBlockState(blockPos.set(x, y, b3)).liquid()
                        || level.getBlockState(blockPos.set(x, y, b6)).liquid()) {
                    return true;
                }
            }
        }
        for (int z = b3; z <= b6; z++) {
            for (int y = b2; y <= b5; y++) {
                if (level.getBlockState(blockPos.set(b1, y, z)).liquid()
                        || level.getBlockState(blockPos.set(b4, y, z)).liquid()) {
                    return true;
                }
            }
        }
        return false;
    }
}
