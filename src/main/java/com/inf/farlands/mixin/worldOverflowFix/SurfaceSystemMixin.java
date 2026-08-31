package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;
import com.inf.farlands.window.WindowedChunk;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BlockColumn;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 全高适配：SurfaceSystem.buildSurface 原版列扫描下界 = chunk.getMinBuildHeight()（维度 -64），
 * 极端 Y 窗口 section 不做地表。本 @Overwrite 复制原逻辑（1.21.1 L66-163）+ 2 处改动：
 * 1. 列扫描下界 = max(chunk.getMinBuildHeight(), 地表+1 − Config.maxCapIter)——对齐
 *    findLowestSourceBelow 先例（#44.6）；正常 Y（地表−512 < −64）恒取 −64 = vanilla 原行为；
 *    极端 Y 有界 512 格。
 * 2. BlockColumn.setBlock 范围 = 扫描区间 [l2, l1]（原 [−64,320) 会挡掉极端 Y 写入）。
 * 其余原样：getBlock、stoneDepth、updateY、tryApply（只替换 defaultBlock）、
 * erodedBadlands/frozenOcean 扩展、biomeManager.getBiome 判定。
 *
 * 实现细节（protected 嵌套类可见性）：
 * - NeoForge transform 产物中 SurfaceRules$Context/$SurfaceRule 的 InnerClasses 属性为 protected
 *   （类 access_flags 实为 public）——javac 按 InnerClasses 判断 → 跨包不可引用类型。
 *   方案：全部 Object + 反射（Context 构造 / updateXZ / updateY / getMinSurfaceLevel /
 *   tryApply，Method 一次缓存，热路径 invoke——用户拍板反射）。
 * - SurfaceRules$RuleSource 为 public（InnerClasses 一致）→ 类型可见；apply 经
 *   Function（public）擦除调用返回 Object，不需 SurfaceRule 类型。
 * - BlockColumn 匿名类无法捕获循环内变化的 l2/l1 → final int[] scanRange 捕获，循环内更新。
 */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemMixin {

    // ---- @Shadow：SurfaceSystem 私有成员（buildSurface 复制体内实际使用的）----

    @Shadow
    private BlockState defaultBlock;

    @Shadow
    private boolean isStone(BlockState state) {
        return false;
    }

    @Shadow
    protected abstract void erodedBadlandsExtension(BlockColumn blockColumn, int x, int z, int height, LevelHeightAccessor level);

    @Shadow
    private void frozenOceanExtension(int minSurfaceLevel, Biome biome, BlockColumn blockColumn,
            BlockPos.MutableBlockPos topWaterPos, int x, int z, int height) {
    }

    // ---- SurfaceRules$Context / $SurfaceRule：InnerClasses protected 跨包不可见 → 反射 ----

    @Unique
    private static final Constructor<?> CTOR_CONTEXT;
    @Unique
    private static final Method M_CTX_UPDATE_XZ;
    @Unique
    private static final Method M_CTX_UPDATE_Y;
    @Unique
    private static final Method M_CTX_GET_MIN_SURFACE_LEVEL;
    @Unique
    private static final Method M_SURFACE_RULE_TRY_APPLY;

    static {
        try {
            Class<?> ctxClass = Class.forName("net.minecraft.world.level.levelgen.SurfaceRules$Context");
            CTOR_CONTEXT = ctxClass.getDeclaredConstructor(
                    SurfaceSystem.class, RandomState.class, ChunkAccess.class,
                    NoiseChunk.class, Function.class, Registry.class, WorldGenerationContext.class);
            CTOR_CONTEXT.setAccessible(true);
            M_CTX_UPDATE_XZ = ctxClass.getDeclaredMethod("updateXZ", int.class, int.class);
            M_CTX_UPDATE_XZ.setAccessible(true);
            M_CTX_UPDATE_Y = ctxClass.getDeclaredMethod(
                    "updateY", int.class, int.class, int.class, int.class, int.class, int.class);
            M_CTX_UPDATE_Y.setAccessible(true);
            M_CTX_GET_MIN_SURFACE_LEVEL = ctxClass.getDeclaredMethod("getMinSurfaceLevel");
            M_CTX_GET_MIN_SURFACE_LEVEL.setAccessible(true);
            Class<?> ruleClass = Class.forName("net.minecraft.world.level.levelgen.SurfaceRules$SurfaceRule");
            M_SURFACE_RULE_TRY_APPLY = ruleClass.getMethod("tryApply", int.class, int.class, int.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static Object newContext(SurfaceSystem system, RandomState randomState, ChunkAccess chunk,
            NoiseChunk noiseChunk, Function<BlockPos, Holder<Biome>> biomeGetter, Registry<Biome> biomes,
            WorldGenerationContext context) {
        try {
            return CTOR_CONTEXT.newInstance(system, randomState, chunk, noiseChunk, biomeGetter, biomes, context);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 复制原版 buildSurface（1.21.1 L66-163）：
     * - 列扫描下界：max(chunk.getMinBuildHeight(), (long)(地表+1) − Config.maxCapIter)——每列算
     *   （依赖该列地表高度），long 减防溢出。
     * - BlockColumn.setBlock 范围：scanRange[0..1] = [l2, l1]（循环内更新，匿名类数组捕获）。
     * - SurfaceRules$Context/$SurfaceRule 全反射调用（InnerClasses protected，见类注释）。
     */
    @Overwrite
    public void buildSurface(
            RandomState randomState,
            BiomeManager biomeManager,
            Registry<Biome> biomes,
            boolean useLegacyRandomSource,
            WorldGenerationContext context,
            ChunkAccess chunk,
            NoiseChunk noiseChunk,
            SurfaceRules.RuleSource ruleSource) {
        SurfaceSystem self = (SurfaceSystem) (Object) this;
        final BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        final ChunkPos chunkpos = chunk.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        // 扫描区间 [min, max]，匿名类数组捕获（l2/l1 每列不同，匿名类不能捕获循环变量）
        final int[] scanRange = new int[2];
        // 列坐标 [i1, j1] 同样数组捕获——x/z 用列坐标，仅 y 用 p_190008_（此前笔误三参全取
        // p_190008_ & 15，所有写入堆到同一相对列，surface 全乱）
        final int[] colCoord = new int[2];
        BlockColumn blockcolumn = new BlockColumn() {
            @Override
            public BlockState getBlock(int p_190006_) {
                return chunk.getBlockState(blockpos$mutableblockpos.setY(p_190006_));
            }

            @Override
            public void setBlock(int p_190008_, BlockState p_190009_) {
                if (p_190008_ >= scanRange[0] && p_190008_ <= scanRange[1]) {
                    // 无锁直写 section（useLocks=false，getAndSetUnchecked）——对齐 fill：
                    // chunk.setBlockState 走 4 参重载（useLocks=true → PalettedContainer.acquire），
                    // 与主线程 §5 section.write 并发 → "Accessing PalettedContainer from multiple
                    // threads" CTD（fill 一直无锁所以安全，SURFACE 引入带锁写即竞争）。
                    LevelChunkSection section = ((WindowedChunk) chunk).windowedAllSections().get(p_190008_ >> 4);
                    if (section != null) {
                        section.setBlockState(
                                colCoord[0] & 15, p_190008_ & 15, colCoord[1] & 15, p_190009_, false);
                        if (!p_190009_.getFluidState().isEmpty()) {
                            // 原版 chunk.setBlockState(blockpos.setY(p), ...) 内部 setY——直写 section
                            // 后 markPos 需补 setY（blockpos 的 y 是上次 getBlock 残留）
                            chunk.markPosForPostprocessing(blockpos$mutableblockpos.setY(p_190008_));
                        }
                    }
                }
            }

            @Override
            public String toString() {
                return "ChunkBlockColumn " + chunkpos;
            }
        };
        Object surfacerules$context = newContext(
                self, randomState, chunk, noiseChunk, biomeManager::getBiome, biomes, context);
        // RuleSource extends Function（public）——apply 经原始 Function 擦除返回 Object，不需 SurfaceRule 类型
        Object surfacerules$surfacerule = ((Function) ruleSource).apply(surfacerules$context);
        BlockPos.MutableBlockPos blockpos$mutableblockpos1 = new BlockPos.MutableBlockPos();

        for (int k = 0; k < 16; k++) {
            for (int l = 0; l < 16; l++) {
                int i1 = i + k;
                int j1 = j + l;
                int k1 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;
                colCoord[0] = i1;
                colCoord[1] = j1;
                blockpos$mutableblockpos.setX(i1).setZ(j1);
                Holder<Biome> holder = biomeManager.getBiome(blockpos$mutableblockpos1.set(i1, useLegacyRandomSource ? 0 : k1, j1));
                if (holder.is(Biomes.ERODED_BADLANDS)) {
                    this.erodedBadlandsExtension(blockcolumn, i1, j1, k1, chunk);
                }

                int l1 = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, k, l) + 1;
                invokeCtx(M_CTX_UPDATE_XZ, surfacerules$context, i1, j1);
                int i2 = 0;
                int j2 = Integer.MIN_VALUE;
                int k2 = Integer.MAX_VALUE;
                // 列扫描下界：max(维度下界, 地表+1 − maxCapIter)——正常 Y 恒取维度下界（vanilla 原行为），
                // 极端 Y 有界 512 格（findLowestSourceBelow 同构，#44.6）。long 减防溢出。
                int l2 = Math.max(chunk.getMinBuildHeight(),
                        (int) ((long) l1 - (long) Config.maxCapIter));
                scanRange[0] = l2;
                scanRange[1] = l1;

                for (int i3 = l1; i3 >= l2; i3--) {
                    BlockState blockstate = blockcolumn.getBlock(i3);
                    if (blockstate.isAir()) {
                        i2 = 0;
                        j2 = Integer.MIN_VALUE;
                    } else if (!blockstate.getFluidState().isEmpty()) {
                        if (j2 == Integer.MIN_VALUE) {
                            j2 = i3 + 1;
                        }
                    } else {
                        if (k2 >= i3) {
                            k2 = net.minecraft.world.level.dimension.DimensionType.WAY_BELOW_MIN_Y;

                            for (int j3 = i3 - 1; j3 >= l2 - 1; j3--) {
                                BlockState blockstate1 = blockcolumn.getBlock(j3);
                                if (!isStone(blockstate1)) {
                                    k2 = j3 + 1;
                                    break;
                                }
                            }
                        }

                        i2++;
                        int k3 = i3 - k2 + 1;
                        invokeCtx(M_CTX_UPDATE_Y, surfacerules$context, i2, k3, j2, i1, i3, j1);
                        if (blockstate == this.defaultBlock) {
                            BlockState blockstate2 = (BlockState) invokeTryApply(surfacerules$surfacerule, i1, i3, j1);
                            if (blockstate2 != null) {
                                blockcolumn.setBlock(i3, blockstate2);
                            }
                        }
                    }
                }

                if (holder.is(Biomes.FROZEN_OCEAN) || holder.is(Biomes.DEEP_FROZEN_OCEAN)) {
                    int minSurfaceLevel = (int) invokeCtx(M_CTX_GET_MIN_SURFACE_LEVEL, surfacerules$context);
                    this.frozenOceanExtension(
                            minSurfaceLevel, holder.value(), blockcolumn,
                            blockpos$mutableblockpos1, i1, j1, k1);
                }
            }
        }
    }

    /** Context 方法反射（热路径：updateXZ/updateY 每列/每格——Method 缓存 invoke）。 */
    @Unique
    private static Object invokeCtx(Method method, Object ctx, Object... args) {
        try {
            return method.invoke(ctx, args);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** SurfaceRule.tryApply 反射（热路径：每 stone 格——Method 缓存 invoke）。 */
    @Unique
    private static Object invokeTryApply(Object rule, int x, int y, int z) {
        try {
            return M_SURFACE_RULE_TRY_APPLY.invoke(rule, x, y, z);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
