package com.inf.farlands.terrain.system.overworld.blockInfdev_20100226;

import com.inf.farlands.terrain.BlockSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Infdev 20100226 无限世界地形——原样移植。
 *
 * Minecraft 第一个无限世界版本（2010-02-26）的地形生成：
 *  - y &lt; 0 全基岩（原代码 getBlock 的 y&lt;0 分支：x.q.ao）
 *  - 0 ≤ y &lt; 高度 → 草方块（原代码 x.h = af = 方块 id 2）
 *  - 高度 = 64 + 4×4 周期微丘（原代码 a(g) 的 n5%4/n6%4 判定链，海拔 64~68）
 *  - 无洞穴/水/树——只有草面 + 底下基岩
 *
 * 移植要点：
 *  - 原代码 n5 从 chunkX*4 起恒为 4 的倍数、n5%4 恒非负（0..3）；本实现直接拿
 *    绝对坐标，用 Math.floorMod 保持同样语义（负坐标安全，否则模式错乱）
 *  - 方块映射：id 2 草 → GRASS_BLOCK；id 11 基岩 → BEDROCK
 *  - y ≥ 128 原代码返回空气，高度图最大 68 自然满足
 */
@SuppressWarnings("null")
public final class Infdev20100226BlockSystem implements BlockSystem {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        // 原代码 getBlock：y < 0 → 基岩（严格原样：负半世界全基岩）
        if (y < 0) {
            return BEDROCK;
        }
        // 原代码 getBlock：y ≥ 高度 → 空气（高度最大 68，y ≥ 128 自然满足）
        return y < height(x, z) ? GRASS : AIR;
    }

    /** 地形高度（原代码 a(g) 的判定链原样搬移）：4×4 周期微丘，海拔 64~68。 */
    private static int height(int x, int z) {
        int mx = Math.floorMod(x, 4);
        int mz = Math.floorMod(z, 4);
        if (mx == 0 || mz == 0) {
            return 64; // 原：n5%4==0 || n6%4==0 → 64（四边/角低地）
        }
        if (mx == 2 && mz == 2) {
            return 64; // 原：n5%4==2 && n6%4==2 → 64（中心）
        }
        if (mx == 1) {
            return 64 + mz; // 原：n5%4==1 → 64 + n6%4
        }
        if (mx == 3) {
            return 68 - mz; // 原：n5%4==3 → 68 - n6%4
        }
        return 66; // 原：n5%4==2（其余 mz∈{1,3}）→ 66
    }

    @Override
    public void onLevelLoad(long seed) {
        // 原样：地形与种子无关
    }
}
