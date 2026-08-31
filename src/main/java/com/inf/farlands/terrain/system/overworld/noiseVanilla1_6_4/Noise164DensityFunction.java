package com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4;

import com.inf.farlands.Config;
import com.inf.farlands.util.HashUtil;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * 1.6.4 地形密度函数（initializeNoiseField L303-334 + generateTerrain 三线性插值移植）。
 *
 * cell = (4, 8, 4)（XZ quart / Y 8-block 单元，1.6.4 网格单位）：
 * - compute 定位 cell，变化时算 8 角点密度（噪声项 + 高度场项）
 * - 8 角 lerp3 三线性插值到 block（与原版 generateTerrain 插值数学等价，追踪验证）
 *
 * 角点密度（L303-334 去耦合）：
 *   noiseVal = lerp(noise1, noise2, clamp((noise3/10+1)/2)) / 512
 *   dY = (blockY − centerY_block)·grad；blockY < centerY 时 dY×4（地下衰减）
 *   density = noiseVal − dY
 *   顶部渐变：blockY > 104 → t=min((blockY−104)/24, 1) → density·(1−t) + (−10)·t
 *
 * 角点坐标：XZ quart（cellX/cellZ，4 block/格），Y 8-block 单元（cy，8 block/格）。
 *
 * 性能（仿 beta pointCache）：cornerCache 按角点坐标缓存完整密度——相邻 cell 的
 * 8 角中 4 个共享角点直接命中，每 chunk 唯一角点 5×3×5=75 个，替代 32 cell × 8
 * 重复计算；高度场 Result 由 Noise164HeightField 内部按 (cx,cz) 缓存，biome 查询
 * 由 per-chunk 10×10 网格缓存（setChunk）。DensityFunction 为 per-NoiseChunk
 * 实例（单 chunk 生命周期），缓存无跨 chunk 污染。
 */
@SuppressWarnings("null")
public final class Noise164DensityFunction implements DensityFunction.SimpleFunction {

    private static final double MAX = 2000.0D;
    private static final double MIN = -2000.0D;

    private final Noise164Octaves noise1;
    private final Noise164Octaves noise2;
    private final Noise164Octaves noise3;
    private final Noise164HeightField heightField;

    // ---- 8 角缓存：cell 变化重算 ----
    private int cachedCx = Integer.MIN_VALUE;
    private int cachedCy = Integer.MIN_VALUE;
    private int cachedCz = Integer.MIN_VALUE;
    private final double[] corners = new double[8];

    // ---- 角点密度缓存（仿 beta pointCache）：hashPos(cx,cy,cz) → 密度 ----
    private final Long2DoubleOpenHashMap cornerCache = new Long2DoubleOpenHashMap();

    public Noise164DensityFunction(Noise164Octaves noise1, Noise164Octaves noise2,
            Noise164Octaves noise3, Noise164Octaves noise6) {
        this.noise1 = noise1;
        this.noise2 = noise2;
        this.noise3 = noise3;
        this.heightField = new Noise164HeightField(noise6);
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

    /** 角点密度查缓存，miss 才计算——相邻 cell 共享角点（原版 5×17×5 网格点共享语义）。 */
    private double cornerDensityCached(int cx, int cy, int cz) {
        long key = HashUtil.hashPos(cx, cy, cz);
        double v = this.cornerCache.get(key);
        if (Double.isNaN(v)) {
            v = cornerDensity(cx, cy, cz);
            this.cornerCache.put(key, v);
        }
        return v;
    }

    /** 角点密度：噪声项 − 高度场项 + 顶部渐变。cell 坐标（quart/8-block 单元）。 */
    private double cornerDensity(int cx, int cy, int cz) {
        // ---- 噪声项（L315-324）----
        double n1 = this.noise1.sample3D(cx, cy, cz, 684.412D, 684.412D, 684.412D);
        double n2 = this.noise2.sample3D(cx, cy, cz, 684.412D, 684.412D, 684.412D);
        double n3 = this.noise3.sample3D(cx, cy, cz, 684.412D / 80.0D, 684.412D / 160.0D, 684.412D / 80.0D);
        double blend = (n3 / 10.0D + 1.0D) / 2.0D;
        double noiseVal;
        if (blend < 0.0D) {
            noiseVal = n1 / 512.0D;
        } else if (blend > 1.0D) {
            noiseVal = n2 / 512.0D;
        } else {
            noiseVal = n1 / 512.0D + (n2 / 512.0D - n1 / 512.0D) * blend;
        }

        // ---- 高度场项（L303-313 去耦合，Result 按 (cx,cz) 内部缓存）----
        Noise164HeightField.Result hf = this.heightField.compute(cx, cz);
        double blockY = (double) cy * 8.0D;
        double dY = (blockY - hf.centerY()) * hf.grad();
        if (dY < 0.0D) {
            dY *= 4.0D;
        }
        double density = noiseVal - dY;

        // ---- 顶部渐变（L327-330，t clamp ≤1 防无限 Y 翻转）----
        // Config.topFadeEnabled 开关：默认关（与 beta C11 同源风险隔离，note #81）。
        if (Config.topFadeEnabled && blockY > 104.0D) {
            double t = (blockY - 104.0D) / 24.0D;
            if (t > 1.0D) {
                t = 1.0D;
            }
            density = density * (1.0D - t) + (-10.0D) * t;
        }
        return density;
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
