package com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * 1.6.4 地形系统 biome 查询侧信道。
 *
 * DensityFunction.compute 无 level/sampler 引用，biome 查询经 OverworldNoiseFiller.fill
 * 入口 set、出口 clear（ThreadLocal，genPool 多线程安全）。仿 BetaTerrain
 * getCurrentChunk 模式；MultiNoiseBiomeSource.getNoiseBiome 为纯函数，线程安全。
 *
 * biome 采样坐标 = quart（4 block 粒度，对齐 1.6.4 genBiomes），quart Y 固定 0
 * （vanilla 自身 L303 同款：getNoiseBiome(qx, 0, qz, sampler)）。
 */
@SuppressWarnings("null")
public final class Noise164Context {

    private static final ThreadLocal<Climate.Sampler> SAMPLER = new ThreadLocal<>();
    private static final ThreadLocal<BiomeSource> SOURCE = new ThreadLocal<>();

    private Noise164Context() {
    }

    /** OverworldNoiseFiller.fill 入口调用（genPool 线程）。 */
    public static void set(Climate.Sampler sampler, BiomeSource source) {
        SAMPLER.set(sampler);
        SOURCE.set(source);
    }

    /** OverworldNoiseFiller.fill 出口（finally）调用。 */
    public static void clear() {
        SAMPLER.remove();
        SOURCE.remove();
    }

    /** quart 坐标查 biome；侧信道未设置时返回 null（调用方按默认 biome 处理）。 */
    public static Holder<Biome> biomeAt(int qx, int qz) {
        Climate.Sampler s = SAMPLER.get();
        BiomeSource b = SOURCE.get();
        if (s == null || b == null) {
            return null;
        }
        return b.getNoiseBiome(qx, 0, qz, s);
    }
}
