package com.inf.farlands.light;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;

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
 * Block-light engine. Propagation ported from vanilla
 * {@code BlockLightEngine} with mechanical renames:
 * {@code storingLightForSection} → {@code storage.containsKey},
 * {@code getStoredLevel} → direct {@code DataLayer.get},
 * {@code setStoredLevel} → direct {@code DataLayer.set}.
 *
 * <p>Single-layer {@link FarLandsDataLayerStorage} replaces vanilla's
 * queued/updating/visible triple-buffer. No Y-range limits.
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

    public FarLandsBlockLightEngine(LightChunkGetter chunkSource) {
        this.chunkSource = chunkSource;
        this.storage = new FarLandsDataLayerStorage();
        Arrays.fill(lastChunkKeys, ChunkPos.INVALID_CHUNK_POS);
    }

    // ==================== public API ====================

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

    public DataLayer getOrCreate(long sectionKey) {
        return storage.getOrCreate(sectionKey);
    }

    public void setDataLayer(long sectionKey, DataLayer layer) {
        storage.put(sectionKey, layer);
    }

    /** 服务端 chunk 卸载清理：side-channel 反查移除该 chunk 全部光照层（storage 不再随卸载增长）。 */
    public void removeChunk(ChunkPos pos) {
        int cx = pos.x, cz = pos.z;
        storage.removeIf(key -> {
            IntSectionPos sp = HashUtil.getSection(key);
            return sp != null && sp.x == cx && sp.z == cz;
        });
    }

    // ==================== runLightUpdates ====================

    @Override
    public boolean hasLightWork() {
        return !blockNodesToCheck.isEmpty() || !decreaseQueue.isEmpty() || !increaseQueue.isEmpty();
    }

    @Override
    public int runLightUpdates() {
        synchronized (queueLock) {
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

    // ==================== queue loops ====================

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

    // ==================== storage read/write ====================

    private int getStoredLevel(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        DataLayer dl = storage.get(sec);
        return dl == null ? 0 : dl.get(bp.x & 15, bp.y & 15, bp.z & 15);
    }

    private void setStoredLevel(long packedPos, int level) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        DataLayer dl = storage.getOrCreate(sec);
        dl.set(bp.x & 15, bp.y & 15, bp.z & 15, level);
        markAffected(packedPos);
    }

    // ==================== checkNode (ported from BlockLightEngine L26-43) ====================

    private void checkNode(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        if (!storage.containsKey(sec)) return;

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

    // ==================== propagateIncrease (ported from BlockLightEngine L46-79) ====================

    private void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockstate = null;
        IntBlockPos src = IntBlockPos.getBlockPos(packedPos);

        for (Direction dir : PROPAGATION_DIRECTIONS) {
            if (!LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, dir)) continue;

            long nPos = BlockPos.offset(packedPos, dir);
            IntBlockPos nbp = IntBlockPos.getBlockPos(nPos);
            long nSec = HashUtil.hashSection((long) (nbp.x >> 4), (long) (nbp.y >> 4), (long) (nbp.z >> 4));
            DataLayer ndl = storage.get(nSec); // 按需建层：先 get，需要写时才 getOrCreate（消除传播白建空层积累）
            int nVal = ndl == null ? 0 : ndl.get(nbp.x & 15, nbp.y & 15, nbp.z & 15);

            int reduced = lightLevel - 1;
            if (reduced <= nVal) continue;

            mutablePos.set(nbp.x, nbp.y, nbp.z);
            BlockState bs1 = getState(mutablePos);
            int afterOpacity = lightLevel - getOpacity(bs1, mutablePos);
            if (afterOpacity <= nVal) continue;

            if (blockstate == null) {
                blockstate = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                        ? Blocks.AIR.defaultBlockState()
                        : getState(mutablePos.set(src.x, src.y, src.z));
            }
            if (shapeOccludes(packedPos, blockstate, nPos, bs1, dir)) continue;

            if (ndl == null) {
                ndl = storage.getOrCreate(nSec);
            }
            ndl.set(nbp.x & 15, nbp.y & 15, nbp.z & 15, afterOpacity);
            markAffected(nPos);
            if (afterOpacity > 1) {
                enqueueIncrease(nPos, LightEngine.QueueEntry.increaseSkipOneDirection(
                        afterOpacity, isEmptyShape(bs1), dir.getOpposite()));
            }
        }
    }

    // ==================== propagateDecrease (ported from BlockLightEngine L82-109) ====================

    private void propagateDecrease(long packedPos, long lightLevel) {
        int fromLevel = LightEngine.QueueEntry.getFromLevel(lightLevel);

        for (Direction dir : PROPAGATION_DIRECTIONS) {
            if (!LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, dir)) continue;

            long nPos = BlockPos.offset(packedPos, dir);
            IntBlockPos nbp = IntBlockPos.getBlockPos(nPos);
            long nSec = HashUtil.hashSection((long) (nbp.x >> 4), (long) (nbp.y >> 4), (long) (nbp.z >> 4));
            if (!storage.containsKey(nSec)) continue;

            DataLayer ndl = storage.get(nSec);
            if (ndl == null) continue;
            int nVal = ndl.get(nbp.x & 15, nbp.y & 15, nbp.z & 15);
            if (nVal == 0) continue;

            if (nVal <= fromLevel - 1) {
                mutablePos.set(nbp.x, nbp.y, nbp.z);
                BlockState state = getState(mutablePos);
                int emission = getEmission(nPos, state);
                ndl.set(nbp.x & 15, nbp.y & 15, nbp.z & 15, 0);
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

    // ==================== emission (ported from BlockLightEngine L111-114) ====================

    private int getEmission(long packedPos, BlockState state) {
        int emission = state.getLightEmission(chunkSource.getLevel(), mutablePos);
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        long sec = HashUtil.hashSection((long) (bp.x >> 4), (long) (bp.y >> 4), (long) (bp.z >> 4));
        return emission > 0 && storage.containsKey(sec) ? emission : 0;
    }

    // ==================== propagateLightSources (ported from BlockLightEngine L117-126) ====================

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
        if (isEmpty) {
            storage.remove(pos.asLong());
        } else {
            storage.getOrCreate(pos.asLong());
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
        // no-op — block light has no column enable/disable
    }

    // ==================== chunk cache ====================

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

    // ==================== block access ====================

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
