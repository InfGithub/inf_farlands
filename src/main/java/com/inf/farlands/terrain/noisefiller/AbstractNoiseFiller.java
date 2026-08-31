package com.inf.farlands.terrain.noisefiller;

import com.inf.farlands.mixin.noise.NoiseChunkInvoker;
import com.inf.farlands.terrain.BlockSystem;
import com.inf.farlands.terrain.TerrainSystem;

import java.lang.reflect.Constructor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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

/**
 * 维度地形填充器抽象基类：按维度（主世界/下界/末地）填充一段 section。
 *
 * 三个实现共享同一 NoiseChunk 管线（1 section = 1 NoiseChunk），差异仅在：
 * 维度 Context 侧信道（setContext/clearContext）、维度系统分派（terrainSystem）、
 * 维度枚举（dimension）。基类持有全部公共逻辑——多维度 mod 兼容的接入面：
 * 新维度只需子类实现三个抽象点，窗口/管线/光照/fsa 全链路自动共享。
 *
 * 与原版 NoiseBasedChunkGenerator.doFill 的差异点：
 * - Y 循环裁剪到 [minSection, maxSection]（按需生成窗口段，非全量 cellCountY）
 * - 每 X cell 处理完后 swapSlices()（原版 L422）+ 全部结束后 stopInterpolation()
 *   （原版 L425）——vanilla 链（带 Interpolated marker 的函数）依赖 slice 双缓冲
 *   X 插值，缺失会导致第 2 个 X cell 起 X 方向错位；纯函数系统（beta/1.6.4/1.16.5
 *   自定义 DensityFunction）不用 slice，无影响。
 *
 * 并发约束：fillCellColumn 更新 chunk 共享 heightmap，BitStorage 非线程安全，
 * 同 chunk 的 fill 必须串行，由调用方保证；不同 chunk 并行。
 */
@SuppressWarnings("null")
public abstract class AbstractNoiseFiller implements NoiseFiller {

    protected final NoiseGeneratorSettings settings;
    protected final Aquifer.FluidPicker fluidPicker;

    protected AbstractNoiseFiller(NoiseGeneratorSettings settings) {
        this.settings = settings;
        this.fluidPicker = createFluidPicker(settings);
    }

    /** 当前填充维度：NoiseChunkMixin 的 finalDensity/aquifer 注入按此分派。 */
    protected abstract NoiseFillerContext.TerrainDimension dimension();

    /** 当前维度地形系统：finalDensity/aquifer/fluidPicker/BlockSystem 分派来源。 */
    protected abstract TerrainSystem terrainSystem();

    /** 维度 biome 查询侧信道设置（DensityFunction.compute 无 level 引用）。
     * 默认空；主世界 override 设 Beta/164/1165 三个 Context。 */
    protected void setContext(RandomState randomState, ServerLevel level) {
    }

    /** 与 {@link #setContext} 对称的清理，fill 出口 finally 调用。 */
    protected void clearContext() {
    }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus water = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? lava : water;
    }

    // ---- 反射句柄：构造器保留，方法调用改 NoiseChunkInvoker 直调 ----

    private static final Constructor<NoiseChunk> CTOR_NOISE_CHUNK;
    private static final Constructor<NoiseSettings> CTOR_NOISE_SETTINGS;
    static {
        try {
            CTOR_NOISE_CHUNK = NoiseChunk.class.getDeclaredConstructor(
                    int.class, RandomState.class, int.class, int.class,
                    NoiseSettings.class, DensityFunctions.BeardifierOrMarker.class,
                    NoiseGeneratorSettings.class, Aquifer.FluidPicker.class, Blender.class);
            CTOR_NOISE_SETTINGS = NoiseSettings.class.getDeclaredConstructor(
                    int.class, int.class, int.class, int.class);
            CTOR_NOISE_SETTINGS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SURFACE 阶段：构建维度范围 NoiseChunk（全高 settings.noiseSettings()，非 fill 的窗口段）。
     * 与 fill 同款构造模式（CTOR_NOISE_CHUNK），fluidPicker 用默认（lava/water）——SURFACE 只用
     * initialDensity（preliminarySurfaceLevel），fluidPicker/aquifer 不影响。
     * 调用方须已 set NoiseFillerContext（NoiseChunkMixin @Redirect finalDensity/aquifer 按维度分派）。
     */
    public static NoiseChunk createDimensionNoiseChunk(ServerLevel level, ChunkAccess chunk) {
        try {
            NoiseBasedChunkGenerator gen = (NoiseBasedChunkGenerator) level.getChunkSource().getGenerator();
            NoiseGeneratorSettings settings = gen.generatorSettings().value();
            NoiseSettings orig = settings.noiseSettings();
            RandomState randomState = level.getChunkSource().randomState();
            Beardifier beardifier = Beardifier.forStructuresInChunk(level.structureManager(), chunk.getPos());
            int cellW = QuartPos.toBlock(orig.noiseSizeHorizontal());
            int cellCountXZ = 16 / cellW;
            return CTOR_NOISE_CHUNK.newInstance(
                    cellCountXZ, randomState,
                    chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(),
                    orig, beardifier, settings,
                    createFluidPicker(settings), Blender.empty());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 填一段 section 的地形，1 段 = 1 NoiseChunk。调用方保证同 chunk 串行。 */
    @Override
    public void fill(ServerLevel level, ChunkAccess chunk, int minSectionY, int maxSectionY) {
        RandomState randomState = level.getChunkSource().randomState();
        // 维度侧信道：NoiseChunkMixin 按此分派 finalDensity/aquifer 到对应维度系统
        NoiseFillerContext.set(dimension());
        // 维度 biome 侧信道：DensityFunction.compute 无 level 引用，fill 入口设、出口清
        setContext(randomState, level);
        try {
            NoiseSettings orig = settings.noiseSettings();            // 网格分派：NoiseSystem 可自定义 noiseSize（天域 8×4 cell），null = 维度 settings 默认
            int[] ns = null;
            if (terrainSystem() instanceof com.inf.farlands.terrain.NoiseSystem n) {
                ns = n.noiseSize();
            }
            int noiseH = ns != null ? ns[0] : orig.noiseSizeHorizontal();
            int noiseV = ns != null ? ns[1] : orig.noiseSizeVertical();
            int cellW = QuartPos.toBlock(noiseH);
            int cellH = QuartPos.toBlock(noiseV);

            // 构建恰好覆盖该段 section 的 NoiseSettings，避免 NoiseSettings.create 的 MAX_Y 校验
            NoiseSettings customNS = CTOR_NOISE_SETTINGS.newInstance(
                    minSectionY * 16, (maxSectionY - minSectionY + 1) * 16, noiseH, noiseV);

            Beardifier beardifier = Beardifier.forStructuresInChunk(level.structureManager(), chunk.getPos());

            int cellCountXZ = 16 / cellW;
            // fluidPicker 按维度系统取：VOID/空气系统用空气 picker，null 用本类默认
            Aquifer.FluidPicker picker = terrainSystem().createFluidPicker(settings);
            if (picker == null) {
                picker = this.fluidPicker;
            }
            NoiseChunk nc = CTOR_NOISE_CHUNK.newInstance(
                    cellCountXZ, randomState,
                    chunk.getPos().getMinBlockX(), chunk.getPos().getMinBlockZ(),
                    customNS, beardifier, settings,
                    picker, Blender.empty());

            int minCellY = Mth.floorDiv(customNS.minY(), cellH);
            int cellCountY = Mth.floorDiv(customNS.height(), cellH);

            NoiseChunkInvoker inv = (NoiseChunkInvoker) nc;
            doFillRangeWithNoiseChunk(inv, chunk, minSectionY, maxSectionY, minCellY, cellCountY);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            clearContext();
            NoiseFillerContext.clear();
        }
    }

    // ---- 填充循环：对齐原版 NoiseBasedChunkGenerator.doFill（L353-427）----

    protected void doFillRangeWithNoiseChunk(NoiseChunkInvoker inv, ChunkAccess chunk,
            int minSection, int maxSection, int minCellY, int cellCountY) {
        try {
            Heightmap hmOcean = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            Heightmap hmSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
            ChunkPos cpos = chunk.getPos();
            int baseX = cpos.getMinBlockX();
            int baseZ = cpos.getMinBlockZ();
            Aquifer aquifer = inv.farlands$aquifer();
            inv.farlands$initializeForFirstCellX();
            BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
            int cw = inv.farlands$cellWidth();
            int ch = inv.farlands$cellHeight();
            int cellsX = 16 / cw;
            int cellsZ = 16 / cw;

            for (int cx = 0; cx < cellsX; cx++) {
                inv.farlands$advanceCellX(cx);
                for (int cz = 0; cz < cellsZ; cz++) {
                    fillCellColumn(inv, chunk, minSection, maxSection, minCellY, cellCountY,
                            ch, cw, cx, cz, baseX, baseZ, hmOcean, hmSurface, aquifer, mpos, cpos);
                }
                // 原版 doFill L422：每个 X cell 处理完后交换 slice0/slice1——
                // vanilla 链（Interpolated marker）的 X 插值依赖双缓冲推进，缺失则错位一列
                ((NoiseChunk) inv).swapSlices();
            }
            // 原版 doFill L425：插值结束标志
            ((NoiseChunk) inv).stopInterpolation();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void fillCellColumn(
            NoiseChunkInvoker inv,
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
            inv.farlands$selectCellYZ(cy, cz);
            for (int ky = ch - 1; ky >= 0; ky--) {
                int blockY = (minCellY + cy) * ch + ky;
                int ry = blockY & 15;
                int secIdx = chunk.getSectionIndex(blockY);
                if (prevSecIdx != secIdx) {
                    prevSecIdx = secIdx;
                    section = chunk.getSection(secIdx);
                }
                inv.farlands$updateForY(blockY, (double) ky / (double) ch);
                for (int kx = 0; kx < cw; kx++) {
                    int blockX = baseX + cx * cw + kx;
                    int rx = blockX & 15;
                    inv.farlands$updateForX(blockX, (double) kx / (double) cw);
                    for (int kz = 0; kz < cw; kz++) {
                        int blockZ = baseZ + cz * cw + kz;
                        int rz = blockZ & 15;
                        inv.farlands$updateForZ(blockZ, (double) kz / (double) cw);
                        // 块级填充优先（BlockSystem 逐块几何），否则走密度链（NoiseSystem）
                        TerrainSystem sys = terrainSystem();
                        BlockState bs = sys instanceof BlockSystem b ? b.fillBlock(blockX, blockY, blockZ) : null;
                        if (bs == null) {
                            bs = inv.farlands$getInterpolatedState();
                            if (bs == null) {
                                bs = settings.defaultBlock();
                            }
                        }
                        if (bs != Blocks.AIR.defaultBlockState() && !net.minecraft.SharedConstants.debugVoidTerrain(cpos)) {
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
