package com.inf.farlands.terrain.system.misc.blockSierpinski;

import com.inf.farlands.terrain.BlockSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 谢尔宾斯基四棱锥（逐块几何系统）。
 * 顶点 0 2^30-1 0
 * 层数 d = 2^30-1 - y。
 */
public final class SierpinskiPyramidBlockSystem implements BlockSystem {

    private static final int Y_MAX = (1 << 30) - 1;   // 顶点 y = 1073741823

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        return isInSet(x, y, z) ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /** diff = d - coord 偶且 diff>>1 的位 in d。 */
    private static boolean isInSet(int x, int y, int z) {
        long d = (long) Y_MAX - y; // 层数
        if (d < 0) {
            return false; // 顶点上方
        }
        if (d == 0) {
            return x == 0 && z == 0; // 顶点单点
        }
        long dx = d - x;
        if ((dx & 1) != 0) {
            return false;
        }
        if (((dx >> 1) & ~d) != 0) {
            return false;
        }
        long dz = d - z;
        if ((dz & 1) != 0) {
            return false;
        }
        return ((dz >> 1) & ~d) == 0;
    }

    @Override
    public void onLevelLoad(long seed) {
    }
}
