package com.inf.farlands.terrain.system.overworld.noiseVanilla1_16_5;

import com.inf.farlands.Config;
import com.inf.farlands.util.HashUtil;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * 1.16.5 地形密度函数（fillNoiseColumn L193-280 + sampleAndClampNoise L145-185 移植）。
 *
 * cell = (4, 8, 4)（XZ quart / Y 8-block 单元，1.16.5 网格单位）：
 * - compute 定位 cell，变化时算 8 角点密度（噪声项 + 高度场项 + slide）
 * - 8 角 lerp3 三线性插值到 block（与原版 fillFromNoise 插值数学等价）
 *
 * 角点密度（fillNoiseColumn 单点化）：
 *   noise = sampleAndClampNoise(cx, cy, cz)：
 *       minSum += minLimit.getOctaveNoise(i).noise(xw, yw, zw, yFreq, yFreq) / freq   // 16 层
 *       maxSum += maxLimit.getOctaveNoise(i).noise(同上) / freq                      // 16 层
 *       mainSum += main.getOctaveNoise(i).noise(同上) / freq                         // 仅 i<8
 *       noise = clampedLerp(minSum/512, maxSum/512, (mainSum/10+1)/2)
 *   高度场（5×5 biome 加权，列内共享）→ baseDensity/ySlope/rdo
 *   slope = (1 − cy×2/chunkCountY + rdo) × densityFactor + densityOffset
 *   base = (slope + baseDensity) × ySlope
 *   noise += base>0 ? base×4 : base
 *   topSlide：t=((chunkCountY−cy)−offset)/size → clampedLerp(target, noise, t)
 *     —— 受 Config.topFadeEnabled 控制（默认关，与 beta C11/1.6.4 渐消开关一致）
 *   bottomSlide：主世界 size=0 被门卫跳过（不执行）
 *
 * 性能（仿 beta pointCache）：cornerCache 按角点坐标缓存完整密度——相邻 cell 的
 * 8 角中共享角点直接命中；高度场 Result 由 Noise1165HeightField 内部按 (cx,cz) 缓存。
 * DensityFunction 为 per-NoiseChunk 实例（单 chunk 生命周期），缓存无跨 chunk 污染。
 */
@SuppressWarnings("null")
public final class Noise1165DensityFunction implements DensityFunction.SimpleFunction {

    private static final double MAX = 2000.0D;
    private static final double MIN = -2000.0D;

    // ---- 1.16.5 主世界参数（NoiseGeneratorSettings.overworld）----
    private static final int HEIGHT = 256;
    private static final int CHUNK_COUNT_Y = HEIGHT / 8; // 32
    private static final double XZ_SCALE = 0.9999999814507745D;
    private static final double Y_SCALE = 0.9999999814507745D;
    private static final double XZ_FACTOR = 80.0D;
    private static final double Y_FACTOR = 160.0D;
    private static final double DENSITY_FACTOR = 1.0D;
    private static final double DENSITY_OFFSET = -0.46875D;
    private static final int TOP_SLIDE_TARGET = -10;
    private static final int TOP_SLIDE_SIZE = 3;
    private static final int TOP_SLIDE_OFFSET = 0;
    private static final int BOTTOM_SLIDE_SIZE = 0; // 主世界 bottomSlide size=0 → 门卫跳过

    private final PerlinNoise minLimit;
    private final PerlinNoise maxLimit;
    private final PerlinNoise main;
    private final Noise1165HeightField heightField;

    // ---- 8 角缓存：cell 变化重算 ----
    private int cachedCx = Integer.MIN_VALUE;
    private int cachedCy = Integer.MIN_VALUE;
    private int cachedCz = Integer.MIN_VALUE;
    private final double[] corners = new double[8];

    // ---- 角点密度缓存（仿 beta pointCache）：hashPos(cx,cy,cz) → 密度 ----
    private final Long2DoubleOpenHashMap cornerCache = new Long2DoubleOpenHashMap();

    public Noise1165DensityFunction(PerlinNoise minLimit, PerlinNoise maxLimit, PerlinNoise main,
            PerlinNoise depthNoise) {
        this.minLimit = minLimit;
        this.maxLimit = maxLimit;
        this.main = main;
        this.heightField = new Noise1165HeightField(depthNoise);
        this.cornerCache.defaultReturnValue(Double.NaN);
    }

    @Override
    public double compute(DensityFunction.FunctionContext context) {
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        int cx = Math.floorDiv(x, 4);
        int cy = Math.floorDiv(y, 8);
        int cz = Math.floorDiv(z, 4);
        if (cx != this.cachedCx || cy != this.cachedCy || cz != this.cachedCz) {
            this.cachedCx = cx;
            this.cachedCy = cy;
            this.cachedCz = cz;
            // 首次进入新 chunk 时预取 biome 网格（per-NoiseChunk 单 chunk，幂等）
            this.heightField.setChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
            // 8 角：XZ ±4 block、Y ±8 block（角点缓存共享相邻 cell）
            this.corners[0] = cornerDensityCached(cx, cy, cz);
            this.corners[1] = cornerDensityCached(cx + 1, cy, cz);
            this.corners[2] = cornerDensityCached(cx, cy + 1, cz);
            this.corners[3] = cornerDensityCached(cx + 1, cy + 1, cz);
            this.corners[4] = cornerDensityCached(cx, cy, cz + 1);
            this.corners[5] = cornerDensityCached(cx + 1, cy, cz + 1);
            this.corners[6] = cornerDensityCached(cx, cy + 1, cz + 1);
            this.corners[7] = cornerDensityCached(cx + 1, cy + 1, cz + 1);
        }
        double fx = (double) (x - cx * 4) / 4.0D;
        double fy = (double) (y - cy * 8) / 8.0D;
        double fz = (double) (z - cz * 4) / 4.0D;
        return lerp3(this.corners, fx, fy, fz);
    }

    /** 角点密度查缓存，miss 才计算——相邻 cell 共享角点（1.16.5 列采样共享语义）。 */
    private double cornerDensityCached(int cx, int cy, int cz) {
        long key = HashUtil.hashPos(cx, cy, cz);
        double v = this.cornerCache.get(key);
        if (Double.isNaN(v)) {
            v = cornerDensity(cx, cy, cz);
            this.cornerCache.put(key, v);
        }
        return v;
    }

    /** 角点密度：噪声项 −/＋ 高度场项 + slide。cell 坐标（quart/8-block 单元）。 */
    private double cornerDensity(int cx, int cy, int cz) {
        // ---- 噪声项（sampleAndClampNoise L145-185，手动展开 16 层八度）----
        // 频率参数（fillNoiseColumn L243-246 传入）：
        //   var4 = 684.412·xzScale（min/max 的 x/z 频率）
        //   var6 = 684.412·yScale（min/max 的 y 频率）
        //   var8 = 684.412·xzScale/xzFactor（main 的 x/z 频率，低 xzFactor 倍）
        //   var10= 684.412·yScale/yFactor（main 的 y 频率，低 yFactor 倍）
        // main 分支频率与 min/max 不同（字节码 L182-228 验证）——min/max 用 var4/var6，
        // main 用 var8/var10，且第 4/5 参 = yFreq/cellY·yFreq（不是同值）。
        double minSum = 0.0D;
        double maxSum = 0.0D;
        double mainSum = 0.0D;
        double freq = 1.0D;
        for (int i = 0; i < 16; i++) {
            double xw = PerlinNoise.wrap((double) cx * XZ_SCALE * 684.412D * freq);
            double yw = PerlinNoise.wrap((double) cy * Y_SCALE * 684.412D * freq);
            double zw = PerlinNoise.wrap((double) cz * XZ_SCALE * 684.412D * freq);
            double yFreq = Y_SCALE * 684.412D * freq; // min/max 的 Y 量化步长 = var6·freq
            ImprovedNoise minOct = this.minLimit.getOctaveNoise(i);
            if (minOct != null) {
                minSum += minOct.noise(xw, yw, zw, yFreq, (double) cy * yFreq) / freq;
            }
            ImprovedNoise maxOct = this.maxLimit.getOctaveNoise(i);
            if (maxOct != null) {
                maxSum += maxOct.noise(xw, yw, zw, yFreq, (double) cy * yFreq) / freq;
            }
            if (i < 8) {
                ImprovedNoise mainOct = this.main.getOctaveNoise(i);
                if (mainOct != null) {
                    // main：频率低 xzFactor/yFactor 倍（var8/var10），第 4/5 参 = var10·freq/cellY·var10·freq
                    double mx = PerlinNoise.wrap((double) cx * XZ_SCALE * 684.412D / XZ_FACTOR * freq);
                    double my = PerlinNoise.wrap((double) cy * Y_SCALE * 684.412D / Y_FACTOR * freq);
                    double mz = PerlinNoise.wrap((double) cz * XZ_SCALE * 684.412D / XZ_FACTOR * freq);
                    double myFreq = Y_SCALE * 684.412D / Y_FACTOR * freq;
                    mainSum += mainOct.noise(mx, my, mz, myFreq, (double) cy * myFreq) / freq;
                }
            }
            freq /= 2.0D;
        }
        double noise = Mth.clampedLerp(minSum / 512.0D, maxSum / 512.0D, (mainSum / 10.0D + 1.0D) / 2.0D);

        // ---- 高度场项（L205-253，列内共享）----
        Noise1165HeightField.Result hf = this.heightField.compute(cx, cz);
        double slope = (1.0D - (double) cy * 2.0D / (double) CHUNK_COUNT_Y + hf.randomDensityOffset())
                * DENSITY_FACTOR + DENSITY_OFFSET;
        double base = (slope + hf.baseDensity()) * hf.ySlope();
        if (base > 0.0D) {
            noise += base * 4.0D;
        } else {
            noise += base;
        }

        // ---- topSlide（L268-271）：顶部渐消，受 Config.topFadeEnabled ----
        if (Config.topFadeEnabled && TOP_SLIDE_SIZE > 0) {
            double t = ((double) (CHUNK_COUNT_Y - cy) - TOP_SLIDE_OFFSET) / TOP_SLIDE_SIZE;
            noise = Mth.clampedLerp(TOP_SLIDE_TARGET, noise, t);
        }
        // bottomSlide（L273-276）：主世界 size=0 → 门卫跳过，不执行

        return noise;
    }

    /** 三线性插值，8 角顺序 = [000, 100, 010, 110, 001, 101, 011, 111]。 */
    private static double lerp3(double[] c, double fx, double fy, double fz) {
        double c00 = c[0] + fx * (c[1] - c[0]);
        double c10 = c[2] + fx * (c[3] - c[2]);
        double c01 = c[4] + fx * (c[5] - c[4]);
        double c11 = c[6] + fx * (c[7] - c[6]);
        double c0 = c00 + fy * (c10 - c00);
        double c1 = c01 + fy * (c11 - c01);
        return c0 + fz * (c1 - c0);
    }

    @Override
    public double minValue() {
        return MIN;
    }

    @Override
    public double maxValue() {
        return MAX;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return null; // 与 BetaDensityFunction 同：运行时构造，不序列化
    }
}
