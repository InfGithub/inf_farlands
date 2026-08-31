package com.inf.farlands.terrain.system.skyDimension.noiseBeta1_7_3;

import com.inf.farlands.Config;
import com.inf.farlands.util.HashUtil;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * 天域密度函数：b1.7.3 {@code ChunkProviderSky.func_28073_a} 移植。
 *
 * cell = (8, 4, 8)（XZ 8 格 / Y 4 格——"竖过来"网格，NoiseSystem.noiseSize {2,1}）：
 * - 天域 finalDensity 无 Interpolated 标记 → NoiseChunk wrap 不为其建 NoiseInterpolator，
 *   cell 内插值必须由本函数自己做：8 角缓存 + lerp3（仿 BetaDensityFunction）
 * - 角点密度 = b1.7.3 单点密度（含渐消——b1.7.3 渐消在采样点算、插值在其后）
 * - 密度 = lerp(lower/512, upper/512, blendF) − 8.0 + 上下渐消（无高度衰减 → 悬浮岛）
 * - b1.7.3 的湿润调制 var25/tempFactor 是死代码（var27 只喂 var36 死链）——不移植采样，
 *   octaves 构造仍消费 temp 组占位（随机流顺序）
 * - pointCache：角点按 (cx,cy,cz) 缓存（纯函数，无 biome 依赖），相邻 cell 共享角点只算一次，
 *   缓存无需 clear
 */
public final class SkyDimensionDensityFunction implements DensityFunction.SimpleFunction {

    private static final double MAX = 2000.0D;
    private static final double MIN = -2000.0D;

    private final SkyDimensionNoise noise;

    // 8 角缓存：cell 变化时算 8 个角点密度，cell 内每格三线性插值（相邻 cell 共享角点由 pointCache 命中）
    private int cachedCx = Integer.MIN_VALUE;
    private int cachedCy = Integer.MIN_VALUE;
    private int cachedCz = Integer.MIN_VALUE;
    private final double[] cachedCorners = new double[8];

    /** 角点密度缓存：hashPos(cx,cy,cz) → density——相邻 cell 共享角点只算一次（仿 BetaDensityFunction）。
     * computeCorner 是纯函数（无 biome 依赖），缓存无需 clear；生命周期 = 本 DensityFunction（per-chunk）。 */
    private final Long2DoubleOpenHashMap pointCache = new Long2DoubleOpenHashMap();

    {
        pointCache.defaultReturnValue(Double.NaN);
    }

    public SkyDimensionDensityFunction(SkyDimensionNoise noise) {
        this.noise = noise;
    }

    @SuppressWarnings("null")
    @Override
    public double compute(FunctionContext context) {
        if (noise == null) {
            return -30.0; // onLevelLoad 前（理论上 fill 先于加载不会发生）：恒空气，防御
        }
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        // 天域 cell：XZ 8 格、Y 4 格（noiseSize {2,1}），与 b1.7.3 采样网格一致
        int cx = Math.floorDiv(x, 8);
        int cy = Math.floorDiv(y, 4);
        int cz = Math.floorDiv(z, 8);
        if (cx != cachedCx || cy != cachedCy || cz != cachedCz) {
            cachedCx = cx;
            cachedCy = cy;
            cachedCz = cz;
            // 8 角：XZ ±8、Y ±4（角点缓存共享相邻 cell）
            cachedCorners[0] = cornerDensity(cx, cy, cz);
            cachedCorners[1] = cornerDensity(cx + 1, cy, cz);
            cachedCorners[2] = cornerDensity(cx, cy + 1, cz);
            cachedCorners[3] = cornerDensity(cx + 1, cy + 1, cz);
            cachedCorners[4] = cornerDensity(cx, cy, cz + 1);
            cachedCorners[5] = cornerDensity(cx + 1, cy, cz + 1);
            cachedCorners[6] = cornerDensity(cx, cy + 1, cz + 1);
            cachedCorners[7] = cornerDensity(cx + 1, cy + 1, cz + 1);
        }
        double fx = (double) (x - cx * 8) / 8.0;
        double fy = (double) (y - cy * 4) / 4.0;
        double fz = (double) (z - cz * 8) / 8.0;
        return lerp3(cachedCorners, fx, fy, fz);
    }

    /** 角点密度查缓存，miss 才计算——相邻 cell 共享角点（b1.7.3 网格点共享语义）。 */
    private double cornerDensity(int cx, int cy, int cz) {
        long key = HashUtil.hashPos(cx, cy, cz);
        double v = pointCache.get(key);
        if (Double.isNaN(v)) {
            v = computeCorner(cx, cy, cz);
            pointCache.put(key, v);
        }
        return v;
    }

    /** b1.7.3 func_28073_a 单点密度（cell 角点坐标），含渐消。纯函数（无 biome 依赖）。 */
    private double computeCorner(int cx, int cy, int cz) {
        // blendFactor（L233-240）
        double blendF = (noise.sample(2, cx, cy, cz) / 10.0 + 1.0) / 2.0;
        blendF = blendF < 0.0 ? 0.0 : (blendF > 1.0 ? 1.0 : blendF);

        // density = lerp(lower/512, upper/512, blendF) − 8.0（L242）
        double lower = noise.sample(0, cx, cy, cz) / 512.0;
        double upper = noise.sample(1, cx, cy, cz) / 512.0;
        double d = lower + (upper - lower) * blendF - 8.0;

        // 顶部渐消（L245-248）：cy > 1 → t=(cy−1)/31，clamp ≤1（无限 Y 防翻转）。
        // Config.topFadeEnabled 统一开关（#100：beta C11 / 1.6.4 / 1.16.5 同款）；默认关 → 岛向上无限
        if (Config.topFadeEnabled && cy > 1) {
            double t = (cy - 1) / 31.0;
            if (t > 1.0) {
                t = 1.0;
            }
            d = d * (1.0 - t) + (-30.0) * t;
        }
        // 底部渐消（L250-254）：cy < 8 → t=(8−cy)/7，clamp ≤1。
        // Config.bottomFadeEnabled 天域专属（其他系统无底部渐消）；默认关 → 岛向下无限
        if (Config.bottomFadeEnabled && cy < 8) {
            double t = (8 - cy) / 7.0;
            if (t > 1.0) {
                t = 1.0;
            }
            d = d * (1.0 - t) + (-30.0) * t;
        }
        return d;
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
        return null; // 运行时构造，不序列化
    }
}
