package com.inf.farlands.terrain.system.overworld.carverVanilla;

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
 * vanilla 1.21.1 主世界雕刻系统：biome json 的 carver 列表（CAVE/CAVE_EXTRA_UNDERGROUND/
 * CANYON）+ WorldCarver 体系，目标 chunk 为中心 ±8 chunk 起点网格。
 *
 * <p>移植自 {@code NoiseBasedChunkGenerator.applyCarvers}（L273-321）：
 * - 目标 chunk 为中心遍历 17×17 起点（289 次调用）；每起点 biomeSource 查 biome → 该
 *   biome 的 carver 列表（不依赖起点 chunk 数据——起点未生成也可查询）
 * - setLargeFeatureSeed(世界seed + carver序号, 起点cx, 起点cz) 确定性随机 → isStartChunk
 *   概率过滤 → ConfiguredWorldCarver.carve（carvingMask 去重，只雕目标 chunk）
 * - CarvingContext 用维度全高 NoiseChunk（aquifer 覆盖 carver 带，同 SURFACE 模式）
 *
 * <p>Y 有界：carver 起点 Y 由 biome json 配置（HeightProvider，锚定维度带），极端 Y 天然
 * 不雕。carve 只替换 config.replaceable（stone）；替换方块由 aquifer.computeSubstance
 * 决定（空气/水/岩浆，y≤lavaLevel 填岩浆）。
 *
 * <p>无状态：全部局部构造（CarvingContext/NoiseChunk/WorldgenRandom），单例共享安全。
 */
public final class VanillaCarverSystem implements CarverSystem {

    /** 起点网格半径（vanilla applyCarvers 硬编码 8）。 */
    private static final int GRID_RADIUS = 8;

    @Override
    public void applyCarvers(ServerLevel level, ChunkAccess chunk) {
        RandomState random = level.getChunkSource().randomState();
        NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        NoiseGeneratorSettings settings = gen.generatorSettings().value();
        // NoiseChunkMixin @Redirect finalDensity/aquifer 按维度分派（carver 只用 aquifer，
        // 但维度全高 NoiseChunk 构造会走 @Redirect）
        NoiseFillerContext.set(NoiseFillerContext.TerrainDimension.OVERWORLD);
        try {
            // 维度全高 NoiseChunk：aquifer 网格覆盖 carver 带（fill 的窗口段 NoiseChunk 不覆盖）
            NoiseChunk nc = chunk.getOrCreateNoiseChunk(
                    p -> AbstractNoiseFiller.createDimensionNoiseChunk(level, chunk));
            Aquifer aquifer = nc.aquifer();
            CarvingContext carvingContext = new CarvingContext(
                    gen, level.registryAccess(), chunk.getHeightAccessorForGeneration(),
                    nc, random, settings.surfaceRule());
            CarvingMask carvingMask = ((CarvingMaskStorage) chunk).getOrCreateCarvingMask(GenerationStep.Carving.AIR);

            // biomeAccessor：直接 biomeSource 查询（carveBlock 的表面 dirt→草皮 topMaterial 用）；
            // 不依赖 chunk section biome（起点可能未生成）
            Function<BlockPos, Holder<Biome>> biomeAccessor = pos -> gen.getBiomeSource()
                    .getNoiseBiome(QuartPos.fromBlock(pos.getX()), QuartPos.fromBlock(pos.getY()),
                            QuartPos.fromBlock(pos.getZ()), random.sampler());

            ChunkPos target = chunk.getPos();
            WorldgenRandom worldgenrandom = new WorldgenRandom(
                    new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
            for (int j = -GRID_RADIUS; j <= GRID_RADIUS; j++) {
                for (int k = -GRID_RADIUS; k <= GRID_RADIUS; k++) {
                    ChunkPos start = new ChunkPos(target.x + j, target.z + k);
                    // quart 用 fromSection（= start*4）而非 fromBlock(getMinBlockX())：
                    // getMinBlockX 被 mod 饱和 @Overwrite 覆盖，边界 chunk 会错位（BiomeSystem 同款防御）
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
