package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(
    targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftPiece"
)
public class MineshaftPiecesMixin {

    private static final Field BB_FIELD;

    static {
        try {
            Class<?> sp = Class.forName(
                "net.minecraft.world.level.levelgen.structure.StructurePiece"
            );
            BB_FIELD = sp.getDeclaredField("boundingBox");
            BB_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Overwrite
    protected boolean isInInvalidLocation(LevelAccessor level, BoundingBox bb) {
        try {
            BoundingBox selfBB = (BoundingBox) BB_FIELD.get(this);
            int maxBlock = FarLandsConstants.MAX_BLOCK;
            if (
                Math.abs((long) selfBB.minX()) > maxBlock ||
                Math.abs((long) selfBB.maxX()) > maxBlock ||
                Math.abs((long) selfBB.minZ()) > maxBlock ||
                Math.abs((long) selfBB.maxZ()) > maxBlock
            ) {
                return true;
            }
            int i = Math.max(selfBB.minX() - 1, bb.minX());
            int j = Math.max(selfBB.minY() - 1, bb.minY());
            int k = Math.max(selfBB.minZ() - 1, bb.minZ());
            int l = Math.min(selfBB.maxX() + 1, bb.maxX());
            int i1 = Math.min(selfBB.maxY() + 1, bb.maxY());
            int j1 = Math.min(selfBB.maxZ() + 1, bb.maxZ());

            long midX = (long) i + (long) l;
            long midZ = (long) k + (long) j1;
            if (
                midX > Integer.MAX_VALUE ||
                midX < Integer.MIN_VALUE ||
                midZ > Integer.MAX_VALUE ||
                midZ < Integer.MIN_VALUE ||
                i > l ||
                k > j1
            ) {
                return true;
            }
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos(
                (i + l) / 2,
                (j + i1) / 2,
                (k + j1) / 2
            );

            try {
                if (level.getBiome(mpos).is(BiomeTags.MINESHAFT_BLOCKING)) {
                    return true;
                }
            } catch (Exception e) {
                return true;
            }

            for (int k1 = i; k1 <= l; k1++) for (int l1 = k; l1 <= j1; l1++) {
                if (level.getBlockState(mpos.set(k1, j, l1)).liquid()) return true;
                if (level.getBlockState(mpos.set(k1, i1, l1)).liquid()) return true;
            }
            for (int i2 = i; i2 <= l; i2++) for (int k2 = j; k2 <= i1; k2++) {
                if (level.getBlockState(mpos.set(i2, k2, k)).liquid()) return true;
                if (level.getBlockState(mpos.set(i2, k2, j1)).liquid()) return true;
            }
            for (int j2 = k; j2 <= j1; j2++) for (int l2 = j; l2 <= i1; l2++) {
                if (level.getBlockState(mpos.set(i, l2, j2)).liquid()) return true;
                if (level.getBlockState(mpos.set(l, l2, j2)).liquid()) return true;
            }
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
