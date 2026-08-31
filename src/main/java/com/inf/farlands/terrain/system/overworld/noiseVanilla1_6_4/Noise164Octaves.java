package com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * 1.6.4 {@code NoiseGeneratorOctaves} 逐字节移植（单点采样版）。
 *
 * 原版 {@code generateNoiseOctaves} 批量填数组：每 octave 对起点 XZ 取模 2^24
 * （Y 不取模），坐标×频率×freq 递减叠加，振幅 1/freq。移植为单点
 * {@link #sample3D} / {@link #sample2D}（2D = y=10 固定切片，等价原版 8 参重载
 * 转 9 参后 ny=1 触发 Perlin 2D 快速路径）。
 *
 * 2^24 周期化（原版 L39-46）：只对 XZ 整数部分取模，Y 不取模——原版 128 高成立；
 * 本移植 Y 不取模（绝对生成高度），Perlin 内用 long floor 防溢出。
 */
public final class Noise164Octaves {

    private static final long MOD = 16777216L; // 2^24

    private final Noise164Perlin[] octaves;

    public Noise164Octaves(RandomSource random, int count) {
        this.octaves = new Noise164Perlin[count];
        for (int i = 0; i < count; i++) {
            this.octaves[i] = new Noise164Perlin(random);
        }
    }

    /** XZ 起点取模：frac + (floor mod 2^24)。原版 L39-46 对 X/Z 的处理。 */
    private static double wrapXZ(double v) {
        double fl = Math.floor(v);
        long flMod = (long) fl % MOD; // Java % 负数 → 负余数，与原版 floor_double_long 后 % 同语义
        return (v - fl) + (double) flMod;
    }

    /**
     * 3D 采样（noise1/2/3 用）：x/z 是 quart/cell 坐标（XZ 每格 4 block），
     * y 是 8-block 单元坐标（Y 每格 8 block）。原版频率参数全部 × 684.412 系，
     * 由调用方传入；本方法每 octave freq 从 1 递减，坐标 × 频率 × freq，
     * 振幅 1/freq。XZ 起点取模 2^24，Y 不取模。
     *
     * @param x  X 单元坐标（quart，4 block/格）
     * @param y  Y 单元坐标（8 block/格）
     * @param z  Z 单元坐标（quart，4 block/格）
     * @param freqX / freqY / freqZ 原版 var8/var10/var12（如 684.412 或 684.412/80）
     */
    public double sample3D(double x, double y, double z, double freqX, double freqY, double freqZ) {
        double sum = 0.0D;
        double f = 1.0D; // var27：每 octave 频率权重从 1 递减
        for (Noise164Perlin p : this.octaves) {
            double px = wrapXZ(x * f * freqX);
            double py = y * f * freqY; // Y 不取模
            double pz = wrapXZ(z * f * freqZ);
            // 原版振幅 = 1/var27 = 1/f（freq 递减 → 振幅递增）；坐标步长 = 频率×freq
            sum += p.sample(px, py, pz) / f;
            f /= 2.0D;
        }
        return sum;
    }

    /**
     * 2D 采样（noise6 湿度用）：XZ 平面 y=10 固定切片，等价原版 8 参重载
     * {@code generateNoiseOctaves(v, x, y, z, nx, nz, fx, fz)} → 内部转 9 参时
     * y 起点=10、ny=1、fy=1.0 → Perlin 2D 快速路径（y 参数纯占位，不参与计算）。
     */
    public double sample2D(double x, double z, double freqX, double freqZ) {
        double sum = 0.0D;
        double f = 1.0D;
        for (Noise164Perlin p : this.octaves) {
            double px = wrapXZ(x * f * freqX);
            double pz = wrapXZ(z * f * freqZ);
            sum += p.sample2D(px, pz) / f;
            f /= 2.0D;
        }
        return sum;
    }
}
