package com.inf.farlands.terrain.system.theNether.carverVanilla;

import com.inf.farlands.CarvingMaskStorage;
import com.inf.farlands.terrain.CarverSystem;
import com.inf.farlands.terrain.noisefiller.AbstractNoiseFiller;
import com.inf.farlands.terrain.noisefiller.NoiseFillerContext;

import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;

/**
 * vanilla 1.21.1 下界雕刻系统：biome json 的 NETHER_CAVE（NetherWorldCarver——y≤minGenY+31
 * 填岩浆、其余 CAVE_AIR，不走 aquifer 替换）。
 *
 * <p>与主世界 VanillaCarverSystem 同构——17×17 起点网格 + biomeSource 查 carver 列表 +
 * setLargeFeatureSeed + 维度全高 NoiseChunk（aquifer）；差异仅在维度
 * （NoiseFillerContext.NETHER）与 biome 数据（下界 NETHER_CAVE）。
 *
 * <p>Y 有界：NETHER_CAVE 起点 Y 配置锚定下界维度带。无状态：全部局部构造，单例共享安全。
 */
public final class VanillaCarverSystem implements CarverSystem {

    /** 起点网格半径（vanilla applyCarvers 硬编码 8）。 */
    private static final int GRID_RADIUS = 8;

    @Override
    public void applyCarvers(ServerLevel level, ChunkAccess chunk) {
        RandomState random = level.getChunkSource().randomState();
        NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        NoiseGeneratorSettings settings = gen.generatorSettings().value();
        // NoiseChunkMixin @Redirect finalDensity/aquifer 按维度分派
        NoiseFillerContext.set(NoiseFillerContext.TerrainDimension.NETHER);
        try {
            // 维度全高 NoiseChunk：aquifer 网格覆盖 carver 带
            NoiseChunk nc = chunk.getOrCreateNoiseChunk(
                    p -> AbstractNoiseFiller.createDimensionNoiseChunk(level, chunk));
            Aquifer aquifer = nc.aquifer();
            CarvingContext carvingContext = new CarvingContext(
                    gen, level.registryAccess(), chunk.getHeightAccessorForGeneration(),
                    nc, random, settings.surfaceRule());
            CarvingMask carvingMask = ((CarvingMaskStorage) chunk).getOrCreateCarvingMask(GenerationStep.Carving.AIR);

            // biomeAccessor：直接 biomeSource 查询（carveBlock 表面 dirt→草皮用；下界表面规则少触发）
            Function<BlockPos, Holder<Biome>> biomeAccessor = pos -> gen.getBiomeSource()
                    .getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                            QuartPos.fromBlock(pos.getZ()), random.sampler());

            ChunkPos target = chunk.getPos();
            WorldgenRandom worldgenrandom = new WorldgenRandom(
                    new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            for (int j = -GRID_RADIUS; j <= GRID_RADIUS; j++) {
                for (int k = -GRID_RADIUS; k <= GRID_RADIUS; k++) {
                    ChunkPos start = new ChunkPos(target.x + j, target.z + k);
                    // quart 用 fromSection（= start*4）——getMinBlockX 被 mod 饱和 @Overwrite 覆盖
                    Holder<Biome> biome = gen.getBiomeSource().getNoiseBiome(
                            QuartPos.fromSection(start.x), 0, QuartPos.fromSection(start.z), random.sampler());
                    BiomeGenerationSettings biomeGen = gen.getBiomeGenerationSettings(biome);
                    int l = 0;
                    for (Holder<ConfiguredWorldCarver<?>> holder : biomeGen.getCarvers(GenerationStep.Carving.AIR)) {
                        ConfiguredWorldCarver<?> carver = holder.value();
                        worldgenrandom.setLargeFeatureSeed(level.getSeed() + l, start.x, start.z);
                        if (carver.isStartChunk(worldgenrandom)) {
                            carver.carve(carvingContext, chunk, biomeAccessor, worldgenrandom,
                                    aquifer, start, carvingMask);
                        }
                        l++;
                    }
                }
            }
        } finally {
            NoiseFillerContext.clear();
        }
    }
}
