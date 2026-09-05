package com.inf.farlands.client.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * RenderSectionRegion 编译取数越界防御。
 */
@Mixin(RenderSectionRegion.class)
public abstract class RenderSectionRegionMixin {

    @Shadow
    private int minSectionX;

    @Shadow
    private int minSectionY;

    @Shadow
    private int minSectionZ;

    @Unique
    private boolean sectionInWindow(int sx, int sy, int sz) {
        return sx >= this.minSectionX && sx <= this.minSectionX + 2
                && sy >= this.minSectionY && sy <= this.minSectionY + 2
                && sz >= this.minSectionZ && sz <= this.minSectionZ + 2;
    }

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("HEAD"), cancellable = true)
    private void guardGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (!sectionInWindow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;", at = @At("HEAD"), cancellable = true)
    private void guardGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (!sectionInWindow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;", at = @At("HEAD"), cancellable = true)
    private void guardGetBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (!sectionInWindow(SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(null);
        }
    }
}
