package com.inf.farlands.terrain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 群系系统：决定 section 的 biome 数据来源。
 *
 * 与地形系统（{@link TerrainSystem}）正交——biome 是独立阶段（vanilla BIOMES 先于 NOISE，
 * 不依赖地形内容）。由 {@link com.inf.farlands.terrain.system.BiomeSystemRegistry} 按
 * Config 选择实现并持有单例。实现必须无状态、纯方法——fillBiomes 跑 genPool 多线程，
 * 实例被多个 fill 任务共享。
 */
public interface BiomeSystem {

    /** 填 [minSectionY, maxSectionY] 段内每个 section 的 4×4×4 biome 网格（genPool 线程）。 */
    void fillBiomes(ServerLevel level, ChunkAccess chunk, int minSectionY, int maxSectionY);
}
