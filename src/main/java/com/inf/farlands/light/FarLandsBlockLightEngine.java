package com.inf.farlands.light;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

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
import net.minecraft.world.level.lighting.LayerLightEventListener;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 方块光引擎。传播移植自 vanilla {@code BlockLightEngine}，机械改名：
 * {@code storingLightForSection} → {@code storage.containsKey}，
 * {@code getStoredLevel} → 直接 {@code DataLayer.get}，
 * {@code setStoredLevel} → 直接 {@code DataLayer.set}。
 *
 * <p>单层 {@link FarLandsDataLayerStorage} 替代 vanilla 的 queued/updating/visible
 * 三层缓冲。无 Y 范围限制。
 */
@SuppressWarnings({ "null" })
public class FarLandsBlockLightEngine implements LayerLightEventListener {

    private static final long PULL_LIGHT_IN_ENTRY = LightEngine.QueueEntry.decreaseAllDirections(1);
    private static final Direction[] PROPAGATION_DIRECTIONS = Direction.values();

    final FarLandsDataLayerStorage storage;
    private final LongArrayFIFOQueue increaseQueue = new LongArrayFIFOQueue();
    private final LongArrayFIFOQueue decreaseQueue = new LongArrayFIFOQueue();
    final LongOpenHashSet blockNodesToCheck = new LongOpenHashSet(512, 0.5F);
    private final Object queueLock = new Object();

    /** 传播写入影响的 section（对齐 vanilla sectionsAffectedByLightUpdates）——runLightUpdates 末尾通知。 */
    private final LongOpenHashSet affectedSections = new LongOpenHashSet();

    private final LightChunkGetter chunkSource;
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
    private final long[] lastChunkKeys = new long[2];
    private final LightChunk[] lastChunks = new LightChunk[2];

    // ---- 传播批次局部缓存：引用共享，纯读加速（省 CHM 装箱）。任务内单线程。----
    private final Long2ObjectOpenHashMap<DataLayer> taskLocal = new Long2ObjectOpenHashMap<>();

    private DataLayer localGet(long sec) {
        DataLayer dl = taskLocal.get(sec);
        if (dl == null) {
            dl = storage.get(sec);
            if (dl != null) taskLocal.put(sec, dl);
        }
        return dl;
    }

    private DataLayer localGetOrCreate(long sec, long chunkKey) {
        DataLayer dl = taskLocal.get(sec);
        if (dl == null) {
            dl = storage.getOrCreate(sec, chunkKey);
            taskLocal.put(sec, dl);
        }
        return dl;
    }

    private boolean localContains(long sec) {
        return taskLocal.containsKey(sec) || storage.containsKey(sec);
    }

    public FarLandsBlockLightEngine(LightChunkGetter chunkSource) {
        this(chunkSource, new FarLandsDataLayerStorage());
    }

    /** 服务端共享 storage 构造（多个引擎实例共享同一仓库，供并行任务池化复用）。 */
    FarLandsBlockLightEngine(LightChunkGetter chunkSource, FarLandsDataLayerStorage storage) {
        this.chunkSource = chunkSource;
        this.storage = storage;
        Arrays.fill(lastChunkKeys, ChunkPos.INVALID_CHUNK_POS);
    }

    // ==================== 公共 API ====================

    public int getLightValue(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        DataLayer dl = storage.get(sec);
        return dl == null ? 0 : dl.get(bp.x & 15, bp.y & 15, bp.z & 15);
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

    public DataLayer getOrCreate(long sectionKey, long chunkKey) {
        return storage.getOrCreate(sectionKey, chunkKey);
    }

    public void setDataLayer(long sectionKey, DataLayer layer, long chunkKey) {
        storage.put(sectionKey, layer, chunkKey);
    }

    // ==================== 服务端 per-chunk 任务入口 ====================

    /**
     * 处理一个 chunk 的方块变化 + section 变化。任务独占本引擎实例（单线程），
     * 队列/affectedSections 在任务内消耗。先建层（section 变化）再 checkNode。
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

    /** 服务端 chunk 卸载清理：按索引移除该 chunk 全部光照层（O(sections/chunk)）。 */
    public void removeChunk(ChunkPos pos) {
        storage.removeChunk(ChunkPos.asLong(pos.x, pos.z));
    }

    // ==================== runLightUpdates ====================

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
            chunkSource.onLightUpdate(LightLayer.BLOCK, SectionPos.of(sp.x, sp.y, sp.z));
        }
        affectedSections.clear();
    }

    // ==================== 队列循环 ====================

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

    // ==================== 存储读写 ====================

    private int getStoredLevel(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        DataLayer dl = localGet(sec);
        return dl == null ? 0 : dl.get(bp.x & 15, bp.y & 15, bp.z & 15);
    }

    private void setStoredLevel(long packedPos, int level) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        DataLayer dl = localGetOrCreate(sec, ChunkPos.asLong(bp.x >> 4, bp.z >> 4));
        dl.set(bp.x & 15, bp.y & 15, bp.z & 15, level);
        markAffected(packedPos);
    }

    // ==================== checkNode（移植自 BlockLightEngine L26-43）====================

    private void checkNode(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        if (!localContains(sec)) return;

        mutablePos.set(bp.x, bp.y, bp.z);
        BlockState state = getState(mutablePos);
        int emission = getEmission(packedPos, state);
        int cur = getStoredLevel(packedPos);

        if (emission < cur) {
            setStoredLevel(packedPos, 0);
            enqueueDecrease(packedPos, LightEngine.QueueEntry.decreaseAllDirections(cur));
        } else {
            enqueueDecrease(packedPos, PULL_LIGHT_IN_ENTRY);
        }
        if (emission > 0) {
            enqueueIncrease(packedPos,
                    LightEngine.QueueEntry.increaseLightFromEmission(emission, isEmptyShape(state)));
        }
    }

    // ==================== propagateIncrease（移植自 BlockLightEngine L46-79）====================

    private void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockstate = null;
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
            // 形状检查跳过：仅当两方块都 isEmptyShape 才跳过。
            if (!(isEmptyShape(blockstate) && isEmptyShape(bs1))) {
                if (shapeOccludes(packedPos, blockstate, nPos, bs1, dir)) continue;
            }

            if (ndl == null) {
                ndl = localGetOrCreate(nSec, ChunkPos.asLong(nbpX >> 4, nbpZ >> 4));
            }
            ndl.set(nbpX & 15, nbpY & 15, nbpZ & 15, afterOpacity);
            markAffected(nPos);
            if (afterOpacity > 1) {
                enqueueIncrease(nPos, LightEngine.QueueEntry.increaseSkipOneDirection(
                        afterOpacity, isEmptyShape(bs1), dir.getOpposite()));
            }
        }
    }

    // ==================== propagateDecrease（移植自 BlockLightEngine L82-109）====================

    private void propagateDecrease(long packedPos, long lightLevel) {
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
                mutablePos.set(nbpX, nbpY, nbpZ);
                BlockState state = getState(mutablePos);
                int emission = getEmission(nPos, state);
                ndl.set(nbpX & 15, nbpY & 15, nbpZ & 15, 0);
                markAffected(nPos);
                if (emission < nVal) {
                    enqueueDecrease(nPos,
                            LightEngine.QueueEntry.decreaseSkipOneDirection(nVal, dir.getOpposite()));
                }
                if (emission > 0) {
                    enqueueIncrease(nPos,
                            LightEngine.QueueEntry.increaseLightFromEmission(emission, isEmptyShape(state)));
                }
            } else {
                enqueueIncrease(nPos,
                        LightEngine.QueueEntry.increaseOnlyOneDirection(nVal, false, dir.getOpposite()));
            }
        }
    }

    // ==================== emission（移植自 BlockLightEngine L111-114）====================

    private int getEmission(long packedPos, BlockState state) {
        int emission = state.getLightEmission(chunkSource.getLevel(), mutablePos);
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        return emission > 0 && localContains(sec) ? emission : 0;
    }

    // ==================== propagateLightSources（移植自 BlockLightEngine L117-126）====================

    @Override
    public void propagateLightSources(ChunkPos pos) {
        synchronized (queueLock) {
            LightChunk lc = getChunk(pos.x, pos.z);
        if (lc != null) {
            lc.findBlockLightSources((blockPos, state) -> {
                int emission = state.getLightEmission(chunkSource.getLevel(), blockPos);
                enqueueIncrease(blockPos.asLong(),
                        LightEngine.QueueEntry.increaseLightFromEmission(emission, isEmptyShape(state)));
            });
            }
        }
    }

    @Override
    public void updateSectionStatus(SectionPos pos, boolean isEmpty) {
        long chunkKey = ChunkPos.asLong(pos.x(), pos.z());
        if (isEmpty) {
            // 不删层：层保留到 chunk 卸载（removeChunk 清理）。立即删层会让
            // checkNode 的 localContains 早退 → decrease 不传播 → 相邻 section
            // 光残留（打掉方块 section 变空场景，跨任务时序无法保证 checkNode 先于删层）。
        } else {
            storage.getOrCreate(pos.asLong(), chunkKey);
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
        // 无操作 —— 方块光没有列启用/禁用
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

    private static boolean isEmptyShape(BlockState state) {
        return !state.canOcclude() || !state.useShapeForLightOcclusion();
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
