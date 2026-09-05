package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

/**
 * AABB 三处 int 坐标 +1 溢出修复。
 */
@Mixin(AABB.class)
public abstract class AABBMixin {

    /** of(BoundingBox)：maxX/Y/Z + 1 为 int 溢出，long 化。 */
    @Inject(method = "of(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private static void safeOf(BoundingBox box, CallbackInfoReturnable<AABB> cir) {
        // 任一 max 坐标 ≥ MAX_BLOCK-1 才需 long 化
        if (box.maxX() < FarlandsConstant.MAX_BLOCK - 1 && box.maxY() < FarlandsConstant.MAX_BLOCK - 1
                && box.maxZ() < FarlandsConstant.MAX_BLOCK - 1) {
            return;
        }
        cir.setReturnValue(new AABB(
                (double) box.minX(),
                (double) box.minY(),
                (double) box.minZ(),
                (double) ((long) box.maxX() + 1L),
                (double) ((long) box.maxY() + 1L),
                (double) ((long) box.maxZ() + 1L)));
    }

    /** encapsulatingFullBlocks：同 of 的 int+1 溢出，long 化。 */
    @Inject(method = "encapsulatingFullBlocks(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/AABB;", at = @At("HEAD"), cancellable = true)
    private static void safeEncapsulatingFullBlocks(BlockPos startPos, BlockPos endPos,
            CallbackInfoReturnable<AABB> cir) {
        int maxX = Math.max(startPos.getX(), endPos.getX());
        int maxY = Math.max(startPos.getY(), endPos.getY());
        int maxZ = Math.max(startPos.getZ(), endPos.getZ());
        if (maxX < FarlandsConstant.MAX_BLOCK - 1 && maxY < FarlandsConstant.MAX_BLOCK - 1
                && maxZ < FarlandsConstant.MAX_BLOCK - 1) {
            return; // 正常坐标：放行原方法
        }
        cir.setReturnValue(new AABB(
                (double) Math.min(startPos.getX(), endPos.getX()),
                (double) Math.min(startPos.getY(), endPos.getY()),
                (double) Math.min(startPos.getZ(), endPos.getZ()),
                (double) ((long) maxX + 1L),
                (double) ((long) maxY + 1L),
                (double) ((long) maxZ + 1L)));
    }

    /**
     * AABB(BlockPos) 构造器：delegate this(DDDDDD) 的 max 侧参数在坐标 = MAX_VALUE
     * 时溢出为 MIN。还原 max = min + 1.0。
     */
    @ModifyArgs(method = "<init>(Lnet/minecraft/core/BlockPos;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;<init>(DDDDDD)V"))
    private static void fixOverflow(Args args) {
        double minX = args.<Double>get(0), minY = args.<Double>get(1), minZ = args.<Double>get(2);
        double maxX = args.<Double>get(3), maxY = args.<Double>get(4), maxZ = args.<Double>get(5);
        if (maxX < minX) {
            args.set(3, minX + 1.0); // maxX 溢出还原
        }
        if (maxY < minY) {
            args.set(4, minY + 1.0); // maxY
        }
        if (maxZ < minZ) {
            args.set(5, minZ + 1.0); // maxZ
        }
    }
}
