package com.inf.farlands.mixin;

import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Fixes {@code VoxelShape.collideX} at high coordinates by replacing
 * the hard-coded {@code 1.0E-7} epsilon with a value that is at least
 * one ULP of the coordinate being adjusted.
 * <p>
 * At 2.14B the double ULP is ~4.77e-7, larger than 1.0E-7, so the
 * original epsilon is completely rounded away — causing voxel-slice
 * indices to shift and collision results to flip between frames
 * (the "wall jitter" bug).
 */
@Mixin(VoxelShape.class)
public abstract class VoxelShapeMixin {

    private static final double EPSILON = 1.0E-7;

    @Unique
    private static double fixedAdd(double value) {
        double base = value - EPSILON;
        return base + Math.max(EPSILON, Math.ulp(base));
    }

    @Unique
    private static double fixedSub(double value) {
        double base = value + EPSILON;
        return base - Math.max(EPSILON, Math.ulp(base));
    }

    // L250: d1 + 1.0E-7
    @ModifyArg(
        method = "collideX",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;findIndex(Lnet/minecraft/core/Direction$Axis;D)I",
            ordinal = 0
        ),
        index = 1
    )
    private double fixEpsilon0(double value) { return fixedAdd(value); }

    // L251: d0 - 1.0E-7
    @ModifyArg(
        method = "collideX",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;findIndex(Lnet/minecraft/core/Direction$Axis;D)I",
            ordinal = 1
        ),
        index = 1
    )
    private double fixEpsilon1(double value) { return fixedSub(value); }

    // L252: collisionBox.min(axis1) + 1.0E-7
    @ModifyArg(
        method = "collideX",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;findIndex(Lnet/minecraft/core/Direction$Axis;D)I",
            ordinal = 2
        ),
        index = 1
    )
    private double fixEpsilon2(double value) { return fixedAdd(value); }

    // L253: collisionBox.max(axis1) - 1.0E-7
    @ModifyArg(
        method = "collideX",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;findIndex(Lnet/minecraft/core/Direction$Axis;D)I",
            ordinal = 3
        ),
        index = 1
    )
    private double fixEpsilon3(double value) { return fixedSub(value); }

    // L254: collisionBox.min(axis2) + 1.0E-7
    @ModifyArg(
        method = "collideX",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;findIndex(Lnet/minecraft/core/Direction$Axis;D)I",
            ordinal = 4
        ),
        index = 1
    )
    private double fixEpsilon4(double value) { return fixedAdd(value); }

    // L255: collisionBox.max(axis2) - 1.0E-7
    @ModifyArg(
        method = "collideX",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/phys/shapes/VoxelShape;findIndex(Lnet/minecraft/core/Direction$Axis;D)I",
            ordinal = 5
        ),
        index = 1
    )
    private double fixEpsilon5(double value) { return fixedSub(value); }
}
