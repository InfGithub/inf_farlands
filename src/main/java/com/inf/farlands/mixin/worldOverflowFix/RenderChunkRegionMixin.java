package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.util.WorldBounds;

import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.world.AuxiliaryLightManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 防御编译取数时的溢出/越界 chunk 坐标。
 *
 * 世界边界允许方块铺到 z = Integer.MAX_VALUE = 2147483647。环境光遮蔽 AO
 * 编译时取该方块邻居 z+1 → int 溢出为 -2147483648 → chunk 坐标 -134217728
 * 之外的垃圾值 → RenderChunkRegion.index 越界 → CTD。
 *
 * chunk 坐标的合法范围 = 可玩范围 [-MAX_CHUNK, MAX_CHUNK-1]；缓冲 chunk =
 * MAX_CHUNK 与 ~MAX_CHUNK，覆盖 int 极值方块，不可用于正常逻辑。
 * 越界含溢出坐标 → 返回空气/空值，语义与 vanilla 世界边界外一致：
 * getAuxLightManager 返回 null 镜像 IBlockGetterExtension 默认实现，
 * chunk 不存在时为 null。
 */
@Mixin(RenderChunkRegion.class)
public abstract class RenderChunkRegionMixin {

    @Unique
    private boolean outOfChunkRange(int x, int z) {
        // 缓冲 chunk 覆盖 int 极值方块，不可用
        return !WorldBounds.inChunkRange(x, z);
    }

    @Inject(method = "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"), cancellable = true)
    private void guardGetBlockState(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        if (this.outOfChunkRange(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(Blocks.AIR.defaultBlockState());
        }
    }

    @Inject(method = "getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;",
            at = @At("HEAD"), cancellable = true)
    private void guardGetFluidState(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
        if (this.outOfChunkRange(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(Fluids.EMPTY.defaultFluidState());
        }
    }

    @Inject(method = "getBlockEntity(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
            at = @At("HEAD"), cancellable = true)
    private void guardGetBlockEntity(BlockPos pos, CallbackInfoReturnable<BlockEntity> cir) {
        if (this.outOfChunkRange(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getAuxLightManager(Lnet/minecraft/world/level/ChunkPos;)Lnet/neoforged/neoforge/common/world/AuxiliaryLightManager;",
            at = @At("HEAD"), cancellable = true)
    private void guardGetAuxLightManager(ChunkPos pos, CallbackInfoReturnable<AuxiliaryLightManager> cir) {
        if (this.outOfChunkRange(pos.x, pos.z)) {
            cir.setReturnValue(null);
        }
    }
}
