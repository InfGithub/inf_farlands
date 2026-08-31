package com.inf.farlands.terrain.noisefiller;

import com.inf.farlands.terrain.TerrainSystem;
import com.inf.farlands.terrain.system.NoiseSystemRegistry;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;

/**
 * 末地地形填充器：维度系统 = Config.endTerrainSystem（默认 VANILLA_THE_END，
 * vanilla 链裸地形）。逻辑全部继承 {@link AbstractNoiseFiller}；无维度 biome
 * 侧信道（末地 vanilla 链无 biome 高度场）。biome 阶段由 BiomeFiller 独立负责。
 */
@SuppressWarnings("null")
public final class TheEndNoiseFiller extends AbstractNoiseFiller {

    private TheEndNoiseFiller(NoiseGeneratorSettings settings) {
        super(settings);
    }

    /** 从 generator 构造，由 GenQueue 惰性缓存持有。 */
    public static TheEndNoiseFiller of(ServerLevel level) {
        NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
        return new TheEndNoiseFiller(gen.generatorSettings().value());
    }

    @Override
    protected NoiseFillerContext.TerrainDimension dimension() {
        return NoiseFillerContext.TerrainDimension.END;
    }

    @Override
    protected TerrainSystem terrainSystem() {
        return NoiseSystemRegistry.getTheEnd();
    }
}
