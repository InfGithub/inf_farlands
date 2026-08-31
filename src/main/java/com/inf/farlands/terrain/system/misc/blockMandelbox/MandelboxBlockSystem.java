package com.inf.farlands.terrain.system.misc.blockMandelbox;

import com.inf.farlands.terrain.BlockSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Mandelbox 分形逐块几何系统：整个 ±2.14B 可玩世界 = 一个巨型 Mandelbox。
 *
 * 缩放 scale = 2^30：cx = x/2^30 ∈ [-1,1] 作为迭代常数 c，迭代 z = scale·fold(z) + c。
 * 盒折叠（box fold，折叠到 [-1,1]）+ 球折叠（sphere fold，半径落到 [0.5,1]）——
 * 纯折叠/缩放运算，无超越函数，确定性（与种子无关）。
 * MAX_ITER = 64：实测 1024/256 每格迭代满仍太慢（含 sqrt），降至 64——速度优先，卷曲精度让位于可玩性。
 */
@SuppressWarnings("null")
public final class MandelboxBlockSystem implements BlockSystem {

    private static final double INV_SCALE = 1.0 / (1L << 30);
    private static final int MAX_ITER = 64;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        return inMandelbox(x, y, z) ? OBSIDIAN : AIR;
    }

    /**
     * 判断给定整数坐标 (x, y, z) 是否属于标准 Mandelbox 分形的内部点。
     *
     * <p>该函数将整数坐标按 {@link #INV_SCALE} 缩放至 [-1,1] 区间作为迭代常数 c，
     * 然后从原点 z = (0,0,0) 开始迭代 z = scale * fold(z) + c，最多迭代 {@link #MAX_ITER} 次。
     * 若在迭代过程中模平方超过逃逸半径 4.0 (即半径 > 2) 则返回 false；否则认为该点属于分形。</p>
     *
     * <p>算法包含两个折叠操作：
     * <ul>
     *   <li><b>盒折叠 (Box Fold)</b>：将每个坐标分量折叠到区间 [-1,1] 内。</li>
     *   <li><b>球折叠 (Sphere Fold)</b>：根据当前半径 r 进行缩放，使半径落到 [0.5,1] 内。</li>
     * </ul>
     * 随后按固定缩放因子 2.0 进行缩放，并加上常数 c。</p>
     *
     * @param x 整数 x 坐标
     * @param y 整数 y 坐标
     * @param z 整数 z 坐标
     * @return 若该点属于 Mandelbox 则返回 true，否则返回 false
     */
    private static boolean inMandelbox(int x, int y, int z) {
        // 将整数坐标缩放到 [-1,1] 区间，作为迭代常数 c
        double cx = x * INV_SCALE;
        double cy = y * INV_SCALE;
        double cz = z * INV_SCALE;

        // 初始快速逃逸检查：若常数 c 的模平方已超过逃逸半径，则直接排除
        if (cx * cx + cy * cy + cz * cz > 4.0) {
            return false;
        }

        // 迭代变量 z 初始化为原点 (0,0,0)
        double zx = 0.0;
        double zy = 0.0;
        double zz = 0.0;

        // 固定迭代次数
        for (int i = 0; i < MAX_ITER; i++) {
            // ---------- 盒折叠 (Box Fold) ----------
            // 将每个坐标分量折叠到 [-1,1] 区间
            if (zx > 1.0) {
                zx = 2.0 - zx;
            } else if (zx < -1.0) {
                zx = -2.0 - zx;
            }
            if (zy > 1.0) {
                zy = 2.0 - zy;
            } else if (zy < -1.0) {
                zy = -2.0 - zy;
            }
            if (zz > 1.0) {
                zz = 2.0 - zz;
            } else if (zz < -1.0) {
                zz = -2.0 - zz;
            }

            // 计算当前点模的平方 r2 = x^2 + y^2 + z^2
            double r2 = zx * zx + zy * zy + zz * zz;

            // 逃逸检测：若模平方超过 4.0（即半径 > 2），则该点不在集合内
            if (r2 > 4.0) {
                return false;
            }

            // ---------- 球折叠 (Sphere Fold) ----------
            // 计算半径 r
            double r = Math.sqrt(r2);
            double scale;

            // 防止除以零：当半径极小（接近 0）时，将缩放因子设为 0.0，等价于把点拉回原点
            if (r < 1e-30) {
                scale = 0.0;
            } else if (r < 0.5) {
                // 当半径小于 0.5 时，放大到 0.5：scale = 0.5 / r
                scale = 0.5 / r;
            } else if (r > 1.0) {
                // 当半径大于 1.0 时，缩小到 1.0：scale = 1.0 / r
                scale = 1.0 / r;
            } else {
                // 半径在 [0.5, 1.0] 之间时不缩放
                scale = 1.0;
            }

            // 应用球折叠缩放
            zx *= scale;
            zy *= scale;
            zz *= scale;

            // ---------- 缩放和平移 (Scale & Translate) ----------
            // 将折叠后的点按固定缩放因子 2.0 缩放，并加上常数 c
            zx = zx * 2.0 + cx;
            zy = zy * 2.0 + cy;
            zz = zz * 2.0 + cz;
        }

        // 所有迭代均未逃逸，判定该点属于 Mandelbox
        return true;
    }

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // 空气 picker：分形实心 + 空洞，不生成流体（同 Mandelbulb）
        return (x, y, z) -> new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());
    }

    @Override
    public void onLevelLoad(long seed) {
        // Mandelbox 纯数学，与种子无关
    }
}
