package com.inf.farlands.mixin.axisYOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(TheEndGatewayBlockEntity.class)
public class TheEndGatewayBlockEntityMixin {

    @SuppressWarnings("null")
    @Overwrite
    private static BlockPos findTallestBlock(BlockGetter level, BlockPos pos, int radius, boolean allowBedrock) {
        BlockPos blockpos = null;
        int absoluteMinY = level.getMinBuildHeight();
        int maxY = Math.min(level.getMaxBuildHeight() - 1, absoluteMinY + Config.maxCapIter);

        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if (i != 0 || j != 0 || allowBedrock) {
                    continue;
                }
                int minY = blockpos == null ? absoluteMinY : Math.max(blockpos.getY(), absoluteMinY);
                for (int k = maxY; k > minY; k--) {
                    BlockPos bp = new BlockPos(pos.getX() + i, k, pos.getZ() + j);
                    BlockState bs = level.getBlockState(bp);
                    if (bs.isCollisionShapeFullBlock(level, bp) && (allowBedrock || !bs.is(Blocks.BEDROCK))) {
                        blockpos = bp;
                        break;
                    }
                }
            }
        }

        return blockpos == null ? pos : blockpos;
    }
}
