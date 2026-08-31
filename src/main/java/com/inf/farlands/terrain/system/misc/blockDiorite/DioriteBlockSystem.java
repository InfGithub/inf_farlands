package com.inf.farlands.terrain.system.misc.blockDiorite;

import com.inf.farlands.terrain.BlockSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 闪长岩填充系统：整个世界（所有坐标、所有 Y）全部填满闪长岩。
 *
 * 普通测试用基底——与 Void（纯空气）相反：无地形、无空洞、无流体，
 * fillBlock 恒返回 DIORITE，O(1) 零判定。
 */
@SuppressWarnings("null")
public final class DioriteBlockSystem implements BlockSystem {

    private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        return DIORITE;
    }

    @Override
    public void onLevelLoad(long seed) {
        // 与种子无关
    }
}
