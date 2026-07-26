package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePieceAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces")
public class MineshaftPiecesMixin {

    @Inject(method = "generateAndAddPiece(Lnet/minecraft/world/level/levelgen/structure/StructurePiece;Lnet/minecraft/world/level/levelgen/structure/StructurePieceAccessor;Lnet/minecraft/util/RandomSource;IIILnet/minecraft/core/Direction;I)Lnet/minecraft/world/level/levelgen/structure/structures/MineshaftPieces$MineShaftPiece;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fixOverflow(
            StructurePiece piece,
            StructurePieceAccessor pieces,
            RandomSource random,
            int x, int y, int z,
            Direction direction,
            int genDepth,
            CallbackInfoReturnable<Object> cir) {
        if (genDepth > 8) {
            cir.setReturnValue(null);
            return;
        }

        long dx = (long) x - (long) piece.getBoundingBox().minX();
        long dz = (long) z - (long) piece.getBoundingBox().minZ();
        if (Math.abs(dx) > 80 || Math.abs(dz) > 80) {
            cir.setReturnValue(null);
        }
    }
}
