package com.inf.farlands.terrain.system.misc.blockMenger;

import com.inf.farlands.terrain.BlockSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 谢尔宾斯基海绵 Menger Sponge 逐块几何系统：全局三进制分形，黑曜石实心。
 * 每层挖掉坐标三进制至少两位 = 1 的格，即 3³ 块中心 7 块，无限递归，任意坐标直接判定。
 * 正负对称：用 |坐标| 判定（long 防 Integer.MIN_VALUE 溢出），八象限互为镜像——
 * 负坐标的 floorDiv/floorMod 展开会收敛到 -1 不动点（死循环）且位 = 2 ≠ 1 破坏对称，
 * 绝对值映射同时修复两者。 */
public final class MengerSpongeBlockSystem implements BlockSystem {

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        // long 绝对值：int 范围安全（|MIN_VALUE| = 2^31 不溢出 long）
        long ax = Math.abs((long) x), ay = Math.abs((long) y), az = Math.abs((long) z);
        for (long px = ax, py = ay, pz = az; px != 0 || py != 0 || pz != 0; ) {
            // 非负坐标 % 3 = floorMod（余数 0..2），位检查不变
            if ((px % 3 == 1 && py % 3 == 1)
                    || (px % 3 == 1 && pz % 3 == 1)
                    || (py % 3 == 1 && pz % 3 == 1)) {
                return Blocks.AIR.defaultBlockState();
            }
            px /= 3;
            py /= 3;
            pz /= 3; // 非负 long 整除向零收敛到 0，无死循环
        }
        return Blocks.OBSIDIAN.defaultBlockState();
    }

    @Override
    public void onLevelLoad(long seed) {
    }
}
