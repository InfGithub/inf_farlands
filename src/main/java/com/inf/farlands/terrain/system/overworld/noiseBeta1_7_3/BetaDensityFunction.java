package com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3;

// import com.inf.farlands.Config;

import com.inf.farlands.util.HashUtil;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.DensityFunction;

public class BetaDensityFunction implements DensityFunction.SimpleFunction {

    private static final double MAX = 2000;
    private static final double MIN = -2000;

    private int cachedCx = Integer.MIN_VALUE;
    private int cachedCy;
    private int cachedCz;
    private final double[] cachedCorners = new double[8];
    private double cachedTemp, cachedHum;

    /**
     * 网格点密度缓存，key = hashPos(cx,cy,cz)：每 cell 8 角中相邻 cell 的公共角只算
     * 一次，每 section 网格点 5×3×5=75，对比 32 cell × 8 角 = 256 次 density。density
     * 依赖 temp/hum，biome 变化即 cachedTemp/Hum 更新时 clear，不跨 biome。
     */
    private final Long2DoubleOpenHashMap pointCache = new Long2DoubleOpenHashMap();

    /** 通道 3/4 的 2D 缓存，y=10.0 切片：按 (cx,cz) 网格点缓存，每 section 5×5=25，对比 256 次 sample。 */
    private final Long2DoubleOpenHashMap sample3Cache = new Long2DoubleOpenHashMap();
    private final Long2DoubleOpenHashMap sample4Cache = new Long2DoubleOpenHashMap();

    {
        pointCache.defaultReturnValue(Double.NaN);
        sample3Cache.defaultReturnValue(Double.NaN);
        sample4Cache.defaultReturnValue(Double.NaN);
    }

    @SuppressWarnings("null")
    @Override
    public double compute(FunctionContext context) {
        BetaTerrainNoise noise = BetaTerrain.get();
        if (noise == null) {
            return 0.0;
        }

        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();

        int cx0 = Math.floorDiv(x, 4);
        int cy0 = Math.floorDiv(y, 8);
        int cz0 = Math.floorDiv(z, 4);

        if (cx0 != cachedCx || cy0 != cachedCy || cz0 != cachedCz) {
            cachedCx = cx0;
            cachedCy = cy0;
            cachedCz = cz0;

            // note #85 断链修复：CURRENT_CHUNK 无人 set → temp/hum 恒 0 → 湿润调制失效。
            // 改用 BetaContext 侧信道（OverworldNoiseFiller.fill 入口 set，仿 Noise164Context）——
            // 按坐标查 biome，语义与原版 chunk.getNoiseBiome(qx, 63q, qz) 一致。
            Holder<Biome> holder = BetaContext.biomeAt(
                    QuartPos.fromBlock(x), QuartPos.fromBlock(z));
            if (holder != null) {
                    Biome.ClimateSettings cs = holder.value().getModifiedClimateSettings();
                    double t = cs.temperature();
                    double h = cs.downfall();
                    if (t != cachedTemp || h != cachedHum) {
                        cachedTemp = t;
                        cachedHum = h;
                        pointCache.clear(); // biome 变化 → density 依赖 temp/hum 失效；sample 缓存保留
                    }
            }

            cachedCorners[0] = pointDensity(noise, cx0, cy0, cz0);
            cachedCorners[1] = pointDensity(noise, cx0 + 1, cy0, cz0);
            cachedCorners[2] = pointDensity(noise, cx0, cy0 + 1, cz0);
            cachedCorners[3] = pointDensity(noise, cx0 + 1, cy0 + 1, cz0);
            cachedCorners[4] = pointDensity(noise, cx0, cy0, cz0 + 1);
            cachedCorners[5] = pointDensity(noise, cx0 + 1, cy0, cz0 + 1);
            cachedCorners[6] = pointDensity(noise, cx0, cy0 + 1, cz0 + 1);
            cachedCorners[7] = pointDensity(noise, cx0 + 1, cy0 + 1, cz0 + 1);
        }

        double fx = (double) (x - cx0 * 4) / 4.0;
        double fy = (double) (y - cy0 * 8) / 8.0;
        double fz = (double) (z - cz0 * 4) / 4.0;

        return lerp3(
                cachedCorners[0],
                cachedCorners[1],
                cachedCorners[2],
                cachedCorners[3],
                cachedCorners[4],
                cachedCorners[5],
                cachedCorners[6],
                cachedCorners[7],
                fx, fy, fz);
    }

    /** 网格点密度缓存：同 (cx,cy,cz) 且同 temp/hum 时共享，相邻 cell 公共角只算一次。 */
    private double pointDensity(BetaTerrainNoise noise, int cx, int cy, int cz) {
        long key = HashUtil.hashPos(cx, cy, cz);
        double v = pointCache.get(key);
        if (Double.isNaN(v)) {
            v = BetaTerrainFormula.density(cx * 4, cy * 8, cz * 4, noise, cachedTemp, cachedHum,
                    sample2D(noise, 3, cx, cz), sample2D(noise, 4, cx, cz));
            pointCache.put(key, v);
        }
        return v;
    }

    /** 通道 3/4 的 2D 采样，y=10.0 固定，是 beta func_4109_a 的 y 起点，配合 sample fy=1.0，
     * 各 octave 切片 = 10·2⁻ᵒ；按 (cx,cz) 缓存，同 XZ 网格点共享。 */
    private double sample2D(BetaTerrainNoise noise, int channel, int cx, int cz) {
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        Long2DoubleOpenHashMap cache = channel == 3 ? sample3Cache : sample4Cache;
        double v = cache.get(key);
        if (Double.isNaN(v)) {
            v = noise.sample(channel, cx, 10.0, cz);
            cache.put(key, v);
        }
        return v;
    }

    private static double lerp3(
            double c000,
            double c100,
            double c010,
            double c110,
            double c001,
            double c101,
            double c011,
            double c111,
            double fx,
            double fy,
            double fz) {
        double c00 = c000 + fx * (c100 - c000);
        double c10 = c010 + fx * (c110 - c010);
        double c01 = c001 + fx * (c101 - c001);
        double c11 = c011 + fx * (c111 - c011);
        double c0 = c00 + fy * (c10 - c00);
        double c1 = c01 + fy * (c11 - c01);
        return c0 + fz * (c1 - c0);
    }

    @Override
    public double maxValue() {
        return MAX;
    }

    @Override
    public double minValue() {
        return MIN;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return null;
    }
}
