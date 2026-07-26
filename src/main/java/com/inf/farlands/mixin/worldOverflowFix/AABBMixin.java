package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(AABB.class)
public class AABBMixin {

    @Overwrite
    public static AABB of(BoundingBox mutableBox) {
        return new AABB(
                (double) mutableBox.minX(),
                (double) mutableBox.minY(),
                (double) mutableBox.minZ(),
                (double) ((long) mutableBox.maxX() + 1L),
                (double) ((long) mutableBox.maxY() + 1L),
                (double) ((long) mutableBox.maxZ() + 1L));
    }
}
