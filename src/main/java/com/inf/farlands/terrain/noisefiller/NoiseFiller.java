package com.inf.farlands.terrain.noisefiller;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 维度地形填充器公共接口：按维度（主世界/下界/末地）填充一段 section。
 *
 * 三个实现（Overworld/Nether/TheEndNoiseFiller）共享同一 NoiseChunk 管线（1 section =
 * 1 NoiseChunk），差异在维度系统（finalDensity/fluidPicker/aquifer）与维度 Context。
 * GenQueue.filler 按 level.dimension() 分派。
 */
public interface NoiseFiller {

    /** 填一段 section，调用方保证同 chunk 串行。 */
    void fill(ServerLevel level, ChunkAccess chunk, int minSectionY, int maxSectionY);
}
