package com.inf.farlands.light;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import com.inf.farlands.WindowedChunk;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
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
 * Sky-light engine with sub-region model. No Y-range assumptions.
 *
 * <p>Propagation algorithm ported from vanilla {@code SkyLightEngine} with
 * three mechanical renames: {@code storingLightForSection} → {@code
 * storage.containsKey}, {@code getStoredLevel} → direct {@code DataLayer.get},
 * {@code setStoredLevel} → direct {@code DataLayer.set}.
 *
 * <p>Sky light is partitioned into 256-block sub-regions (16 sections each).
 * The top section of each active sub-region is seeded with
 * {@code DataLayer(15)}. Propagation stops at sub-region boundaries.
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

    // ---- empty chunk sources (vanilla L21) ----
    private final ChunkSkyLightSources emptyChunkSources;

    public FarLandsSkyLightEngine(LightChunkGetter chunkSource) {
        this.chunkSource = chunkSource;
        this.storage = new FarLandsDataLayerStorage();
        this.topSections = new ConcurrentHashMap<>();
        this.emptyChunkSources = new ChunkSkyLightSources(chunkSource.getLevel());
        Arrays.fill(lastChunkKeys, ChunkPos.INVALID_CHUNK_POS);
    }

    // ==================== public API ====================

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

        // Search upward within sub-region
        int subRegion = sy >> 4;
        int topSY = subRegion * 16 + 15;
        for (int s = sy + 1; s <= topSY; s++) {
            dl = storage.get(HashUtil.hashSection((long) cx, (long) s, (long) cz));
            if (dl != null) return dl.get(rx, 0, rz); // 读层底部行（对齐 vanilla flatIndex y=0——遮挡列读 0 防幽灵亮）
        }

        // Above all data or inactive sub-region — virtual sky ceiling
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
            dl.fill(15);
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

    // ==================== runLightUpdates support ====================

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
            chunkSource.onLightUpdate(LightLayer.SKY, SectionPos.of(sp.x, sp.y, sp.z));
        }
        affectedSections.clear();
    }

    // ==================== propagation ====================

    // ported from LightEngine L164-181
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

    // ported from LightEngine L184-193
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

    // ---- propagateIncrease (ported from SkyLightEngine L139-175) ----

    private void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        BlockState blockstate = null;
        int emptyBelow = countEmptySectionsBelowIfAtBorder(packedPos);
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
            propagateFromEmptySections(nPos, dir, afterOpacity, true, emptyBelow);
        }
    }

    // ---- propagateDecrease (ported from SkyLightEngine L178-199) ----

    private void propagateDecrease(long packedPos, long lightLevel) {
        int emptyBelow = countEmptySectionsBelowIfAtBorder(packedPos);
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
                ndl.set(nbp.x & 15, nbp.y & 15, nbp.z & 15, 0);
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

        int lowestY = getLowestSourceY(x, z);
        if (lowestY != Integer.MAX_VALUE) {
            updateSourcesInColumn(x, z, lowestY);
        }

        if (storage.containsKey(secKey)) {
            if (y >= lowestY) {
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
                && !storage.containsKey(HashUtil.hashSection((long) cx, (long) (sy - count - 1), (long) cz))) {
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
            DataLayer dl = storage.getOrCreate(secKey);
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

    // ==================== sky sources ====================

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
            // int by++ 溢出为负 → 无限循环 → DataLayer.get 越界 CTD（#24/#36/#38 同族）。
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
        int highestLowest = sources.getHighestLowestSourceY() - 1;
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
                        if (lowest0 > maxY) continue;

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
