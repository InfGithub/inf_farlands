package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;

/**
 * 极端 X/Z 的 Mineshaft 生成死循环，worldgen 线程冻结。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftCorridor")
public abstract class MineshaftPieces$MineShaftCorridorMixin {

    @Inject(method = "addChildren", at = @At("HEAD"), cancellable = true)
    private void skipOverflowingBox(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random,
            CallbackInfo ci) {
        BoundingBox bb = ((StructurePiece) (Object) this).getBoundingBox();
        if (bb.minX() > FarlandsConstant.MAX_BLOCK - 100 || bb.maxX() > FarlandsConstant.MAX_BLOCK - 100
                || bb.minZ() > FarlandsConstant.MAX_BLOCK - 100 || bb.maxZ() > FarlandsConstant.MAX_BLOCK - 100) {
            ci.cancel();
        }
    }
}
