package com.inf.farlands.terrain.system.overworld.surfaceVanilla;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

/**
 * vanilla 1.21.1 主世界地表系统：settings.surfaceRule() 的草皮/泥土/石头分层等。
 *
 * <p>依赖 vanilla SurfaceSystem（SurfaceSystemMixin @Overwrite 全高适配：列扫描下界
 * maxCapIter）+ 维度 NoiseChunk（preliminarySurfaceLevel——极端 Y 列 minSurfaceLevel≈
 * vanilla 地表 → abovePreliminarySurface 恒 true 自动放行）+ 自定义 NoiseBiomeSource
 * （直查我们 section 的 4×4×4 biome 网格，绕开 @Overwrite getNoiseBiome 的维度 clamp，
 * 极端 Y 正确）+ obfuscateSeed(世界种子)（复刻 vanilla WorldGenRegion fiddle 语义）。
 *
 * <p>幂等：surfaceRule.tryApply 只替换 defaultBlock（stone）——已替换的草皮不重替换，
 * 多次 fill 段后重复跑无害。NoiseChunk 经 chunk.getOrCreateNoiseChunk 缓存（vanilla 字段），
 * 首次构造后复用。genPool 线程（GenTask.execute 内），NoiseFillerContext 维度 set/clear。
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
        NoiseFillerContext.set(NoiseFillerContext.TerrainDimension.OVERWORLD);
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
