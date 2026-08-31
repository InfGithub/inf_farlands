package com.inf.farlands.mixin.axisY;

import com.inf.farlands.CarvingMaskStorage;
import com.inf.farlands.window.EntitySectionWindow;
import com.inf.farlands.window.WindowedChunk;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntRBTreeSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.blending.BlendingData;
import net.minecraft.core.Registry;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

import org.slf4j.Logger;

@Mixin(ChunkAccess.class)
public abstract class ChunkAccessMixin implements WindowedChunk, CarvingMaskStorage {

    @Unique
    private final Map<Integer, LevelChunkSection> allSections = new ConcurrentHashMap<>();

    @Unique
    private volatile LevelChunkSection[] windowSections = new LevelChunkSection[0];

    @Redirect(method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/core/Registry;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"), require = 0)
    private void suppressSectionMismatchWarn(Logger logger, String msg, Object a, Object b) {
        // 抑制 section 数组长度不匹配的警告："Could not set level chunk sections"
    }

    @Unique
    private int windowMinY = 4;

    // 客户端持有边界见 window.md §7.2：服务端承诺发送的 Y 范围，
    // 由 §5 section 包 handler 更新；MIN_VALUE = 未收到包。
    @Unique
    private int lastPacketMinY = Integer.MIN_VALUE;

    @Unique
    private int lastPacketMaxY = Integer.MIN_VALUE;

    // per-section 脏标记，由 fsa 序列化引擎维护：记录磁盘持久化后内容是否修改过。
    // 线程安全：setBlock 主线程标脏、GenTask fill genPool 线程标脏、清理主线程读写。
    @Unique
    private final Map<Integer, Boolean> dirtySections = new ConcurrentHashMap<>();

    // 增量扫描架构 3——allSections key 的有序镜像。synchronized：懒创建 add 在
    // genPool 线程、清理扫描在主线程，并发需同步。只维护服务端相关：客户端无清理。
    @Unique
    private final IntRBTreeSet activeSectionYs = new IntRBTreeSet();

    @Unique
    private Registry<Biome> biomeRegistry;

    // carvingMask 存储（CARVERS 阶段）：目标 chunk 生成期 17×17 起点 carver 调用的同格去重。
    // vanilla 在 ProtoChunk 上（carvingMasks map），mod 短路后 chunk 是 LevelChunk → 接口注入。
    // 生命周期：LIGHTED 后可丢弃（mod 无 FEATURES 地物，CarvingMaskPlacement 不需要），内存有界。
    // 生成期单线程（GenTask 串行），无需并发保护。
    @Unique
    private final Map<GenerationStep.Carving, CarvingMask> carvingMasks =
            new EnumMap<>(GenerationStep.Carving.class);

    @Override
    public CarvingMask getCarvingMask(GenerationStep.Carving step) {
        return this.carvingMasks.get(step);
    }

    @Override
    public CarvingMask getOrCreateCarvingMask(GenerationStep.Carving step) {
        return this.carvingMasks.computeIfAbsent(step,
                s -> new CarvingMask(this.getHeight(), this.getMinBuildHeight()));
    }

    @Override
    public void setCarvingMask(GenerationStep.Carving step, CarvingMask mask) {
        this.carvingMasks.put(step, mask);
    }

    private static final Field F_SECTIONS;
    static {
        try {
            F_SECTIONS = ChunkAccess.class.getDeclaredField("sections");
            F_SECTIONS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Shadow
    protected LevelHeightAccessor levelHeightAccessor;

    @Shadow
    public abstract int getHeight();

    @Shadow
    public abstract int getMinBuildHeight();

    @Override
    public LevelHeightAccessor levelHeightAccessor() {
        return this.levelHeightAccessor;
    }

    // 辅助：LevelChunk 用 windowMinY，ProtoChunk 用 ServerLevel
    @Override
    public int windowSectionYFromIndex(int idx) {
        if ((Object) this instanceof LevelChunk) {
            return this.windowMinY + idx;
        }
        return this.levelHeightAccessor.getSectionYFromSectionIndex(idx);
    }

    @Override
    public int windowSectionIndexFromY(int y) {
        if ((Object) this instanceof LevelChunk) {
            return y - this.windowMinY;
        }
        return this.levelHeightAccessor.getSectionIndexFromSectionY(y);
    }

    private int _minSection() {
        return this.levelHeightAccessor.getMinSection();
    }

    private int _maxSection() {
        return this.levelHeightAccessor.getMaxSection();
    }

    private int _maxBuild() {
        return this.levelHeightAccessor.getMaxBuildHeight();
    }

    private int _sectIdx(int y) {
        return windowSectionIndexFromY(y >> 4);
    }

    // ---------------- disable replaceMissingSections ----------------

    @SuppressWarnings("null")
    @Overwrite
    private static void replaceMissingSections(Registry<Biome> biomeRegistry,
            LevelChunkSection[] sections) {
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == null) {
                sections[i] = new LevelChunkSection(biomeRegistry);
            }
        }
    }

    // ---------------- redirect sections array size ----------------

    // 已由 LevelHeightAccessorMixin 替换 —— getSectionsCount 全局返回窗口大小

    // ---------------- move sections to allSections ----------------

    @Inject(method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/core/Registry;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/LevelHeightAccessor;getSectionsCount()I"))
    private void initWindowEarly(ChunkPos chunkPos, UpgradeData upgradeData, LevelHeightAccessor lha,
            Registry<Biome> biomes, long inhabitedTime, LevelChunkSection[] sectionsParam, BlendingData blendingData,
            CallbackInfo ci) {
        if (!((Object) this instanceof ProtoChunk)) {
            this.biomeRegistry = biomes;
            this.windowMinY = -4;
            buildWindow(-4, -4);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/chunk/UpgradeData;Lnet/minecraft/world/level/LevelHeightAccessor;Lnet/minecraft/core/Registry;J[Lnet/minecraft/world/level/chunk/LevelChunkSection;Lnet/minecraft/world/level/levelgen/blending/BlendingData;)V", at = @At("RETURN"))
    private void initAllSections(
            ChunkPos chunkPos,
            UpgradeData upgradeData,
            LevelHeightAccessor lha,
            Registry<Biome> biomes,
            long inhabitedTime,
            LevelChunkSection[] sectionsParam,
            BlendingData blendingData,
            CallbackInfo ci) {
        this.biomeRegistry = biomes;
        if (sectionsParam != null) {
            for (int i = 0; i < sectionsParam.length; i++) {
                if (sectionsParam[i] != null) {
                    int sy = lha.getSectionYFromSectionIndex(i);
                    this.allSections.put(sy, sectionsParam[i]);
                    addActiveSection(sy);
                }
            }
        }
        buildDefaultWindow();
    }

    private void buildDefaultWindow() {
        if ((Object) this instanceof ProtoChunk) {
            buildWindow(4, 4);
        } else {
            buildWindow(_minSection(), _minSection());
        }
    }

    @Override
    public void buildWindow(int sectionYMin, int sectionYMax) {
        if (sectionYMin > sectionYMax) {
            return;
        }
        if (sectionYMin == this.windowMinY && sectionYMax == this.getWindowMaxY()) {
            return; // 窗口未变 → 零开销，这是每帧调用的前提
        }
        int count = sectionYMax - sectionYMin + 1;
        this.windowMinY = sectionYMin;
        LevelChunkSection[] win = new LevelChunkSection[count];
        for (int sy = sectionYMin; sy <= sectionYMax; sy++) {
            int idx = windowSectionIndexFromY(sy);
            LevelChunkSection s = this.getSection(idx);
            win[sy - sectionYMin] = s;
            try {
                LevelChunkSection[] arr = (LevelChunkSection[]) F_SECTIONS.get(this);
                if (idx >= 0 && idx < arr.length && arr[idx] != s) {
                    arr[idx] = s;
                }
            } catch (Exception ignored) {
            }
        }
        this.windowSections = win;
    }

    @Override
    public int getWindowMinY() {
        return this.windowMinY;
    }

    @Override
    public int getWindowMaxY() {
        return this.windowMinY + this.windowSections.length - 1;
    }

    @Override
    public int lastPacketMinY() {
        return this.lastPacketMinY;
    }

    @Override
    public int lastPacketMaxY() {
        return this.lastPacketMaxY;
    }

    @Override
    public void setLastPacketWindow(int minY, int maxY) {
        this.lastPacketMinY = minY;
        this.lastPacketMaxY = maxY;
    }

    @Override
    public void markSectionDirty(int sectionY) {
        this.dirtySections.put(sectionY, Boolean.TRUE);
    }

    @Override
    public boolean isSectionDirty(int sectionY) {
        return this.dirtySections.containsKey(sectionY);
    }

    @Override
    public void clearSectionDirty(int sectionY) {
        this.dirtySections.remove(sectionY);
    }

    // ---- 增量扫描架构 3 ----

    @Override
    public void addActiveSection(int sectionY) {
        synchronized (this.activeSectionYs) {
            this.activeSectionYs.add(sectionY);
        }
    }

    @Override
    public void removeActiveSection(int sectionY) {
        synchronized (this.activeSectionYs) {
            this.activeSectionYs.remove(sectionY);
        }
    }

    /**
     * 遍历窗口并集 ±margin 外的 section，即清理候选。锁内快照到 IntSet 去重，
     * 因为多窗口区间边界外可能重叠；锁外遍历，避免遍历时调用 removeFromMemory 修改。
     */
    @Override
    public void forEachOutsideWindows(int margin, IntConsumer consumer) {
        IntSet collected = new IntOpenHashSet();
        int[] ranges = EntitySectionWindow.ranges();
        synchronized (this.activeSectionYs) {
            for (int i = 0; i < ranges.length; i += 2) {
                int min = ranges[i];
                int max = ranges[i + 1];
                IntIterator it = this.activeSectionYs.headSet(min - margin).iterator();
                while (it.hasNext()) {
                    collected.add(it.nextInt());
                }
                it = this.activeSectionYs.tailSet(max + margin + 1).iterator();
                while (it.hasNext()) {
                    collected.add(it.nextInt());
                }
            }
        }
        collected.forEach(consumer);
    }

    @Override
    public Map<Integer, LevelChunkSection> windowedAllSections() {
        return this.allSections;
    }

    // ---------------- getSections / getSection ----------------

    @Overwrite
    public LevelChunkSection[] getSections() {
        return this.windowSections;
    }

    @SuppressWarnings("null")
    @Overwrite
    public LevelChunkSection getSection(int index) {
        int sy = windowSectionYFromIndex(index);
        LevelChunkSection s = this.allSections.get(sy);
        if (s == null) {
            s = new LevelChunkSection(this.biomeRegistry);
            this.allSections.put(sy, s);
            addActiveSection(sy); // 增量扫描镜像：genPool fill 也在本路径，synchronized 保护
        }
        try {
            LevelChunkSection[] arr = (LevelChunkSection[]) F_SECTIONS.get(this);
            if (index >= 0 && index < arr.length) {
                arr[index] = s;
            }
            // 同步 windowSections 窗口数组：数据到达时只写 allSections + vanilla sections
            // 数组 F_SECTIONS，windowSections 仅在 buildWindow 重建——若 buildWindow 先于
            // 数据跑，窗口数组留懒创建空 → sodium 编译读到空 → section 消失。这里补同步。
            if (index >= 0 && index < this.windowSections.length) {
                this.windowSections[index] = s;
            }
        } catch (Exception ignored) {
        }
        return s;
    }

    // ---------------- getHighestFilledSectionIndex ----------------

    @Overwrite
    public int getHighestFilledSectionIndex() {
        int max = -1;
        if ((Object) this instanceof ProtoChunk) {
            for (Integer sy : this.allSections.keySet()) {
                if (sy > max)
                    max = sy;
            }
            return max == -1 ? -1 : windowSectionIndexFromY(max);
        }
        int sectionsCount = this.getSections().length;
        for (Integer sy : this.allSections.keySet()) {
            int idx = windowSectionIndexFromY(sy);
            if (idx >= 0 && idx < sectionsCount && idx > max) {
                max = idx;
            }
        }
        return max;
    }

    // ---------------- getNoiseBiome ----------------

    @SuppressWarnings("null")
    @Overwrite
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        try {
            int j = _sectIdx(QuartPos.toBlock(Mth.clamp(y,
                    QuartPos.fromBlock(this.levelHeightAccessor.getMinBuildHeight()),
                    QuartPos.fromBlock(this.levelHeightAccessor.getMinBuildHeight())
                            + QuartPos.fromBlock(this.levelHeightAccessor.getHeight()) - 1)));
            LevelChunkSection s = this.getSection(j);
            Holder<Biome> result;
if (s != null) {
                result = s.getNoiseBiome(x & 3, y & 3, z & 3);
            } else {
                result = this.biomeRegistry != null
                        ? this.biomeRegistry.getHolderOrThrow(Biomes.THE_VOID)
                        : ((ChunkAccess) (Object) this)
                                .getLevel()
                                .registryAccess()
                                .registryOrThrow(Registries.BIOME)
                                .getHolderOrThrow(Biomes.THE_VOID);
            }
            return result;

        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting biome");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Biome being got");
            crashreportcategory.setDetail("Location",
                    () -> CrashReportCategory.formatLocation(((ChunkAccess) (Object) this), x, y, z));
            throw new ReportedException(crashreport);
        }
    }

    // ---------------- isYSpaceEmpty ----------------

    @Overwrite
    public boolean isYSpaceEmpty(int startY, int endY) {
        if (startY < this.levelHeightAccessor.getMinBuildHeight()) {
            startY = this.levelHeightAccessor.getMinBuildHeight();
        }
        if (endY >= _maxBuild()) {
            endY = _maxBuild() - 1;
        }
        for (int i = startY; i <= endY; i += 16) {
            LevelChunkSection s = this.getSection(_sectIdx(i));
            if (s != null && !s.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    // ---------------- isSectionEmpty ----------------

    @Overwrite
    public boolean isSectionEmpty(int y) {
        LevelChunkSection s = this.getSection(windowSectionIndexFromY(y));
        return s == null || s.hasOnlyAir();
    }

    // ---------------- findBlocks ----------------

    @SuppressWarnings("null")
    @Overwrite
    public void findBlocks(
            Predicate<BlockState> quickFilter,
            BiPredicate<BlockState, BlockPos> fineFilter,
            BiConsumer<BlockPos, BlockState> output) {
        ChunkAccess self = (ChunkAccess) (Object) this;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        for (int i = _minSection(); i < _maxSection(); i++) {
            LevelChunkSection s = this.getSection(windowSectionIndexFromY(i));
            if (s == null) {
                continue;
            }
            if (s.maybeHas(quickFilter)) {
                BlockPos origin = SectionPos.of(self.getPos(), i).origin();
                for (int x = 0; x < 16; x++)
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++) {
                            BlockState state = s.getBlockState(x, y, z);
                            mpos.setWithOffset(origin, x, y, z);
                            if (fineFilter.test(state, mpos)) {
                                output.accept(mpos, state);
                            }
                        }
            }
        }
    }

    // ---------------- suppress post-processing on LevelChunk ----------------

    @Overwrite
    public void markPosForPostprocessing(BlockPos pos) {
        // LevelChunk 不支持后处理；静默忽略。
    }

    // ---------------- fillBiomesFromNoise ----------------

    @SuppressWarnings("null")
    @Overwrite
    public void fillBiomesFromNoise(BiomeResolver resolver, Climate.Sampler sampler) {
        ChunkAccess self = (ChunkAccess) (Object) this;
        ChunkPos cpos = self.getPos();
        int i = QuartPos.fromBlock(cpos.getMinBlockX());
        int j = QuartPos.fromBlock(cpos.getMinBlockZ());
        LevelHeightAccessor lha = this.levelHeightAccessor;
        for (int k = lha.getMinSection(); k < lha.getMaxSection(); k++) {
            LevelChunkSection s = this.getSection(windowSectionIndexFromY(k));
            if (s == null) {
                continue;
            }
            int l = QuartPos.fromSection(k);
            s.fillBiomesFromNoise(resolver, sampler, i, l, j);
        }
    }
}
