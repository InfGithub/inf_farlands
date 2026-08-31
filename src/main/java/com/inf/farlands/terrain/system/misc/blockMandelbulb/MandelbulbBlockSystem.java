package com.inf.farlands.terrain.system.misc.blockMandelbulb;

import com.inf.farlands.terrain.BlockSystem;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * Mandelbulb 分形逐块几何系统（n=8）：整个 ±2.14B 可玩世界 = 一个巨型 Mandelbulb 花朵。
 *
 * 缩放 scale = 2^30：cx = x/2^30 ∈ [-2, 2) 恰好覆盖集合球 |c| ≤ 2，花朵充满世界并在边界截断。
 * 迭代 z = z⁸ + c（球坐标幂），迭代 MAX_ITER 次未逃逸 = 集合内（黑曜石实心）；内部空洞 = 空气。
 *
 * 纯多项式判定（无 sqrt/超越函数）：sinθ·cosφ = zx/r、sinθ·sinφ = zz/r 恒等式消去
 * 极角/方位角三角，sin8θ/cos8θ 用切比雪夫 U7/T8 展开，r⁸ = (r²)⁴——全 FMA/乘法；
 * r² 极小特判防 0/0 NaN。数学逐项验证见会话追踪。
 *
 * 确定性：纯常数 + IEEE 浮点（Java 17+ 默认 strictfp），与种子无关（所有世界形状相同）；
 * 无共享可变状态，线程安全。内部空洞无天空光 = 全黑，玩家自备光源；
 * 核心实心区（|c| &lt; 1）每格迭代满 MAX_ITER，生成较慢，按需跟随玩家。
 */
@SuppressWarnings("null")
public final class MandelbulbBlockSystem implements BlockSystem {

    /** scale = 2^30：可玩范围 ±2.147e9 / 2^30 = ±2.0 = 集合球 |c| ≤ 2，整个世界一个花朵。 */
    private static final double INV_SCALE = 1.0 / (1L << 30);
    /**
     * 迭代上限（写死）：1024——分形卷曲精度用户实测 256 仍不够，提到 1024。
     * 性能代价显著：集合内格迭代满 1024（每格 ~2.5 万运算），核心区生成会明显慢，实测看。
     */
    private static final int MAX_ITER = 1024;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();

    @Override
    public BlockState fillBlock(int x, int y, int z) {
        return inMandelbulb(x, y, z) ? OBSIDIAN : AIR;
    }

    /**
     * Mandelbulb（n=8）集合判定：块坐标 (x,y,z) 是否在分形实体内。
     *
     * 迭代 z = z⁸ + c：z' = r⁸·(sin8θ·cosφ, cos8θ, sin8θ·sinφ) + c。
     * 纯多项式：sinθ·cosφ = zx/r、sinθ·sinφ = zz/r（恒等式），
     * sin8θ = sinθ·U7(cosθ)、cos8θ = T8(cosθ)（切比雪夫），r⁸ = (r²)⁴。
     */
    private static boolean inMandelbulb(int x, int y, int z) {
        // 将整数坐标缩放到集合坐标（x·2^-30 精确，无舍入）
        double cx = x * INV_SCALE, cy = y * INV_SCALE, cz = z * INV_SCALE;

        // 快速退出：|c| > 2 → 必然不在集合（花朵之外）
        if (cx * cx + cy * cy + cz * cz > 4.0) {
            return false;
        }

        // z 的初始值为原点 (0, 0, 0)
        double zx = 0, zy = 0, zz = 0;

        for (int i = 0; i < MAX_ITER; i++) {
            // 当前点模平方 r2 = x² + y² + z²，FMA 减少舍入
            double r2 = Math.fma(zx, zx, Math.fma(zy, zy, zz * zz));
            // 逃逸：模平方超过 4
            if (r2 > 4.0) {
                return false;
            }
            // 极点（z ≈ 0）：z⁸ ≈ 0 → z' = c，防数值问题
            if (r2 < 1e-30) {
                zx = cx;
                zy = cy;
                zz = cz;
                continue;
            }
            // 预计算 r2 的幂次
            double r2_2 = r2 * r2;
            double r2_3 = r2_2 * r2;
            double r2_4 = r2_2 * r2_2;
            // 预计算 zy 的幂次
            double zy_2 = zy * zy;
            double zy_4 = zy_2 * zy_2;
            double zy_6 = zy_4 * zy_2;
            double zy_8 = zy_4 * zy_4;
            // term = 128·zy⁶ - 192·r²·zy⁴ + 80·r⁴·zy² - 8·r⁶（z' 的 x/z 分量公共系数）
            double term = Math.fma(128.0, zy_6, -192.0 * (r2 * zy_4));
            term = Math.fma(term, 1.0, 80.0 * (r2_2 * zy_2));
            term = Math.fma(term, 1.0, -8.0 * r2_3);
            // cos8 = 128·zy⁸ - 256·r²·zy⁶ + 160·r⁴·zy⁴ - 32·r⁶·zy² + r⁸（z' 的 y 分量）
            double cos8 = Math.fma(128.0, zy_8, -256.0 * (r2 * zy_6));
            cos8 = Math.fma(cos8, 1.0, 160.0 * (r2_2 * zy_4));
            cos8 = Math.fma(cos8, 1.0, -32.0 * (r2_3 * zy_2));
            cos8 = Math.fma(cos8, 1.0, r2_4);
            // z' = z⁸ + c
            double term_zy = term * zy;
            zx = Math.fma(term_zy, zx, cx);
            zy = cos8 + cy;
            zz = Math.fma(term_zy, zz, cz);
        }
        // 所有迭代均未逃逸 → 集合内（实心）
        return true;
    }

    @Override
    public Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        // 空气 picker：分形实心 + 空洞，不生成流体
        return (x, y, z) -> new Aquifer.FluidStatus(Integer.MIN_VALUE, Blocks.AIR.defaultBlockState());
    }

    @Override
    public void onLevelLoad(long seed) {
        // Mandelbulb 纯数学，与种子无关——所有世界形状相同
    }
}
