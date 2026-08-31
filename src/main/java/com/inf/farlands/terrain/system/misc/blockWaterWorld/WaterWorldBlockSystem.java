package com.inf.farlands.terrain.system.misc.blockWaterWorld;

import com.inf.farlands.terrain.BlockSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * WaterWorld：整个世界（所有坐标、所有 Y）全部是水，无一例外。
 *
 * fillBlock 恒返回水的方块状态（disableFluidSpread 下流体不蔓延，保持写入态）。
 * 与 Diorite（全闪长岩）同类的 meme 基底——水覆盖一切。
 */
@SuppressWarnings("null")
public final class WaterWorldBlockSystem implements BlockSystem {

    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        return WATER;
    }

    @Override
    public void onLevelLoad(long seed) {
        // 与种子无关
    }
}
