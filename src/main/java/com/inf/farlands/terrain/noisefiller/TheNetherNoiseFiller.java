package com.inf.farlands.terrain.noisefiller;

import com.inf.farlands.terrain.TerrainSystem;
import com.inf.farlands.terrain.system.NoiseSystemRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * 下界地形填充器：维度系统 = Config.netherTerrainSystem（默认 VANILLA_THE_NETHER，
 * vanilla 链裸地形）。逻辑全部继承 {@link AbstractNoiseFiller}；无维度 biome
 * 侧信道（下界 vanilla 链无 biome 高度场）。biome 阶段由 BiomeFiller 独立负责。
 */
@SuppressWarnings("null")
public final class TheNetherNoiseFiller extends AbstractNoiseFiller {

    private TheNetherNoiseFiller(NoiseGeneratorSettings settings) {
        super(settings);
    }

    /** 从 generator 构造，由 GenQueue 惰性缓存持有。 */
    public static TheNetherNoiseFiller of(ServerLevel level) {
        NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        return new TheNetherNoiseFiller(gen.generatorSettings().value());
    }

    @Override
    protected NoiseFillerContext.TerrainDimension dimension() {
        return NoiseFillerContext.TerrainDimension.NETHER;
    }

    @Override
    protected TerrainSystem terrainSystem() {
        return NoiseSystemRegistry.getTheNether();
    }
}
