package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.WorldBounds;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftPiece")
public class MineshaftPieces$MineShaftPieceMixin {

    private static final Field BB_FIELD;

    static {
        try {
            Class<?> sp = Class.forName(
                    "net.minecraft.world.level.levelgen.structure.StructurePiece");
            BB_FIELD = sp.getDeclaredField("boundingBox");
            BB_FIELD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("null")
    private static boolean getBiomeIsMineShaftBlocking(LevelAccessor level, MutableBlockPos pos) {
        try {
            return level.getBiome(pos).is(BiomeTags.MINESHAFT_BLOCKING);
        } catch (Exception e) {
            return true;
        }
    }

    private static BoundingBox getBoundingBoxField(Object obj) {
        try {
            return (BoundingBox) BB_FIELD.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({ "deprecation", "null" })
    @Overwrite
    protected boolean isInInvalidLocation(LevelAccessor level, BoundingBox bb) {
        BoundingBox self = getBoundingBoxField(this);

        if (!WorldBounds.inBlock((long) self.minX()) ||
                !WorldBounds.inBlock((long) self.maxX()) ||
                !WorldBounds.inBlock((long) self.minZ()) ||
                !WorldBounds.inBlock((long) self.maxZ())) {
            return true;
        }

        int b1 = Math.max(self.minX() - 1, bb.minX());
        int b2 = Math.max(self.minY() - 1, bb.minY());
        int b3 = Math.max(self.minZ() - 1, bb.minZ());
        int b4 = Math.min(self.maxX() + 1, bb.maxX());
        int b5 = Math.min(self.maxY() + 1, bb.maxY());
        int b6 = Math.min(self.maxZ() + 1, bb.maxZ());

        long midX = (long) b1 + (long) b4;
        long midZ = (long) b3 + (long) b6;
        if (!WorldBounds.inBlock(midX) ||
                !WorldBounds.inBlock(midZ) ||
                b1 > b4 ||
                b3 > b6) {
            return true;
        }

        MutableBlockPos pos = new BlockPos.MutableBlockPos(
                (b1 + b4) / 2,
                (b2 + b5) / 2,
                (b3 + b6) / 2);
        if (getBiomeIsMineShaftBlocking(level, pos)) {
            return true;
        }

        for (int i = b1; i <= b4; i++)
            for (int j = b3; j <= b6; j++) {
                if (level.getBlockState(pos.set(i, b2, j)).liquid()
                        || level.getBlockState(pos.set(i, b5, j)).liquid()) {
                    return true;
                }
            }
        for (int i = b1; i <= b4; i++)
            for (int j = b2; j <= b5; j++) {
                if (level.getBlockState(pos.set(i, j, b3)).liquid()
                        || level.getBlockState(pos.set(i, j, b6)).liquid()) {
                    return true;
                }
            }
        for (int i = b3; i <= b6; i++)
            for (int j = b2; j <= b5; j++) {
                if (level.getBlockState(pos.set(b1, j, i)).liquid()
                        || level.getBlockState(pos.set(b4, j, i)).liquid()) {
                    return true;
                }
            }
        return false;
    }
}
