package com.inf.farlands.mixin.expand.xz;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.BlockPos;

/**
 * 本 mixin 的实现方式存在严重问题，
 * 这会导致所有使用 BlockPos.asLong 的地方 CTD，
 * 且后续本 mixin 可能会被 IntBlockPos 取代。
 * 目前，本 mixin 已从 mixins.json 中移除。
 */
@Mixin(BlockPos.class)
public class BlockPosMixin {
    @Shadow
    @Final
    @Mutable
    private static int PACKED_HORIZONTAL_LENGTH;

    private static void setPackedHorizontalLength(int value) {
        PACKED_HORIZONTAL_LENGTH = value;
    }

    // java 无法存储 -2147483648
    private static final int exceptPackedHorizontalLength = 32;

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void OnClassInit(CallbackInfo ci) {
        setPackedHorizontalLength(exceptPackedHorizontalLength);
    }
}