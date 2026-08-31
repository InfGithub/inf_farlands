package com.inf.farlands.terrain.system.overworld.noiseVanilla1_16_5;

import com.inf.farlands.util.HashUtil;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.PerlinNoise;

/**
 * 1.16.5 biome 高度场计算（fillNoiseColumn L205-253 移植）。
 *
 * 两个机制：
 * 1. 5×5 抛物核加权（L228）：w = 核 / (邻居depth+2)，高于中心 depth 的邻居减半
 * 2. depth/scale → baseDensity/ySlope（L237-240）：
 *    baseDensity = (avgDepth×0.5 − 0.125) × 0.265625
 *    ySlope      = 96.0 / (avgScale×0.9 + 0.1)
 * 3. randomDensityOffset（L253）：depthNoise.getValue(x×200, 10, z×200, 1, 0, true)
 *    列级随机值，列内所有 cellY 共享
 *
 * 与 1.16.5 原版差异：
 * - amplified 分支（depth>0 → ×2、scale ×4）不实现——主世界标准 amplified=false，
 *   NoiseGeneratorSettings.overworld 硬编码；未来接入 amplified 世界类型时补
 * - biome 查询经 Noise1165Context（Y=seaLevel quart），depth/scale 从 Biome1165HeightMap 查表
 *
 * compute 结果按 (cellX, cellZ) 缓存——同 XZ 的 8 角点 Y 角共享（1.16.5 原版语义：
 * 高度场在列循环外算一次，列内所有 cellY 共享 baseDensity/ySlope/rdo）。
 */
@SuppressWarnings("null")
public final class Noise1165HeightField {

    /** 5×5 抛物核：10/√(dx²+dz²+0.2)，索引 (dx+2)+(dz+2)*5。 */
    private static final float[] PARABOLIC = new float[25];

    static {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                PARABOLIC[dx + 2 + (dz + 2) * 5] =
                        (float) (10.0F / Math.sqrt((double) (dx * dx + dz * dz) + 0.2F));
            }
        }
    }

    /** 角点密度结果：baseDensity（block 单位密度偏移）+ ySlope（block⁻¹）+ rdo（列级随机）。 */
    public record Result(double baseDensity, double ySlope, double randomDensityOffset) {
    }

    private final PerlinNoise depthNoise;

    // ---- per-chunk biome 网格缓存（仿 1.16.5 原版，覆盖 5×5 邻域）----
    // 网格覆盖 quart [chunkX*4-2, chunkX*4+7] × [chunkZ*4-2, chunkZ*4+7]，
    // 索引 = (cellX − (chunkX*4−2)) + (cellZ − (chunkZ*4−2))*10。
    private int gridChunkX = Integer.MIN_VALUE;
    private int gridChunkZ = Integer.MIN_VALUE;
    private final Holder<Biome>[] biomeGrid = new Holder[100];

    // ---- compute 结果缓存：(cellX, cellZ) → Result，去重同 XZ 角点 ----
    private final Long2ObjectOpenHashMap<Result> resultCache = new Long2ObjectOpenHashMap<>();

    public Noise1165HeightField(PerlinNoise depthNoise) {
        this.depthNoise = depthNoise;
        this.resultCache.defaultReturnValue(null);
    }

    /** chunk 变化时预取 10×10 biome 网格（DensityFunction cell 变化时调用，per-NoiseChunk 单 chunk）。 */
    public void setChunk(int chunkX, int chunkZ) {
        if (chunkX == this.gridChunkX && chunkZ == this.gridChunkZ) {
            return;
        }
        this.gridChunkX = chunkX;
        this.gridChunkZ = chunkZ;
        int baseQx = chunkX * 4 - 2;
        int baseQz = chunkZ * 4 - 2;
        for (int dz = 0; dz < 10; dz++) {
            for (int dx = 0; dx < 10; dx++) {
                this.biomeGrid[dx + dz * 10] = Noise1165Context.biomeAt(baseQx + dx, baseQz + dz);
            }
        }
    }

    /**
     * 计算 (cellX, cellZ) 的高度场。cellX/cellZ 是 quart 坐标（4 block/格）。
     * 结果按 (cellX, cellZ) 缓存——同 XZ 的 8 角点共享（1.16.5 列内共享语义）。
     */
    public Result compute(int cellX, int cellZ) {
        long key = HashUtil.hashPos(cellX, 0, cellZ);
        Result cached = this.resultCache.get(key);
        if (cached != null) {
            return cached;
        }

        // ---- biome 5×5 加权（fillNoiseColumn L205-236）----
        float[] center = heightsAt(cellX, cellZ);
        float centerDepth = center[0];
        float sumScale = 0.0F;
        float sumDepth = 0.0F;
        float sumW = 0.0F;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                float[] nb = heightsAt(cellX + dx, cellZ + dy);
                float w = PARABOLIC[dx + 2 + (dy + 2) * 5] / (nb[0] + 2.0F);
                if (nb[0] > centerDepth) {
                    w /= 2.0F;
                }
                sumScale += nb[1] * w;
                sumDepth += nb[0] * w;
                sumW += w;
            }
        }
        float avgDepth = sumDepth / sumW;
        float avgScale = sumScale / sumW;

        // ---- depth/scale → baseDensity/ySlope（L237-240）----
        double baseDensity = (double) (avgDepth * 0.5F - 0.125F) * 0.265625D;
        double ySlope = 96.0D / (double) (avgScale * 0.9F + 0.1F);

        // ---- randomDensityOffset（L253）：列级随机，depthNoise ----
        double rdo = getRandomDensity(cellX, cellZ);

        Result r = new Result(baseDensity, ySlope, rdo);
        this.resultCache.put(key, r);
        return r;
    }

    /** getRandomDensity（L282-293）：depthNoise.getValue(x×200, 10, z×200, 1, 0, true)。 */
    private double getRandomDensity(int cellX, int cellZ) {
        double d = this.depthNoise.getValue(
                (double) cellX * 200.0D, 10.0D, (double) cellZ * 200.0D, 1.0D, 0.0D, true);
        double v;
        if (d < 0.0D) {
            v = -d * 0.3D;
        } else {
            v = d;
        }
        double w = v * 24.575625D - 2.0D;
        return w < 0.0D ? w * 0.009486607142857142D : Math.min(w, 1.0D) * 0.006640625D;
    }

    /** (cellX, cellZ) 的 biome depth/scale：从 per-chunk 网格读，缺省 → 默认 (0.1, 0.3)。 */
    private float[] heightsAt(int cellX, int cellZ) {
        int lx = cellX - (this.gridChunkX * 4 - 2);
        int lz = cellZ - (this.gridChunkZ * 4 - 2);
        Holder<Biome> biome = null;
        if (lx >= 0 && lx < 10 && lz >= 0 && lz < 10) {
            biome = this.biomeGrid[lx + lz * 10];
        }
        if (biome == null) {
            // 网格外（不应发生：5×5 邻域被 10×10 覆盖）或侧信道缺失 → 兜底直查/默认
            biome = Noise1165Context.biomeAt(cellX, cellZ);
            if (biome == null) {
                return Biome1165HeightMap.DEFAULT;
            }
        }
        return Biome1165HeightMap.get(biome);
    }
}
