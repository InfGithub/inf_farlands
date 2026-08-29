package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

@Mixin(BlockPos.class)
public class BlockPosMixin {
    @Shadow
    @Final
    @Mutable
    private static int PACKED_HORIZONTAL_LENGTH;

    private static void setPackedHorizontalLength(int value) {
        PACKED_HORIZONTAL_LENGTH = value;
    }

    private static final int exceptPackedHorizontalLength = 1 + Mth.log2(
            Mth.smallestEncompassingPowerOfTwo(FarlandsConstant.MAX_BLOCK));

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void OnClassInit() {
        setPackedHorizontalLength(exceptPackedHorizontalLength);
    }
}