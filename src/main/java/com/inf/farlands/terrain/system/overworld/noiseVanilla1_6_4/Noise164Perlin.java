package com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4;

import net.minecraft.util.RandomSource;

/**
 * 1.6.4 {@code NoiseGeneratorPerlin} 逐字节移植（单点采样版）。
 *
 * 原版是批量 {@code populateNoiseArray}（2D/3D 双分支 + y 整数格缓存），移植为
 * 单点 {@link #sample(double,double,double)} 与 {@link #sample2D(double,double)}——
 * 从批量循环中提取单格计算，结果与原版逐格一致（同一置换表/梯度/插值公式）。
 *
 * 与 beta 1.7.3 的 {@code LegacyPerlinNoise} 关键差异：外层 Octaves 对 XZ 起点
 * 取模 2^24（见 Noise164Octaves），Perlin 内部坐标永不溢出；单层核心
 * （置换表 shuffle、grad 三元组、smoothstep、2D func_76309_a 梯度）两版同源。
 *
 * 坐标范围：XZ 经 Octaves 取模后 ∈ ±2^24，+ xCoord(&lt;256) 后 &lt; 2^25——int 强转安全；
 * Y 不取模，极端生成高度下 cy×684.412 可超 int → (int) 强转饱和，但噪声误差
 * 不影响输出：正极端 Y 恒空气、负极端 Y 恒石头（dY 数十亿 ≫ 噪声 ±128），
 * 饱和无实际影响——保持原版 (int) 字面写法。
 */
@SuppressWarnings("null")
public final class Noise164Perlin {

    private final int[] permutations = new int[512];
    private final double xCoord;
    private final double yCoord;
    private final double zCoord;

    public Noise164Perlin(RandomSource random) {
        this.xCoord = random.nextDouble() * 256.0D;
        this.yCoord = random.nextDouble() * 256.0D;
        this.zCoord = random.nextDouble() * 256.0D;

        for (int i = 0; i < 256; i++) {
            this.permutations[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i) + i;
            int tmp = this.permutations[i];
            this.permutations[i] = this.permutations[j];
            this.permutations[j] = tmp;
            this.permutations[i + 256] = this.permutations[i];
        }
    }

    /** 1.6.4 L39-41：标准线性插值。 */
    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** 1.6.4 L43-48：2D 快速路径专用梯度（第三维 y 恒 0 折叠）。 */
    private static double grad2D(int hash, double x, double z) {
        int h = hash & 15;
        double u = (double) (1 - ((h & 8) >> 3)) * x;
        double v = h < 4 ? 0.0D : (h != 12 && h != 14 ? z : x);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** 1.6.4 L50-55：3D 梯度。 */
    private static double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : (h != 12 && h != 14 ? z : x);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    /** 原版 (int) 强转 + 负向修正的 floor。XZ 经 Octaves 取模约束、Y 在饱和区
     * （极端生成高度）噪声误差不影响输出（正 Y 恒空气、负 Y 恒石头，见类注释），
     * int 饱和无实际影响——保持原版字面写法。 */
    private static int floorInt(double v) {
        int r = (int) v;
        return v < (double) r ? r - 1 : r;
    }

    private static double smooth(double v) {
        return v * v * v * (v * (v * 6.0D - 15.0D) + 10.0D);
    }

    /**
     * 3D 单点采样，等价原版 populateNoiseArray 3D 分支（L127-181）对单点的计算。
     * 8 角梯度 + 三线性插值；x/z 坐标经 Octaves 取模约束（int 安全），y 用 long floor。
     */
    public double sample(double x, double y, double z) {
        double x1 = x + this.xCoord;
        double y1 = y + this.yCoord;
        double z1 = z + this.zCoord;
        int xi = floorInt(x1);
        int yi = floorInt(y1);
        int zi = floorInt(z1);
        int i = xi & 255;
        int j = yi & 255;
        int k = zi & 255;
        x1 -= (double) xi;
        y1 -= (double) yi;
        z1 -= (double) zi;
        double sx = smooth(x1);
        double sy = smooth(y1);
        double sz = smooth(z1);

        int a = this.permutations[i] + j;
        int aa = this.permutations[a] + k;
        int ab = this.permutations[a + 1] + k;
        int b = this.permutations[i + 1] + j;
        int ba = this.permutations[b] + k;
        int bb = this.permutations[b + 1] + k;

        return lerp(sz,
                lerp(sy,
                        lerp(sx, grad(this.permutations[aa], x1, y1, z1),
                                grad(this.permutations[ba], x1 - 1.0D, y1, z1)),
                        lerp(sx, grad(this.permutations[ab], x1, y1 - 1.0D, z1),
                                grad(this.permutations[bb], x1 - 1.0D, y1 - 1.0D, z1))),
                lerp(sy,
                        lerp(sx, grad(this.permutations[aa + 1], x1, y1, z1 - 1.0D),
                                grad(this.permutations[ba + 1], x1 - 1.0D, y1, z1 - 1.0D)),
                        lerp(sx, grad(this.permutations[ab + 1], x1, y1 - 1.0D, z1 - 1.0D),
                                grad(this.permutations[bb + 1], x1 - 1.0D, y1 - 1.0D, z1 - 1.0D))));
    }

    /**
     * 2D 单点采样（XZ 平面，y 恒 0），等价原版 populateNoiseArray var9==1 快速路径
     * （L69-111）：四角两个 grad2D + 两个 grad(y=0)，双线性插值。
     */
    public double sample2D(double x, double z) {
        double x1 = x + this.xCoord;
        double z1 = z + this.zCoord;
        int xi = floorInt(x1);
        int zi = floorInt(z1);
        int i = xi & 255;
        int k = zi & 255;
        x1 -= (double) xi;
        z1 -= (double) zi;
        double sx = smooth(x1);
        double sz = smooth(z1);

        int a = this.permutations[i];
        int aa = this.permutations[a] + k;
        int ab = this.permutations[i + 1];
        int b = this.permutations[ab] + k;

        double v0 = lerp(sx, grad2D(this.permutations[aa], x1, z1),
                grad(this.permutations[b], x1 - 1.0D, 0.0D, z1));
        double v1 = lerp(sx, grad(this.permutations[aa + 1], x1, 0.0D, z1 - 1.0D),
                grad(this.permutations[b + 1], x1 - 1.0D, 0.0D, z1 - 1.0D));
        return lerp(sz, v0, v1);
    }
}
