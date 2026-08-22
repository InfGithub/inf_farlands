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
 * 极端 X/Z 的 Mineshaft 生成死循环（worldgen 线程冻结）：
 * addChildren 的 `for (i1 = minX + 3; i1 + 3 <= maxX; i1 += 5)`（L324/L337）在
 * minX ≥ 2147483645 时 `minX + 3` int 溢出成负数 → i1 从负数循环到正数 maxX →
 * ~8.6 亿次迭代（jstack 实锤：worldgen 线程 CPU 持续增长、PC 固定 L324）。
 * 修复：boundingBox 正方向越界（> MAX_BLOCK - 100，爆炸点在 MAX_BLOCK - 2）
 * → 该 piece 不再延伸（cancel）。负方向 `+ 3` 数学安全无需防御；正常坐标
 * （±3000 万）远低于拦截线，零误伤。place 阶段（postProcess）已有
 * isInInvalidLocation 拦截越界 box，无盲区；Crossing/Room/Stairs 无此循环
 * （只经 generateAndAddPiece，已有 long 距离检查拦截）。
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
