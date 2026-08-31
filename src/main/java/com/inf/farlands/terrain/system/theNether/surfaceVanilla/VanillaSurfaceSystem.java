package com.inf.farlands.terrain.system.theNether.surfaceVanilla;

import com.inf.farlands.terrain.SurfaceSystem;
import com.inf.farlands.terrain.noisefiller.AbstractNoiseFiller;
import com.inf.farlands.terrain.noisefiller.NoiseFillerContext;
import com.inf.farlands.window.WindowedChunk;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/**
 * vanilla 1.21.1 下界地表系统：settings.surfaceRule()（下界地表：地狱岩/灵魂沙/玄武岩
 * 三角洲等按群系规则）。
 *
 * <p>与主世界 VanillaSurfaceSystem 同构——vanilla SurfaceSystem（SurfaceSystemMixin
 * 全高适配）+ 维度 NoiseChunk + 自定义 NoiseBiomeSource（直查 section biome 网格，
 * 绕开 getNoiseBiome clamp）+ obfuscateSeed；差异仅在维度（NoiseFillerContext.NETHER）
 * 与 settings（下界 generator settings）。
 *
 * <p>幂等：tryApply 只替换 defaultBlock（下界 defaultBlock = netherrack）。NoiseChunk
 * 经 getOrCreateNoiseChunk 缓存复用。genPool 线程，NoiseFillerContext 维度 set/clear。
 *
 * <p>无状态：全部局部构造，单例共享安全。
 */
public final class VanillaSurfaceSystem implements SurfaceSystem {

    @Override
    public void applySurface(ServerLevel level, ChunkAccess chunk) {
        RandomState random = level.getChunkSource().randomState();
        NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        NoiseGeneratorSettings settings = gen.generatorSettings().value();
        // NoiseChunkMixin @Redirect finalDensity/aquifer 按维度分派（SURFACE 只用 initialDensity，
        // 但构造会走 @Redirect）
        NoiseFillerContext.set(NoiseFillerContext.TerrainDimension.NETHER);
        try {
            // 维度 NoiseChunk：全高 settings.noiseSettings()，经 getOrCreateNoiseChunk 缓存（vanilla 字段）
            NoiseChunk nc = chunk.getOrCreateNoiseChunk(
                    p -> AbstractNoiseFiller.createDimensionNoiseChunk(level, chunk));
            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            // 自定义 source：直查我们 section 的 4×4×4 biome 网格（quart 坐标），绕开 clamp；
            // seed = obfuscateSeed(世界种子) 复刻 vanilla WorldGenRegion fiddle 语义
            BiomeManager biomeManager = new BiomeManager(
                    (qx, qy, qz) -> biomeAt(chunk, biomes, qx, qy, qz),
                    BiomeManager.obfuscateSeed(level.getSeed()));
            WorldGenerationContext context = new WorldGenerationContext(gen, chunk);
            random.surfaceSystem().buildSurface(
                    random, biomeManager, biomes, settings.useLegacyRandomSource(),
                    context, chunk, nc, settings.surfaceRule());
        } finally {
            NoiseFillerContext.clear();
        }
    }

    /** 直查 section biome 网格（quart 局部索引 &3）；section 不存在 → the_void 兜底。 */
    private static Holder<Biome> biomeAt(ChunkAccess chunk, Registry<Biome> biomes, int qx, int qy, int qz) {
        LevelChunkSection s = ((WindowedChunk) chunk).windowedAllSections().get(qy >> 2);
        return s == null ? biomes.getHolderOrThrow(Biomes.THE_VOID)
                : s.getNoiseBiome(qx & 3, qy & 3, qz & 3);
    }
}
