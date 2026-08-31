package com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4;

import com.inf.farlands.util.HashUtil;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.6.4 biome 高度场计算（initializeNoiseField L230-301 移植）。
 *
 * 两个机制：
 * 1. parabolicField：5×5 半径 2 抛物衰减核（L230-239），中心 22.36 ≈ 对角 3.49 的 6.4 倍
 * 2. biome 加权（L253-278）：权重 = 核 / (邻居.minHeight+2)，高于中心的邻居减半；
 *    minH/maxH 输出变换：maxH_out = Σ·0.9+0.1，minH_out = (Σ·4−1)/8
 * 3. 湿度 var46（L279-299）：noise6/8000 → 负取正×0.3 → ×3−2 → 分支压缩 [-0.357, 0.125]
 * 4. 去耦合 centerY/grad（block 单位，推导见方案）：
 *    centerY_block = 68 + (minH_out + var46·0.2)·34
 *    grad = 1.5 / maxH_out（原版 dY = (var47−centerY)·12/maxH_out，cell→block ÷8×12 = 1.5）
 *
 * 性能（对齐原版）：原版 getBiomesForGeneration 一次取 10×10 quart biome 网格共享
 * 全部 5×5 加权——本实现 setChunk 预取 10×10 网格，heightsAt 走缓存（每 chunk 100 次
 * biome 查询而非逐点），与原版语义一致；compute 结果按 (cellX, cellZ) 缓存去重。
 *
 * 每 (cellX, cellZ) 算一次，8 角点共享（Y 角同 XZ 同值，原版语义）。
 */
@SuppressWarnings("null")
public final class Noise164HeightField {

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

    /** 角点密度结果：centerY（block）+ grad（block⁻¹）。 */
    public record Result(double centerY, double grad) {
    }

    private final Noise164Octaves noise6;

    // ---- per-chunk biome 网格缓存（仿原版 getBiomesForGeneration 10×10）----
    // 网格覆盖 quart [chunkX*4-2, chunkX*4+7] × [chunkZ*4-2, chunkZ*4+7]，
    // 索引 = (cellX − (chunkX*4−2)) + (cellZ − (chunkZ*4−2))*10。
    private int gridChunkX = Integer.MIN_VALUE;
    private int gridChunkZ = Integer.MIN_VALUE;
    private final Holder<Biome>[] biomeGrid = new Holder[100];

    // ---- compute 结果缓存：(cellX, cellZ) → Result，去重同 XZ 角点 ----
    private final Long2ObjectOpenHashMap<Result> resultCache = new Long2ObjectOpenHashMap<>();

    public Noise164HeightField(Noise164Octaves noise6) {
        this.noise6 = noise6;
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
                this.biomeGrid[dx + dz * 10] = Noise164Context.biomeAt(baseQx + dx, baseQz + dz);
            }
        }
    }

    /**
     * 计算 (cellX, cellZ) 的高度场。cellX/cellZ 是 quart 坐标（4 block/格，1.6.4 网格单位）。
     * 结果按 (cellX, cellZ) 缓存——同 XZ 的 8 角 Y 角共享（原版语义）。
     */
    public Result compute(int cellX, int cellZ) {
        long key = HashUtil.hashPos(cellX, 0, cellZ);
        Result cached = this.resultCache.get(key);
        if (cached != null) {
            return cached;
        }

        // ---- biome 5×5 加权（L253-278）----
        float[] center = heightsAt(cellX, cellZ);
        float centerMin = center[0];
        float sumMax = 0.0F;
        float sumMin = 0.0F;
        float sumW = 0.0F;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                float[] nb = heightsAt(cellX + dx, cellZ + dy);
                float w = PARABOLIC[dx + 2 + (dy + 2) * 5] / (nb[0] + 2.0F);
                if (nb[0] > centerMin) {
                    w /= 2.0F;
                }
                sumMax += nb[1] * w;
                sumMin += nb[0] * w;
                sumW += w;
            }
        }
        float maxHOut = (sumMax / sumW) * 0.9F + 0.1F;
        float minHOut = (sumMin / sumW * 4.0F - 1.0F) / 8.0F;

        // ---- 湿度 var46（L279-299）----
        double var46 = this.noise6.sample2D(cellX, cellZ, 200.0D, 200.0D) / 8000.0D;
        if (var46 < 0.0D) {
            var46 = -var46 * 0.3D;
        }
        var46 = var46 * 3.0D - 2.0D;
        if (var46 < 0.0D) {
            var46 /= 2.0D;
            if (var46 < -1.0D) {
                var46 = -1.0D;
            }
            var46 /= 1.4D;
            var46 /= 2.0D;
        } else {
            if (var46 > 1.0D) {
                var46 = 1.0D;
            }
            var46 /= 8.0D;
        }

        // ---- 去耦合 centerY/grad（block 单位）----
        double minAdj = (double) minHOut + var46 * 0.2D;
        double centerY = 68.0D + minAdj * 34.0D;
        double grad = 1.5D / (double) maxHOut;
        Result r = new Result(centerY, grad);
        this.resultCache.put(key, r);
        return r;
    }

    /** (cellX, cellZ) 的 biome minH/maxH：从 per-chunk 网格读，缺省 → 默认 (0.1, 0.3)。 */
    private float[] heightsAt(int cellX, int cellZ) {
        int lx = cellX - (this.gridChunkX * 4 - 2);
        int lz = cellZ - (this.gridChunkZ * 4 - 2);
        Holder<Biome> biome = null;
        if (lx >= 0 && lx < 10 && lz >= 0 && lz < 10) {
            biome = this.biomeGrid[lx + lz * 10];
        }
        if (biome == null) {
            // 网格外（不应发生：5×5 邻域被 10×10 覆盖）或侧信道缺失 → 兜底直查/默认
            biome = Noise164Context.biomeAt(cellX, cellZ);
            if (biome == null) {
                return Biome164HeightMap.DEFAULT;
            }
        }
        return Biome164HeightMap.get(biome);
    }
}
