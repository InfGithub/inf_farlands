package com.inf.farlands.mixin;

import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(TreeFeature.class)
public class TreeFeatureMixin {

    @Redirect(
        method = "updateLeaves",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;fill(III)V"
        )
    )
    private static void safeFill(
        DiscreteVoxelShape shape,
        int x,
        int y,
        int z
    ) {
        if (x >= 0 && y >= 0 && z >= 0) {
            shape.fill(x, y, z);
        }
    }

    @Redirect(
        method = "updateLeaves",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/DiscreteVoxelShape;isFull(III)Z"
        )
    )
    private static boolean safeIsFull(
        DiscreteVoxelShape shape,
        int x,
        int y,
        int z
    ) {
        if (x >= 0 && y >= 0 && z >= 0) {
            return shape.isFull(x, y, z);
        }
        return false;
    }
}
