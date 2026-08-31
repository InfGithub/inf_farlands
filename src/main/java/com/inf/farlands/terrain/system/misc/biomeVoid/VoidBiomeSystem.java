package com.inf.farlands.terrain.system.misc.biomeVoid;

import com.inf.farlands.terrain.BiomeSystem;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * VOID 群系系统：整个世界的 biome 恒为 the_void（虚空群系）。
 *
 * 与 VOID 地形系统（VoidNoiseSystem，全空气）配套——虚空世界的标准群系；
 * BiomeResolver 恒返回 Biomes.THE_VOID，sampler 用 Climate.empty()（resolver 不消费）。
 *
 * XZ quart 用 QuartPos.fromSection(chunkX/Z)（= chunk*4）而非
 * QuartPos.fromBlock(getMinBlockX())——getMinBlockX 被 mod 的饱和 @Overwrite 覆盖，
 * 负极端（-2.14B）饱和到 MIN_VALUE+15 → quart 差 3（12 block）布局错位。
 *
 * 无状态：biome holder 每次 fill 现拿（registry 引用随 level），纯方法多线程安全。
 */
public final class VoidBiomeSystem implements BiomeSystem {

    @Override
    public void fillBiomes(ServerLevel level, ChunkAccess chunk, int minSectionY, int maxSectionY) {
        Holder<Biome> theVoid = level.registryAccess()
                .registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.THE_VOID);
        BiomeResolver resolver = (x, y, z, sampler) -> theVoid;
        Climate.Sampler empty = Climate.empty();
        int qx = QuartPos.fromSection(chunk.getPos().x);
        int qz = QuartPos.fromSection(chunk.getPos().z);
        for (int sy = minSectionY; sy <= maxSectionY; sy++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
            section.fillBiomesFromNoise(resolver, empty, qx, QuartPos.fromSection(sy), qz);
        }
    }
}
