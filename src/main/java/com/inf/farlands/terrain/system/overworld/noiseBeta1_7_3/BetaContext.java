package com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * beta 1.7.3 地形系统 biome 查询侧信道。
 *
 * BetaDensityFunction.compute 无 level/sampler 引用，biome 查询经 OverworldNoiseFiller.fill
 * 入口 set、出口 clear（ThreadLocal，genPool 多线程隔离）。修复 note #85 断链
 * （CURRENT_CHUNK 无接线方 → temp/hum 恒 0 → 湿润调制失效）。
 *
 * 仿 Noise164Context：MultiNoiseBiomeSource.getNoiseBiome 为纯函数，线程安全。
 * 查询坐标 = quart（4 block 粒度），Y 固定海平面 quart（QuartPos.fromBlock(63)=15）
 * ——与原版 BetaDensityFunction 的 chunk.getNoiseBiome(qx, 63q, qz) 语义一致。
 */
@SuppressWarnings("null")
public final class BetaContext {

    /** 海平面 63 的 quart Y（对齐原版查询）。 */
    private static final int QUART_Y = QuartPos.fromBlock(63);

    private static final ThreadLocal<Climate.Sampler> SAMPLER = new ThreadLocal<>();
    private static final ThreadLocal<BiomeSource> SOURCE = new ThreadLocal<>();

    private BetaContext() {
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
    public static Holder<Biome> biomeAt(int qx, int qz) {
        Climate.Sampler s = SAMPLER.get();
        BiomeSource b = SOURCE.get();
        if (s == null || b == null) {
            return null;
        }
        return b.getNoiseBiome(qx, QUART_Y, qz, s);
    }
}
