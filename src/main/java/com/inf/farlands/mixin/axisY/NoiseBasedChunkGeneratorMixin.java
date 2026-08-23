package com.inf.farlands.mixin.axisY;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    @Shadow
    protected Holder<NoiseGeneratorSettings> settings;

    @Shadow
    private Supplier<Aquifer.FluidPicker> globalFluidPicker;

    @Shadow
    public abstract NoiseChunk createNoiseChunk(ChunkAccess chunk, StructureManager manager, Blender blender,
            RandomState random);

    @Shadow
    public abstract BlockState debugPreliminarySurfaceLevel(
            NoiseChunk noisechunk, int x, int y, int z, BlockState state);

    // ---- reflection ----

    private static final Method M_CELL_WIDTH;
    private static final Method M_CELL_HEIGHT;
    private static final Method M_SELECT_CELL_YZ;
    private static final Method M_ADVANCE_CELL_X;
    private static final Method M_INIT_FIRST_CELL_X;
    private static final Method M_UPDATE_FOR_Y;
    private static final Method M_UPDATE_FOR_X;
    private static final Method M_UPDATE_FOR_Z;
    private static final Method M_GET_INTERP_STATE;
    private static final Method M_AQUIFER;
    private static final Constructor<NoiseChunk> CTOR_NOISE_CHUNK;

    static {
        try {
            M_CELL_WIDTH = NoiseChunk.class.getDeclaredMethod("cellWidth");
            M_CELL_WIDTH.setAccessible(true);
            M_CELL_HEIGHT = NoiseChunk.class.getDeclaredMethod("cellHeight");
            M_CELL_HEIGHT.setAccessible(true);
            M_SELECT_CELL_YZ = NoiseChunk.class.getDeclaredMethod("selectCellYZ", int.class, int.class);
            M_SELECT_CELL_YZ.setAccessible(true);
            M_ADVANCE_CELL_X = NoiseChunk.class.getDeclaredMethod("advanceCellX", int.class);
            M_ADVANCE_CELL_X.setAccessible(true);
            M_INIT_FIRST_CELL_X = NoiseChunk.class.getDeclaredMethod("initializeForFirstCellX");
            M_INIT_FIRST_CELL_X.setAccessible(true);
            M_UPDATE_FOR_Y = NoiseChunk.class.getDeclaredMethod("updateForY", int.class, double.class);
            M_UPDATE_FOR_Y.setAccessible(true);
            M_UPDATE_FOR_X = NoiseChunk.class.getDeclaredMethod("updateForX", int.class, double.class);
            M_UPDATE_FOR_X.setAccessible(true);
            M_UPDATE_FOR_Z = NoiseChunk.class.getDeclaredMethod("updateForZ", int.class, double.class);
            M_UPDATE_FOR_Z.setAccessible(true);
            M_GET_INTERP_STATE = NoiseChunk.class.getDeclaredMethod("getInterpolatedState");
            M_GET_INTERP_STATE.setAccessible(true);
            M_AQUIFER = NoiseChunk.class.getDeclaredMethod("aquifer");
            M_AQUIFER.setAccessible(true);
            CTOR_NOISE_CHUNK = NoiseChunk.class.getDeclaredConstructor(
                    int.class, RandomState.class, int.class, int.class,
                    NoiseSettings.class, DensityFunctions.BeardifierOrMarker.class,
                    NoiseGeneratorSettings.class, Aquifer.FluidPicker.class, Blender.class);
            CTOR_NOISE_CHUNK.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- doFill — initial generation (called by fillFromNoise) ----

    @SuppressWarnings("null")
    @Overwrite
    private ChunkAccess doFill(Blender blender, StructureManager structureManager, RandomState random,
            ChunkAccess chunk, int minCellY, int cellCountY) {
        NoiseSettings ns = this.settings.value().noiseSettings()
                .clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
        int cellH = ns.getCellHeight();
        int minSy = SectionPos.blockToSectionCoord(minCellY * cellH);
        int maxSy = SectionPos.blockToSectionCoord((minCellY + cellCountY) * cellH - 1);
        return doFillRange(blender, structureManager, random, chunk, minSy, maxSy);
    }

    // ---- on‑demand fill — 任意 Y 窗口地形生成（预留入口；当前无调用点）----

    /**
     * 把地形填入 {@code [minSection, maxSection]} 范围内的 chunk section。
     * 创建覆盖所需 block-Y 范围的临时 NoiseChunk（自定义 NoiseSettings），
     * 使 beta 地形可在任意 Y 生成。
     */
    @SuppressWarnings("null")
    @Unique
    public void fillWindowSections(ServerLevel level, ChunkAccess chunk, int minSection, int maxSection) {
        try {
            NoiseGeneratorSettings genSettings = this.settings.value();
            NoiseSettings orig = genSettings.noiseSettings();
            int noiseH = orig.noiseSizeHorizontal();
            int noiseV = orig.noiseSizeVertical();
            int cellW = 1 << noiseH; // 4
            int cellH = 1 << noiseV; // 8

            int minBlockY = minSection * 16;
            int maxBlockY = (maxSection + 1) * 16 - 1;
            int height = maxBlockY - minBlockY + 1;
            if (height % 16 != 0)
                height = ((height + 15) / 16) * 16;

            // 构建恰好覆盖窗口 block 范围的 NoiseSettings。
            // 直接用规范构造器；NoiseSettings.create() 在极端 Y 会因 DimensionType.MAX_Y
            // 校验失败。
            NoiseSettings customNS = CTOR_NOISE_SETTINGS.newInstance(
                    minBlockY, height, noiseH, noiseV);

            RandomState randomState = level.getChunkSource().randomState();
            Beardifier beardifier = Beardifier.forStructuresInChunk(
                    level.structureManager(), chunk.getPos());

            int cellCountXZ = 16 / cellW;
            NoiseChunk nc = CTOR_NOISE_CHUNK.newInstance(
                    cellCountXZ, randomState,
                    chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(),
                    customNS, beardifier, genSettings,
                    this.globalFluidPicker.get(), Blender.empty());

            int minCellY = Mth.floorDiv(customNS.minY(), cellH);
            int cellCountY = Mth.floorDiv(customNS.height(), cellH);

            doFillRangeWithNoiseChunk(nc, chunk, minSection, maxSection, minCellY, cellCountY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static final Constructor<NoiseSettings> CTOR_NOISE_SETTINGS;
    static {
        try {
            CTOR_NOISE_SETTINGS = NoiseSettings.class.getDeclaredConstructor(
                    int.class, int.class, int.class, int.class);
            CTOR_NOISE_SETTINGS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- common fill ----

    @SuppressWarnings("null")
    @Unique
    private ChunkAccess doFillRange(Blender blender, StructureManager structureManager, RandomState random,
            ChunkAccess chunk, int minSection, int maxSection) {
        try {
            NoiseChunk nc = chunk.getOrCreateNoiseChunk(
                    p -> createNoiseChunk(p, structureManager, blender, random));
            NoiseSettings ns = this.settings.value().noiseSettings()
                    .clampToHeightAccessor(chunk.getHeightAccessorForGeneration());
            int cellH = ns.getCellHeight();
            int minCellY = Mth.floorDiv(ns.minY(), cellH);
            int cellCountY = Mth.floorDiv(ns.height(), cellH);
            return doFillRangeWithNoiseChunk(nc, chunk, minSection, maxSection, minCellY, cellCountY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private ChunkAccess doFillRangeWithNoiseChunk(NoiseChunk noisechunk, ChunkAccess chunk,
            int minSection, int maxSection, int minCellY, int cellCountY) {
        try {
            Heightmap hmOcean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            Heightmap hmSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            ChunkPos cpos = chunk.getPos();
            int baseX = cpos.getMinBlockX();
            int baseZ = cpos.getMinBlockZ();
            Aquifer aquifer = (Aquifer) M_AQUIFER.invoke(noisechunk);
            M_INIT_FIRST_CELL_X.invoke(noisechunk);
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            int cw = (int) M_CELL_WIDTH.invoke(noisechunk);
            int ch = (int) M_CELL_HEIGHT.invoke(noisechunk);
            int cellsX = 16 / cw;
            int cellsZ = 16 / cw;

            // 同步 sections 数组供光照引擎使用
            int totalSections = chunk.getMaxSection() - chunk.getMinSection();
            for (int i = 0; i < totalSections; i++) {
                chunk.getSection(i);
            }

            for (int cx = 0; cx < cellsX; cx++) {
                M_ADVANCE_CELL_X.invoke(noisechunk, cx);
                for (int cz = 0; cz < cellsZ; cz++) {
                    fillCellColumn(noisechunk, chunk, minSection, maxSection, minCellY, cellCountY,
                            ch, cw, cx, cz, baseX, baseZ, hmOcean, hmSurface, aquifer, mpos, cpos);
                }
            }
            return chunk;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("null")
    private void fillCellColumn(
            NoiseChunk noisechunk,
            ChunkAccess chunk,
            int minSection,
            int maxSection,
            int minCellY,
            int cellCountY,
            int ch,
            int cw,
            int cx,
            int cz,
            int baseX,
            int baseZ,
            Heightmap hmOcean,
            Heightmap hmSurface,
            Aquifer aquifer,
            BlockPos.MutableBlockPos mpos,
            ChunkPos cpos) throws Exception {
        int cyStart = Math.max(0, minSection * 16 / ch - minCellY);
        int cyEnd = Math.min(cellCountY - 1, (maxSection * 16 + 15) / ch - minCellY);
        if (cyStart > cyEnd) {
            return;
        }

        int prevSecIdx = chunk.getSectionsCount() - 1;
        LevelChunkSection section = chunk.getSection(prevSecIdx);
        for (int cy = cyEnd; cy >= cyStart; cy--) {
            M_SELECT_CELL_YZ.invoke(noisechunk, cy, cz);
            for (int ky = ch - 1; ky >= 0; ky--) {
                int blockY = (minCellY + cy) * ch + ky;
                int ry = blockY & 15;
                int secIdx = chunk.getSectionIndex(blockY);
                if (prevSecIdx != secIdx) {
                    prevSecIdx = secIdx;
                    section = chunk.getSection(secIdx);
                }
                M_UPDATE_FOR_Y.invoke(noisechunk, blockY, (double) ky / (double) ch);
                for (int kx = 0; kx < cw; kx++) {
                    int blockX = baseX + cx * cw + kx;
                    int rx = blockX & 15;
                    M_UPDATE_FOR_X.invoke(noisechunk, blockX, (double) kx / (double) cw);
                    for (int kz = 0; kz < cw; kz++) {
                        int blockZ = baseZ + cz * cw + kz;
                        int rz = blockZ & 15;
                        M_UPDATE_FOR_Z.invoke(noisechunk, blockZ, (double) kz / (double) cw);
                        BlockState bs = (BlockState) M_GET_INTERP_STATE.invoke(noisechunk);
                        if (bs == null) {
                            bs = settings.value().defaultBlock();
                        }
                        bs = debugPreliminarySurfaceLevel(noisechunk, blockX, blockY, blockZ, bs);
                        if (bs != Blocks.AIR.defaultBlockState() && !SharedConstants.debugVoidTerrain(cpos)) {
                            section.setBlockState(rx, ry, rz, bs, false);
                            hmOcean.update(rx, blockY, rz, bs);
                            hmSurface.update(rx, blockY, rz, bs);
                            if (aquifer.shouldScheduleFluidUpdate() && !bs.getFluidState().isEmpty()) {
                                mpos.set(blockX, blockY, blockZ);
                                if (chunk instanceof ProtoChunk) {
                                    chunk.markPosForPostprocessing(mpos);
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
