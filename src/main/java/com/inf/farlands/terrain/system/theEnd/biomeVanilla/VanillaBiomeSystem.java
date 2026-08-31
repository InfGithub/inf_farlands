package com.inf.farlands.terrain.system.theEnd.biomeVanilla;

import com.inf.farlands.terrain.BiomeSystem;

import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * vanilla 1.21.1 末地群系系统：TheEndBiomeSource 原版布局。
 *
 * 末地不用 MultiNoise 参数表：TheEndBiomeSource 按距中心 XZ 圆分区（≤4096 → the_end），
 * 外围按 sampler.erosion()（= 末地 router 的 cache2d(endIslands)，NoiseRouterData end()）
 * 分 highlands/midlands/barrens/islands——填 4×4×4 时各 quart 点独立判定。
 *
 * XZ quart 用 QuartPos.fromSection(chunkX/Z)（= chunk*4）而非
 * QuartPos.fromBlock(getMinBlockX())——getMinBlockX 被 mod 的饱和 @Overwrite 覆盖，
 * 负极端（-2.14B）饱和到 MIN_VALUE+15 → quart 差 3（12 block）布局错位。
 *
 * 无状态：sampler/biomeSource 每次 fill 现拿（防维度/世界切换陈旧），纯方法多线程安全。
 */
public final class VanillaBiomeSystem implements BiomeSystem {

    @Override
    public void fillBiomes(ServerLevel level, ChunkAccess chunk, int minSectionY, int maxSectionY) {
        ChunkGenerator gen = level.getChunkSource().getGenerator();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        int qx = QuartPos.fromSection(chunk.getPos().x);
        int qz = QuartPos.fromSection(chunk.getPos().z);
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            section.fillBiomesFromNoise(gen.getBiomeSource(), sampler, qx, QuartPos.fromSection(sy), qz);
        }
    }
}
