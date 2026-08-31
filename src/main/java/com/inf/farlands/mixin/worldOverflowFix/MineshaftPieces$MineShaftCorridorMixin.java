package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Constants;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 极端 X/Z 的 Mineshaft 生成死循环，worldgen 线程冻结。
 *
 * addChildren 的 for (i1 = minX + 3; i1 + 3 <= maxX; i1 += 5) 在 minX ≥ 2147483645
 * 时 minX + 3 int 溢出成负数 → i1 从负数循环到正数 maxX → ~8.6 亿次迭代。
 * 修复：boundingBox 正方向超过 MAX_BLOCK - 100 即越界，爆炸点在 MAX_BLOCK - 2，
 * 该 piece 不再延伸。负方向 + 3 数学安全无需防御；正常坐标 ±3000 万远低于
 * 拦截线，零误伤。
 */
@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftCorridor")
public abstract class MineshaftPieces$MineShaftCorridorMixin {

    @Inject(method = "addChildren", at = @At("HEAD"), cancellable = true)
    private void skipOverflowingBox(StructurePiece piece, StructurePieceAccessor pieces, RandomSource random,
            CallbackInfo ci) {
        BoundingBox bb = ((StructurePiece) (Object) this).getBoundingBox();
        if (bb.minX() > Constants.MAX_BLOCK - 100 || bb.maxX() > Constants.MAX_BLOCK - 100
                || bb.minZ() > Constants.MAX_BLOCK - 100 || bb.maxZ() > Constants.MAX_BLOCK - 100) {
            ci.cancel();
        }
    }
}
