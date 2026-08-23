package com.inf.farlands.light;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import com.inf.farlands.WindowedChunk;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 天空光引擎，子区域模型。无 Y 范围假设。
 *
 * <p>传播算法移植自 vanilla {@code SkyLightEngine}，三处机械改名：
 * {@code storingLightForSection} → {@code storage.containsKey}，
 * {@code getStoredLevel} → 直接 {@code DataLayer.get}，
 * {@code setStoredLevel} → 直接 {@code DataLayer.set}。
 *
 * <p>天空光按 256 格子区域划分（每区 16 个 section）。每个活跃子区域的顶部
 * section 以 {@code DataLayer(15)} 播种。传播在子区域边界停止。
 */
@SuppressWarnings({ "null" })
public class FarLandsSkyLightEngine implements LayerLightEventListener {

    // ---- static constants (from vanilla SkyLightEngine) ----
    private static final long PULL_LIGHT_IN_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(1);
    private static final long REMOVE_TOP_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(15);
    private static final long REMOVE_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.decreaseSkipOneDirection(15, Direction.UP);
    private static final long ADD_SKY_SOURCE_ENTRY = LightEngine.QueueEntry.increaseSkipOneDirection(15, false, Direction.UP);
    private static final Direction[] PROPAGATION_DIRECTIONS = Direction.values();

    // ---- storage ----
    final FarLandsDataLayerStorage storage;
    /** Column-key → highest section Y that has a non-null DataLayer in storage. */
    private final ConcurrentHashMap<Long, Integer> topSections;

    // ---- propagation queues ----
    private final LongArrayFIFOQueue increaseQueue = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue decreaseQueue = new LongArrayFIFOQueue();
    final LongOpenHashSet blockNodesToCheck = new LongOpenHashSet(512, 0.5F);
    private final Object queueLock = new Object();

    /** 传播写入影响的 section（对齐 vanilla sectionsAffectedByLightUpdates）——runLightUpdates 末尾通知。 */
    private final LongOpenHashSet affectedSections = new LongOpenHashSet();

    // ---- chunk access ----
    private final LightChunkGetter chunkSource;
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final long[] lastChunkKeys = new long[2];
    private final LightChunk[] lastChunks = new LightChunk[2];

    // ---- 传播批次局部缓存：引用共享（传播 set DataLayer 数组 = 改 CHM），
    // 纯读加速（省 CHM.get 的 Long 装箱 + volatile）。任务内单线程，批次入口 clear。----
    private final Long2ObjectOpenHashMap<DataLayer> taskLocal = new Long2ObjectOpenHashMap<>();

    private DataLayer localGet(long sec) {
        DataLayer dl = taskLocal.get(sec);
        if (dl == null) {
            dl = storage.get(sec);
            if (dl != null) taskLocal.put(sec, dl);
        }
        return dl;
    }

    private DataLayer localGetOrCreate(long sec) {
        DataLayer dl = taskLocal.get(sec);
        if (dl == null) {
            dl = storage.getOrCreate(sec);
            taskLocal.put(sec, dl);
        }
        return dl;
    }

    private boolean localContains(long sec) {
        return taskLocal.containsKey(sec) || storage.containsKey(sec);
    }

    // ---- empty chunk sources (vanilla L21) ----
    private final ChunkSkyLightSources emptyChunkSources;

    public FarLandsSkyLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new FarLandsDataLayerStorage(), new ConcurrentHashMap<>());
    }

    /** 服务端共享 storage/topSections 构造（多个引擎实例共享同一仓库，供并行任务池化复用）。 */
    FarLandsSkyLightEngine(LightChunkGetter chunkSource, FarLandsDataLayerStorage storage,
            ConcurrentHashMap<Long, Integer> topSections) {
        this.chunkSource = chunkSource;
        this.storage = storage;
        this.topSections = topSections;
        this.emptyChunkSources = new ChunkSkyLightSources(chunkSource.getLevel());
        Arrays.fill(lastChunkKeys, ChunkPos.INVALID_CHUNK_POS);
    }

    // ==================== 公共 API ====================

    public int getLightValue(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        int cx = bp.x >> 4, sy = bp.y >> 4, cz = bp.z >> 4;
        int rx = bp.x & 15, ry = bp.y & 15, rz = bp.z & 15;

        long secKey = HashUtil.hashSection((long) cx, (long) sy, (long) cz);
        DataLayer dl = storage.get(secKey);
        if (dl != null) return dl.get(rx, ry, rz);

        // 快速失败：该列最高有层 section ≤ 当前 → 区内（sy+1..topSY）必无层 → 搜索必返回 15。
        // topSections 只增不减（merge max）、初始无条目（null 等价旧 MIN_VALUE）——
        // stale（remove 不更新）时只偏高不偏低 → 快速失败结果与搜索严格一致，绝不误判。
        // CHM 弱一致读到旧值（偏低）→ 快速失败不触发 → 走搜索 → 结果仍正确。
        Integer top = topSections.get(HashUtil.hashSection((long) cx, 0, (long) cz));
        if (top == null || top <= sy) {
            return 15;
        }

        // 在子区域内向上搜索
        int subRegion = sy >> 4;
        int topSY = subRegion * 16 + 15;
        for (int s = sy + 1; s <= topSY; s++) {
            dl = storage.get(HashUtil.hashSection((long) cx, (long) s, (long) cz));
            if (dl != null) return dl.get(rx, 0, rz); // 读层底部行（对齐 vanilla flatIndex y=0——遮挡列读 0 防幽灵亮）
        }

        // 在所有数据之上或子区域未激活 —— 虚拟天空顶
        return 15;
    }

    public void checkBlock(BlockPos pos) {
        synchronized (queueLock) {
            blockNodesToCheck.add(HashUtil.hashPos(
                    (long) pos.getX(), (long) pos.getY(), (long) pos.getZ()));
        }
    }

    public DataLayer getDataLayer(long sectionKey) {
        return storage.get(sectionKey);
    }

    public DataLayer getOrCreate(long sectionKey) {
        DataLayer dl = storage.getOrCreate(sectionKey);
        IntSectionPos sp = IntSectionPos.getSectionPos(sectionKey);
        int subRegion = sp.y >> 4;
        int topSY = subRegion * 16 + 15;
        if (sp.y == topSY && dl.isEmpty()) {
            // 区顶虚拟天穹：仅当该列最低光源 <= 区顶底（露天透光到区顶）才 fill 15。
            // 最低光源在区顶之上（该区顶被上方遮挡）→ 不 fill——深地下实心区顶被填
            // 15 → 客户端 getLightValue 向上搜索命中区顶 15 → 全亮（破坏出现 15 光束）。
            ChunkSkyLightSources src = getChunkSources(sp.x, sp.z);
            if (src == null) {
                dl.fill(15);
            } else {
                int lowest = src.getLowestSourceY(sp.x & 15, sp.z & 15);
                int topBase = topSY * 16;
                if (lowest != Integer.MIN_VALUE && lowest <= topBase) {
                    dl.fill(15);
                }
            }
        }
        updateTopSection(sp);
        return dl;
    }

    /** 服务端 chunk 卸载清理：side-channel 反查移除该 chunk 全部光照层（storage 不再随卸载增长）。 */
    public void removeChunk(ChunkPos pos) {
        int cx = pos.x, cz = pos.z;
        storage.removeIf(key -> {
            IntSectionPos sp = HashUtil.getSection(key);
            return sp != null && sp.x == cx && sp.z == cz;
        });
        // topSections 故意不清：stale 偏高 → O1 快速失败不触发（安全）。
        // ConcurrentHashMap：initializeLight（commonPool 异步）与主线程（setDataLayer/
        // updateSectionStatus）并发写安全（原 Long2IntOpenHashMap 并发写 → rehash 损坏
        // → AIOOBE CTD，2026-08-23 实崩）。
    }

    public void setDataLayer(long sectionKey, DataLayer layer) {
        storage.put(sectionKey, layer);
        updateTopSection(IntSectionPos.getSectionPos(sectionKey));
    }

    private void updateTopSection(IntSectionPos sp) {
        long zeroNode = HashUtil.hashSection((long) sp.x, 0, (long) sp.z);
        // 原子"只增不减"：absent → sp.y+1；present → max（原 if (cur < sp.y+1) put）
        topSections.merge(zeroNode, sp.y + 1, (a, b) -> Math.max(a.intValue(), b.intValue()));
    }

    // ==================== 服务端 per-chunk 任务入口 ====================

    /**
     * 处理一个 chunk 的方块变化 + section 变化。任务独占本引擎实例（单线程），
     * 队列/affectedSections 在任务内消耗。先建层（section 变化）再 checkNode
     * （方块变化）——checkNode 依赖所在 section 已建层。
     */
    public void processBlocksChanged(int chunkX, int chunkZ, Set<BlockPos> positions,
            Map<Integer, Boolean> sectionChanges) {
        taskLocal.clear(); // 批次入口
        blockNodesToCheck.clear(); // 防池化复用残留（任务路径不经 blockNodesToCheck）
        if (sectionChanges != null) {
            for (Map.Entry<Integer, Boolean> e : sectionChanges.entrySet()) {
                updateSectionStatus(SectionPos.of(chunkX, e.getKey(), chunkZ), e.getValue());
            }
        }
        for (BlockPos pos : positions) {
            // 必须用 asLong()（会 putBlock 注册 side-channel）——直接 hashPos 会 miss，
            // 传播链反查位解码垃圾坐标 → 光照写错位置 → 损坏。
            checkNode(pos.asLong());
        }
        runPropagation();
    }

    /**
     * 处理传播队列 + 通知。方块变化任务与播种任务共用——propagateLightSources 只
     * enqueue 光源，必须经本方法 propagate 才扩散（原同步模型靠每 tick runLightUpdates）。
     */
    public void runPropagation() {
        taskLocal.clear(); // 批次入口；播种后传播
        propagateDecreases();
        propagateIncreases();
        clearChunkCache();
        notifyLightChanges();
    }

    // ==================== runLightUpdates 支持 ====================

    @Override
    public boolean hasLightWork() {
        return !blockNodesToCheck.isEmpty() || !decreaseQueue.isEmpty() || !increaseQueue.isEmpty();
    }

    @Override
    public int runLightUpdates() {
        synchronized (queueLock) {
taskLocal.clear(); // 批次入口
            var it = blockNodesToCheck.iterator();
            while (it.hasNext()) checkNode(it.nextLong());
            blockNodesToCheck.clear();
            blockNodesToCheck.trim(512);

            int i = 0;
            i += propagateDecreases();
            i += propagateIncreases();
            clearChunkCache();
            notifyLightChanges();
            return i;
        }
    }

    // ==================== affected-section 通知（对齐 vanilla swapSectionMap → onLightUpdate）====================

    /** 写入影响 block 周围所有 section（对齐 vanilla setStoredLevel 的 aroundAndAtBlockPos）。 */
    private void markAffected(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        SectionPos.aroundAndAtBlockPos(bp.x, bp.y, bp.z, affectedSections::add);
    }

    /** 传播后通知（服务端 → sectionLightChanged → 增量包；客户端 → setSectionDirty 渲染刷新）。 */
    private void notifyLightChanges() {
        if (affectedSections.isEmpty()) {
            return;
        }
        for (long key : affectedSections) {
            IntSectionPos sp = HashUtil.getSection(key);
            if (sp == null) {
                continue; // 无 side-channel 条目（标记点漏 putSection）→ 跳过防垃圾坐标
            }
            chunkSource.onLightUpdate(LightLayer.SKY, SectionPos.of(sp.x, sp.y, sp.z));
        }
        affectedSections.clear();
    }

    // ==================== 传播 ====================

    // 移植自 LightEngine L164-181
    private int propagateIncreases() {
        int i;
        for (i = 0; increaseQueue.size() >= 2; i++) {
            long pos = increaseQueue.dequeueLong();
            long entry = increaseQueue.dequeueLong();
            int cur = getStoredLevel(pos);
            int fromEntry = LightEngine.QueueEntry.getFromLevel(entry);
            if (LightEngine.QueueEntry.isIncreaseFromEmission(entry) && cur < fromEntry) {
                setStoredLevel(pos, fromEntry);
                cur = fromEntry;
            }
            if (cur == fromEntry) {
                propagateIncrease(pos, entry, cur);
            }
        }
        return i;
    }

    // 移植自 LightEngine L184-193
    private int propagateDecreases() {
        int i;
        for (i = 0; decreaseQueue.size() >= 2; i++) {
            long pos = decreaseQueue.dequeueLong();
            long entry = decreaseQueue.dequeueLong();
            propagateDecrease(pos, entry);
        }
        return i;
    }

    void enqueueIncrease(long pos, long entry) {
        increaseQueue.enqueue(pos);
        increaseQueue.enqueue(entry);
    }

    void enqueueDecrease(long pos, long entry) {
        decreaseQueue.enqueue(pos);
        decreaseQueue.enqueue(entry);
    }

    // ---- storage read/write (vanilla: getStoredLevel / setStoredLevel) ----

    private int getStoredLevel(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        DataLayer dl = localGet(sec);
        return dl == null ? 0 : dl.get(bp.x & 15, bp.y & 15, bp.z & 15);
    }

    private void setStoredLevel(long packedPos, int level) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        DataLayer dl = localGetOrCreate(sec);
        dl.set(bp.x & 15, bp.y & 15, bp.z & 15, level);
        markAffected(packedPos);
    }

    // ---- propagateIncrease (ported from SkyLightEngine L139-175) ----

    private void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockstate = null;
        int emptyBelow = countEmptySectionsBelowIfAtBorder(packedPos);
        IntBlockPos src = IntBlockPos.getBlockPos(packedPos);

        for (Direction dir : PROPAGATION_DIRECTIONS) {
            if (!LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, dir)) continue;

            // C2：内联 BlockPos.offset——复用 src（省 offset 内部反查 + 反查 nPos 两次 CHM.get）
            int nbpX = src.x + dir.getStepX();
            int nbpY = src.y + dir.getStepY();
            int nbpZ = src.z + dir.getStepZ();
            long nPos = HashUtil.hashPos((long) nbpX, (long) nbpY, (long) nbpZ);
            HashUtil.putBlock(nPos, new IntBlockPos(nbpX, nbpY, nbpZ));
            long nSec = HashUtil.hashSection((long) (nbpX >> 4), (long) (nbpY >> 4), (long) (nbpZ >> 4));
            DataLayer ndl = localGet(nSec); // 按需建层：先 get，需要写时才 getOrCreate（消除传播白建空层积累）
            int nVal = ndl == null ? 0 : ndl.get(nbpX & 15, nbpY & 15, nbpZ & 15);

            int reduced = lightLevel - 1;
            if (reduced <= nVal) continue;

            mutablePos.set(nbpX, nbpY, nbpZ);
            BlockState bs1 = getState(mutablePos);
            int afterOpacity = lightLevel - getOpacity(bs1, mutablePos);
            if (afterOpacity <= nVal) continue;

            if (blockstate == null) {
                blockstate = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                        ? Blocks.AIR.defaultBlockState()
                        : getState(mutablePos.set(src.x, src.y, src.z));
            }

            // 形状检查跳过：仅当两方块都 isEmptyShape（getOcclusionShape 都返回
            // empty，faceShapeOccludes=false）才跳过。注意不能只跳过来源空形状——目标
            // 的真实面（台阶/条件方块 getFaceOcclusionShape 非空）会让 faceShapeOccludes
            // 返回 true（遮挡），来源空形状跳过会错误放行（光穿过台阶）。
            if (!(isEmptyShape(blockstate) && isEmptyShape(bs1))) {
                if (shapeOccludes(packedPos, blockstate, nPos, bs1, dir)) continue;
            }

            if (ndl == null) {
                ndl = localGetOrCreate(nSec);
            }
            ndl.set(nbpX & 15, nbpY & 15, nbpZ & 15, afterOpacity);
            markAffected(nPos);
            if (afterOpacity > 1) {
                enqueueIncrease(nPos, LightEngine.QueueEntry.increaseSkipOneDirection(
                        afterOpacity, isEmptyShape(bs1), dir.getOpposite()));
            }
            propagateFromEmptySections(nPos, dir, afterOpacity, true, emptyBelow);
        }
    }

    // ---- propagateDecrease (ported from SkyLightEngine L178-199) ----

    private void propagateDecrease(long packedPos, long lightLevel) {
        int emptyBelow = countEmptySectionsBelowIfAtBorder(packedPos);
        int fromLevel = LightEngine.QueueEntry.getFromLevel(lightLevel);
        IntBlockPos src = IntBlockPos.getBlockPos(packedPos);

        for (Direction dir : PROPAGATION_DIRECTIONS) {
            if (!LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, dir)) continue;

            // C2：内联 BlockPos.offset——复用 src（省 offset 内部反查 + 反查 nPos 两次 CHM.get）
            int nbpX = src.x + dir.getStepX();
            int nbpY = src.y + dir.getStepY();
            int nbpZ = src.z + dir.getStepZ();
            long nPos = HashUtil.hashPos((long) nbpX, (long) nbpY, (long) nbpZ);
            HashUtil.putBlock(nPos, new IntBlockPos(nbpX, nbpY, nbpZ));
            long nSec = HashUtil.hashSection((long) (nbpX >> 4), (long) (nbpY >> 4), (long) (nbpZ >> 4));
            if (!localContains(nSec)) continue;

            DataLayer ndl = localGet(nSec);
            if (ndl == null) continue;
            int nVal = ndl.get(nbpX & 15, nbpY & 15, nbpZ & 15);
            if (nVal == 0) continue;

            if (nVal <= fromLevel - 1) {
                ndl.set(nbpX & 15, nbpY & 15, nbpZ & 15, 0);
                markAffected(nPos);
                enqueueDecrease(nPos, LightEngine.QueueEntry.decreaseSkipOneDirection(nVal, dir.getOpposite()));
                propagateFromEmptySections(nPos, dir, nVal, false, emptyBelow);
            } else {
                enqueueIncrease(nPos, LightEngine.QueueEntry.increaseOnlyOneDirection(
                        nVal, false, dir.getOpposite()));
            }
        }
    }

    // ---- checkNode (ported from SkyLightEngine L51-76) ----

    private void checkNode(long levelPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(levelPos);
        int x = bp.x, y = bp.y, z = bp.z;
        long secKey = HashUtil.hashSection((long) (x >> 4), (long) (y >> 4), (long) (z >> 4));

        // vanilla 用 storage.lightOnInSection 门：无光源列（getLowestSourceY 返回
        // MIN_VALUE）不 updateSourcesInColumn、按清光分支处理。我们无 columnsWithSources，
        // 用 hasSource（!= MIN_VALUE）等价判断——否则 MIN_VALUE 会被 updateSourcesInColumn
        // 传给 addSourcesAbove 从 int 边界填 15（全亮火把样）。
        int lowestY = getLowestSourceY(x, z);
        boolean hasSource = lowestY != Integer.MIN_VALUE;
        if (hasSource) {
            updateSourcesInColumn(x, z, lowestY);
        }

        if (localContains(secKey)) {
            if (hasSource && y >= lowestY) {
                enqueueDecrease(levelPos, REMOVE_SKY_SOURCE_ENTRY);
                enqueueIncrease(levelPos, ADD_SKY_SOURCE_ENTRY);
            } else {
                int cur = getStoredLevel(levelPos);
                if (cur > 0) {
                    setStoredLevel(levelPos, 0);
                    enqueueDecrease(levelPos, LightEngine.QueueEntry.decreaseAllDirections(cur));
                } else {
                    enqueueDecrease(levelPos, PULL_LIGHT_IN_ENTRY);
                }
            }
        }
    }

    // ---- countEmptySectionsBelowIfAtBorder (ported from SkyLightEngine L201-226) ----

    private int countEmptySectionsBelowIfAtBorder(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        int relY = bp.y & 15;
        if (relY != 0) return 0;

        int relX = bp.x & 15, relZ = bp.z & 15;
        if (relX != 0 && relX != 15 && relZ != 0 && relZ != 15) return 0;

        int cx = bp.x >> 4, sy = bp.y >> 4, cz = bp.z >> 4;
        int subRegion = sy >> 4;
        int bottomSY = subRegion * 16;
        int count = 0;

        while (sy - count - 1 >= bottomSY
                && !localContains(HashUtil.hashSection((long) cx, (long) (sy - count - 1), (long) cz))) {
            count++;
        }
        return count;
    }

    // ---- propagateFromEmptySections (ported from SkyLightEngine L228-263) ----

    private void propagateFromEmptySections(long packedPos, Direction dir, int level,
                                            boolean increase, int emptySections) {
        if (emptySections == 0) return;

        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        int x = bp.x, z = bp.z;
        if (!crossedSectionEdge(dir, bp.x & 15, bp.z & 15)) return;

        int cx = x >> 4, cz = z >> 4;
        int startSY = (bp.y >> 4) - 1;
        int endSY = startSY - emptySections + 1;

        for (int sy = startSY; sy >= endSY; sy--) {
            long secKey = HashUtil.hashSection((long) cx, (long) sy, (long) cz);
            DataLayer dl = localGetOrCreate(secKey);
            int blockY = sy << 4;
            for (int ry = 15; ry >= 0; ry--) {
                long bPos = HashUtil.hashPos((long) x, (long) (blockY + ry), (long) z);
                HashUtil.putBlock(bPos, new IntBlockPos(x, blockY + ry, z));
                if (increase) {
                    dl.set(x & 15, ry, z & 15, level);
                    markAffected(bPos);
                    if (level > 1) {
                        enqueueIncrease(bPos, LightEngine.QueueEntry.increaseSkipOneDirection(
                                level, true, dir.getOpposite()));
                    }
                } else {
                    dl.set(x & 15, ry, z & 15, 0);
                    markAffected(bPos);
                    enqueueDecrease(bPos, LightEngine.QueueEntry.decreaseSkipOneDirection(
                            level, dir.getOpposite()));
                }
            }
        }
    }

    // ---- static helpers ----

    private static boolean crossedSectionEdge(Direction dir, int rx, int rz) {
        return switch (dir) {
            case NORTH -> rz == 15;
            case SOUTH -> rz == 0;
            case WEST -> rx == 15;
            case EAST -> rx == 0;
            default -> false;
        };
    }

    private static boolean isSourceLevel(int level) { return level == 15; }

    private static boolean isEmptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
    }

    // ==================== 天空光源 ====================

    private int getLowestSourceY(int x, int z) {
        ChunkSkyLightSources src = getChunkSources(x >> 4, z >> 4);
        return src == null ? Integer.MAX_VALUE : src.getLowestSourceY(x & 15, z & 15);
    }

    private ChunkSkyLightSources getChunkSources(int cx, int cz) {
        LightChunk lc = getChunk(cx, cz);
        return lc == null ? null : lc.getSkyLightSources();
    }

    private void updateSourcesInColumn(int x, int z, int lowestY) {
        int subRegion = (lowestY >> 4) >> 4;
        int bottomSY = subRegion * 16;
        removeSourcesBelow(x, z, lowestY, bottomSY);
        addSourcesAbove(x, z, lowestY, bottomSY);
    }

    // ---- removeSourcesBelow (ported from SkyLightEngine L84-107) ----

    private void removeSourcesBelow(int x, int z, int minY, int bottomSectionY) {
        if (minY <= bottomSectionY) return;
        int cx = x >> 4, cz = z >> 4;
        int limitY = minY - 1;

        for (int sy = limitY >> 4; sy >= bottomSectionY; sy--) {
            long secKey = HashUtil.hashSection((long) cx, (long) sy, (long) cz);
            if (!storage.containsKey(secKey)) continue;

            DataLayer dl = storage.get(secKey);
            if (dl == null) continue;
            affectedSections.add(SectionPos.asLong(cx, sy, cz)); // 进入即标记（含提前 return 分支）
            int baseY = sy << 4;
            int topY = Math.min(baseY + 15, limitY);

            for (int by = topY; by >= baseY; by--) {
                long bKey = HashUtil.hashPos((long) x, (long) by, (long) z);
                HashUtil.putBlock(bKey, new IntBlockPos(x, by, z));
                if (!isSourceLevel(dl.get(x & 15, by - baseY, z & 15))) return;
                dl.set(x & 15, by - baseY, z & 15, 0);
                enqueueDecrease(bKey, by == minY - 1 ? REMOVE_TOP_SKY_SOURCE_ENTRY : REMOVE_SKY_SOURCE_ENTRY);
            }
        }
    }

    // ---- addSourcesAbove (ported from SkyLightEngine L109-136) ----

    private void addSourcesAbove(int x, int z, int maxY, int bottomSectionY) {
        int cx = x >> 4, cz = z >> 4;
        int neighMin = Math.max(
                Math.max(getLowestSourceY(x - 1, z), getLowestSourceY(x + 1, z)),
                Math.max(getLowestSourceY(x, z - 1), getLowestSourceY(x, z + 1)));
        int effectiveMax = Math.max(maxY, bottomSectionY << 4);

        int subRegion = (maxY >> 4) >> 4;
        int topSY = subRegion * 16 + 15;

        for (int sy = effectiveMax >> 4; sy <= topSY; sy++) {
            long secKey = HashUtil.hashSection((long) cx, (long) sy, (long) cz);
            if (!storage.containsKey(secKey)) continue;

            DataLayer dl = storage.get(secKey);
            if (dl == null) continue;
            affectedSections.add(SectionPos.asLong(cx, sy, cz)); // 进入即标记（含提前 return 分支）
            int baseY = sy << 4;
            int topY = baseY + 15;

            // long 化防溢出：topY = baseY + 15 在 2.14B（sy=MAX_CHUNK）时 = Integer.MAX_VALUE，
            // int by++ 溢出为负 → 无限循环 → DataLayer.get 越界 CTD。
            for (long by = Math.max((long) baseY, (long) effectiveMax); by <= topY; by++) {
                long bKey = HashUtil.hashPos((long) x, by, (long) z);
                HashUtil.putBlock(bKey, new IntBlockPos(x, (int) by, z));
                if (isSourceLevel(dl.get(x & 15, (int) by - baseY, z & 15))) return;
                dl.set(x & 15, (int) by - baseY, z & 15, 15);
                if (by < neighMin || by == maxY) {
                    enqueueIncrease(bKey, ADD_SKY_SOURCE_ENTRY);
                }
            }
        }
    }

    // ---- setLightEnabled (ported from SkyLightEngine L276-293) ----

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        if (isEmpty) {
            storage.remove(pos.asLong());
        } else {
            getOrCreate(pos.asLong());
        }
    }

    @Override
    public DataLayer getDataLayerData(SectionPos pos) {
        return storage.get(pos.asLong());
    }

    @Override
    public int getLightValue(BlockPos pos) {
        return getLightValue(pos.asLong());
    }

    @Override
    public void setLightEnabled(ChunkPos pos, boolean enabled) {
        if (!enabled) return;
        int cx = pos.x, cz = pos.z;
        ChunkSkyLightSources sources = java.util.Objects.requireNonNullElse(
                getChunkSources(cx, cz), emptyChunkSources);
        // 无光源（getHighestLowestSourceY 返回 MIN_VALUE，如未 fillFrom 的 LevelChunk）
        // → 无区顶 15——否则 MIN_VALUE-1 溢出成 int max → 遍历垃圾 section 建层 fill 15。
        int highestLowest = sources.getHighestLowestSourceY();
        if (highestLowest == Integer.MIN_VALUE) return;
        highestLowest -= 1;
        int startSY = (highestLowest >> 4) + 1;

        int subRegion = startSY >> 4;
        int bottomSY = subRegion * 16;
        int topSY = subRegion * 16 + 15;

        for (int sy = topSY - 1; sy >= Math.max(bottomSY, startSY); sy--) {
            long secKey = HashUtil.hashSection((long) cx, (long) sy, (long) cz);
            DataLayer dl = storage.getOrCreate(secKey);
            if (dl.isEmpty()) {
                dl.fill(15);
            }
        }
    }

    // ---- propagateLightSources (ported from SkyLightEngine L296-347) ----

    public void propagateLightSources(ChunkPos pos) {
        synchronized (queueLock) {
            propagateLightSourcesLocked(pos);
        }
    }

    private void propagateLightSourcesLocked(ChunkPos pos) {
        int cx = pos.x, cz = pos.z;

        var lc = getChunk(cx, cz);
        if (lc instanceof WindowedChunk wc) {
            for (int sy : wc.windowedAllSections().keySet()) {
                int subRegion = sy >> 4;
                int topSY = subRegion * 16 + 15;
                if (sy == topSY) {
                    getOrCreate(HashUtil.hashSection((long) cx, (long) sy, (long) cz));
                }
            }
        }

        setLightEnabled(pos, true);

        ChunkSkyLightSources s0 = java.util.Objects.requireNonNullElse(getChunkSources(cx, cz), emptyChunkSources);
        ChunkSkyLightSources sN = java.util.Objects.requireNonNullElse(getChunkSources(cx, cz - 1), emptyChunkSources);
        ChunkSkyLightSources sS = java.util.Objects.requireNonNullElse(getChunkSources(cx, cz + 1), emptyChunkSources);
        ChunkSkyLightSources sW = java.util.Objects.requireNonNullElse(getChunkSources(cx - 1, cz), emptyChunkSources);
        ChunkSkyLightSources sE = java.util.Objects.requireNonNullElse(getChunkSources(cx + 1, cz), emptyChunkSources);

        int bx = cx << 4, bz = cz << 4;

        lc = getChunk(cx, cz);
        if (lc instanceof WindowedChunk wc2) {
            for (int sy : wc2.windowedAllSections().keySet()) {
                long secKey = HashUtil.hashSection((long) cx, (long) sy, (long) cz);
                // vanilla 语义：getDataLayerToWrite 创建层（传播初始化必须建层，
                // 否则地表空层被跳过 → 最低光源处永不 enqueue → 传播无源 → 全黑）。
                // 空层（全 0）留在 storage 也无害：该位置本就被遮挡（该黑）。
                DataLayer dl = storage.getOrCreate(secKey);
                affectedSections.add(SectionPos.asLong(cx, sy, cz)); // 每 section 一次（渲染刷新粒度）

                int baseY = sy << 4;
                int maxY = baseY + 15;

                for (int rx = 0; rx < 16; rx++) {
                    for (int rz = 0; rz < 16; rz++) {
                        int lowest0 = s0.getLowestSourceY(rx, rz);
                        // 无光源列（MIN_VALUE，如未 fillFrom 的 LevelChunk）→ 不播种——
                        // 否则 for 循环 max(baseY, MIN_VALUE)=baseY 填整个 section 15。
                        if (lowest0 == Integer.MIN_VALUE || lowest0 > maxY) continue;

                        int lowestN = rz == 0 ? sN.getLowestSourceY(rx, 15) : s0.getLowestSourceY(rx, rz - 1);
                        int lowestS = rz == 15 ? sS.getLowestSourceY(rx, 0) : s0.getLowestSourceY(rx, rz + 1);
                        int lowestW = rx == 0 ? sW.getLowestSourceY(15, rz) : s0.getLowestSourceY(rx - 1, rz);
                        int lowestE = rx == 15 ? sE.getLowestSourceY(0, rz) : s0.getLowestSourceY(rx + 1, rz);
                        int neighborMin = Math.max(Math.max(lowestN, lowestS), Math.max(lowestW, lowestE));

                        for (int by = maxY; by >= Math.max(baseY, lowest0); by--) {
                            dl.set(rx, by - baseY, rz, 15);
                            if (by == lowest0 || by < neighborMin) {
                                long bKey = HashUtil.hashPos((long) (bx + rx), (long) by, (long) (bz + rz));
                                HashUtil.putBlock(bKey, new IntBlockPos(bx + rx, by, bz + rz));
                                enqueueIncrease(bKey, LightEngine.QueueEntry.increaseSkySourceInDirections(
                                        by == lowest0, by < lowestN, by < lowestS, by < lowestW, by < lowestE));
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== chunk 缓存 ====================

    private LightChunk getChunk(int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        for (int i = 0; i < 2; i++) {
            if (key == lastChunkKeys[i]) return lastChunks[i];
        }
        LightChunk lc = chunkSource.getChunkForLighting(cx, cz);
        lastChunkKeys[1] = lastChunkKeys[0];
        lastChunks[1] = lastChunks[0];
        lastChunkKeys[0] = key;
        lastChunks[0] = lc;
        return lc;
    }

    private void clearChunkCache() {
        Arrays.fill(lastChunkKeys, ChunkPos.INVALID_CHUNK_POS);
        Arrays.fill(lastChunks, null);
    }

    // ==================== 方块访问 ====================

    private BlockState getState(BlockPos pos) {
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        LightChunk lc = getChunk(cx, cz);
        return lc == null ? Blocks.BEDROCK.defaultBlockState() : lc.getBlockState(pos);
    }

    private int getOpacity(BlockState state, BlockPos pos) {
        return Math.max(1, state.getLightBlock(chunkSource.getLevel(), pos));
    }

    private boolean shapeOccludes(long pos1, BlockState state1, long pos2, BlockState state2, Direction dir) {
        VoxelShape shape1 = getOcclusionShape(state1, pos1, dir);
        VoxelShape shape2 = getOcclusionShape(state2, pos2, dir.getOpposite());
        return Shapes.faceShapeOccludes(shape1, shape2);
    }

    private VoxelShape getOcclusionShape(BlockState state, long pos, Direction dir) {
        IntBlockPos bp = IntBlockPos.getBlockPos(pos);
        mutablePos.set(bp.x, bp.y, bp.z);
        return LightEngine.getOcclusionShape(chunkSource.getLevel(), mutablePos, state, dir);
    }
}
