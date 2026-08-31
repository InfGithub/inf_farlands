package com.inf.farlands.terrain.system.overworld.noiseVanilla1_16_5;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * 1.16.5 地形系统 biome 查询侧信道。
 *
 * DensityFunction.compute 无 level/sampler 引用，biome 查询经 OverworldNoiseFiller.fill
 * 入口 set、出口 clear（ThreadLocal，genPool 多线程安全）。仿 Noise164Context。
 *
 * 关键语义：1.16.5 fillNoiseColumn 在 seaLevel(63) 处查 biome 高度场
 * （getNoiseBiome(cellX, 63, cellZ)，内部 useY=false 忽略 Y → 2D）。现代 1.21.1
 * MultiNoiseBiomeSource 是 3D（depth 通道随 Y 变化），Y=0 会落到深地下 → 洞穴
 * biome。必须传 seaLevel 的 quart：QuartPos.fromBlock(63) = 15（地表附近，
 * depth 通道低 → 地表 biome），与 1.16.5 海平面高度场语义对齐。
 *
 * cellX/cellZ 即 1.16.5 的 quart 网格坐标（每格 4 block，chunkCountX=4）。
 */
@SuppressWarnings("null")
public final class Noise1165Context {

    /** 1.16.5 主世界 seaLevel=63 的 quart Y：QuartPos.fromBlock(63) = 15。 */
    private static final int QUART_Y = QuartPos.fromBlock(63);

    private static final ThreadLocal<Climate.Sampler> SAMPLER = new ThreadLocal<>();
    private static final ThreadLocal<BiomeSource> SOURCE = new ThreadLocal<>();

    private Noise1165Context() {
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

    /** quart 坐标查地表 biome（Y 固定海平面 quart）；侧信道未设置时返回 null。 */
    public static Holder<Biome> biomeAt(int cellX, int cellZ) {
        Climate.Sampler s = SAMPLER.get();
        BiomeSource b = SOURCE.get();
        if (s == null || b == null) {
            return null;
        }
        return b.getNoiseBiome(cellX, QUART_Y, cellZ, s);
    }
}
