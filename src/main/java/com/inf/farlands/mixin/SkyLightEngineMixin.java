package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

@Mixin(SkyLightEngine.class)
public abstract class SkyLightEngineMixin {

    @Shadow private BlockPos.MutableBlockPos mutablePos;
    @Shadow protected abstract int getLowestSourceY(int x, int z, int defaultReturnValue);
    @Shadow private void updateSourcesInColumn(int x, int z, int lowestY) {}
    @Shadow private static boolean crossedSectionEdge(Direction direction, int x, int z) { return false; }
    @Shadow private static long REMOVE_SKY_SOURCE_ENTRY;
    @Shadow private static long ADD_SKY_SOURCE_ENTRY;
    @Shadow private static long REMOVE_TOP_SKY_SOURCE_ENTRY;

    // --- Inherited members (reflection — no refmap on final class) ---
    private static final java.lang.reflect.Field F_STORAGE, F_PROP_DIRS, F_PULL_LIGHT;
    private static final java.lang.reflect.Method M_ENQUEUE_DEC, M_ENQUEUE_INC;
    static {
        try {
            Class<?> le = LightEngine.class;
            F_STORAGE = le.getDeclaredField("storage"); F_STORAGE.setAccessible(true);
            F_PROP_DIRS = le.getDeclaredField("PROPAGATION_DIRECTIONS"); F_PROP_DIRS.setAccessible(true);
            F_PULL_LIGHT = le.getDeclaredField("PULL_LIGHT_IN_ENTRY"); F_PULL_LIGHT.setAccessible(true);
            M_ENQUEUE_DEC = le.getDeclaredMethod("enqueueDecrease", Long.TYPE, Long.TYPE); M_ENQUEUE_DEC.setAccessible(true);
            M_ENQUEUE_INC = le.getDeclaredMethod("enqueueIncrease", Long.TYPE, Long.TYPE); M_ENQUEUE_INC.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private SkyLightSectionStorage refStorage() { try { return (SkyLightSectionStorage) F_STORAGE.get(this); } catch (Exception e) { throw new RuntimeException(e); } }
    private Direction[] propDirs() { try { return (Direction[]) F_PROP_DIRS.get(null); } catch (Exception e) { throw new RuntimeException(e); } }
    private long pullLightEntry() { try { return F_PULL_LIGHT.getLong(null); } catch (Exception e) { throw new RuntimeException(e); } }
    private void callEnqueueDec(long p, long q) { try { M_ENQUEUE_DEC.invoke(this, p, q); } catch (Exception e) { throw new RuntimeException(e); } }
    private void callEnqueueInc(long p, long q) { try { M_ENQUEUE_INC.invoke(this, p, q); } catch (Exception e) { throw new RuntimeException(e); } }

    // --- Reflection bridges for inherited methods (no refmap → can't @Shadow on final class) ---
    private static final java.lang.reflect.Method M_GET_STATE;
    private static final java.lang.reflect.Method M_GET_OPACITY;
    private static final java.lang.reflect.Method M_SHAPE_OCCLUDES;
    private static final java.lang.reflect.Method M_IS_EMPTY_SHAPE;
    private static final java.lang.reflect.Method M_IS_SOURCE_LEVEL;
    static {
        try {
            Class<?> le = LightEngine.class;
            M_GET_STATE = le.getDeclaredMethod("getState", BlockPos.class); M_GET_STATE.setAccessible(true);
            M_GET_OPACITY = le.getDeclaredMethod("getOpacity", BlockState.class, BlockPos.class); M_GET_OPACITY.setAccessible(true);
            M_SHAPE_OCCLUDES = le.getDeclaredMethod("shapeOccludes", Long.TYPE, BlockState.class, Long.TYPE, BlockState.class, Direction.class); M_SHAPE_OCCLUDES.setAccessible(true);
            M_IS_EMPTY_SHAPE = le.getDeclaredMethod("isEmptyShape", BlockState.class); M_IS_EMPTY_SHAPE.setAccessible(true);
            M_IS_SOURCE_LEVEL = SkyLightEngine.class.getDeclaredMethod("isSourceLevel", Integer.TYPE); M_IS_SOURCE_LEVEL.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private BlockState callGetState(BlockPos p) { try { return (BlockState) M_GET_STATE.invoke(this, p); } catch (Exception e) { throw new RuntimeException(e); } }
    private int callGetOpacity(BlockState s, BlockPos p) { try { return (int) M_GET_OPACITY.invoke(this, s, p); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callShapeOccludes(long a, BlockState sa, long b, BlockState sb, Direction d) { try { return (boolean) M_SHAPE_OCCLUDES.invoke(this, a, sa, b, sb, d); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callIsEmptyShape(BlockState s) { try { return (boolean) M_IS_EMPTY_SHAPE.invoke(null, s); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callIsSourceLevel(int l) { try { return (boolean) M_IS_SOURCE_LEVEL.invoke(null, l); } catch (Exception e) { throw new RuntimeException(e); } }

    // --- Reflection bridges for protected LayerLightSectionStorage methods ---
    // Called via SkyLightSectionStorage which extends LayerLightSectionStorage —
    // the concrete class IS in the target package at runtime, but the
    // compiler rejects the call from our package. Reflection bypasses this.

    private static final java.lang.reflect.Method STORAGE_LIGHT_ON_IN_SECTION;
    private static final java.lang.reflect.Method STORAGE_STORING_LIGHT_FOR_SECTION;
    private static final java.lang.reflect.Method STORAGE_GET_STORED_LEVEL;
    private static final java.lang.reflect.Method STORAGE_SET_STORED_LEVEL;
    private static final java.lang.reflect.Method STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW;
    private static final java.lang.reflect.Method STORAGE_IS_ABOVE_DATA;
    static {
        try {
            STORAGE_LIGHT_ON_IN_SECTION =
                net.minecraft.world.level.lighting.LayerLightSectionStorage.class
                    .getDeclaredMethod("lightOnInSection", Long.TYPE);
            STORAGE_LIGHT_ON_IN_SECTION.setAccessible(true);
            STORAGE_STORING_LIGHT_FOR_SECTION =
                net.minecraft.world.level.lighting.LayerLightSectionStorage.class
                    .getDeclaredMethod("storingLightForSection", Long.TYPE);
            STORAGE_STORING_LIGHT_FOR_SECTION.setAccessible(true);
            STORAGE_GET_STORED_LEVEL =
                net.minecraft.world.level.lighting.LayerLightSectionStorage.class
                    .getDeclaredMethod("getStoredLevel", Long.TYPE);
            STORAGE_GET_STORED_LEVEL.setAccessible(true);
            STORAGE_SET_STORED_LEVEL =
                net.minecraft.world.level.lighting.LayerLightSectionStorage.class
                    .getDeclaredMethod("setStoredLevel", Long.TYPE, Integer.TYPE);
            STORAGE_SET_STORED_LEVEL.setAccessible(true);
            STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW =
                net.minecraft.world.level.lighting.SkyLightSectionStorage.class
                    .getDeclaredMethod("hasLightDataAtOrBelow", Integer.TYPE);
            STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW.setAccessible(true);
            STORAGE_IS_ABOVE_DATA =
                net.minecraft.world.level.lighting.SkyLightSectionStorage.class
                    .getDeclaredMethod("isAboveData", Long.TYPE);
            STORAGE_IS_ABOVE_DATA.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean storageLightOnInSection(long sec) {
        try { return (boolean) STORAGE_LIGHT_ON_IN_SECTION.invoke(refStorage(), sec); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private boolean storageStoringLightForSection(long sec) {
        try { return (boolean) STORAGE_STORING_LIGHT_FOR_SECTION.invoke(refStorage(), sec); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private int storageGetStoredLevel(long pos) {
        try { return (int) STORAGE_GET_STORED_LEVEL.invoke(refStorage(), pos); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private void storageSetStoredLevel(long pos, int level) {
        try { STORAGE_SET_STORED_LEVEL.invoke(refStorage(), pos, level); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private boolean storageHasLightDataAtOrBelow(int y) {
        try { return (boolean) STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW.invoke(refStorage(), y); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    private boolean storageIsAboveData(long sec) {
        try { return (boolean) STORAGE_IS_ABOVE_DATA.invoke(refStorage(), sec); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    // === method3: block-level key → IntBlockPos ===
    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp
            : new IntBlockPos(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
    }
    // === method3: section-level key → IntSectionPos ===
    private static IntSectionPos getSectionPos(long key) {
        IntSectionPos sp = HashUtil.getSection(key);
        return sp != null ? sp
            : new IntSectionPos(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
    }

    // === checkNode ===

    @Overwrite
    protected void checkNode(long levelPos) {
        IntBlockPos bp = getBlockPos(levelPos);
        checkNodeWithPos(bp.x, bp.y, bp.z, levelPos);
    }

    private void checkNodeWithPos(int x, int y, int z, long levelPos) {
        long l = SectionPos.blockToSection(levelPos);
        int i1 = storageLightOnInSection(l) ? this.getLowestSourceY(x, z, Integer.MAX_VALUE) : Integer.MAX_VALUE;
        if (i1 != Integer.MAX_VALUE) {
            this.updateSourcesInColumn(x, z, i1);
        }

        if (storageStoringLightForSection(l)) {
            boolean flag = y >= i1;
            if (flag) {
                callEnqueueDec(levelPos, REMOVE_SKY_SOURCE_ENTRY);
                callEnqueueInc(levelPos, ADD_SKY_SOURCE_ENTRY);
            } else {
                int j1 = storageGetStoredLevel(levelPos);
                if (j1 > 0) {
                    storageSetStoredLevel(levelPos, 0);
                    callEnqueueDec(levelPos, LightEngine.QueueEntry.decreaseAllDirections(j1));
                } else {
                    callEnqueueDec(levelPos, pullLightEntry());
                }
            }
        }
    }

    // === countEmptySectionsBelowIfAtBorder ===

    @Overwrite
    private int countEmptySectionsBelowIfAtBorder(long packedPos) {
        IntBlockPos bp = getBlockPos(packedPos);
        return countEmptySectionsBelowIfAtBorder(bp.x, bp.y, bp.z);
    }

    private int countEmptySectionsBelowIfAtBorder(int x, int y, int z) {
        int j = SectionPos.sectionRelative(y);
        if (j != 0) return 0;
        int i1 = SectionPos.sectionRelative(x);
        int j1 = SectionPos.sectionRelative(z);
        if (i1 != 0 && i1 != 15 && j1 != 0 && j1 != 15) return 0;
        int k1 = SectionPos.blockToSectionCoord(x);
        int l1 = SectionPos.blockToSectionCoord(y);
        int i2 = SectionPos.blockToSectionCoord(z);
        int j2 = 0;
        while (!storageStoringLightForSection(HashUtil.hashSection(k1, l1 - j2 - 1, i2))
                && storageHasLightDataAtOrBelow(l1 - j2 - 1)) {
            j2++;
        }
        return j2;
    }

    // === propagateFromEmptySections ===

    @Overwrite
    private void propagateFromEmptySections(long packedPos, Direction direction, int level,
                                            boolean shouldIncrease, int emptySections) {
        IntBlockPos bp = getBlockPos(packedPos);
        propagateFromEmptySections(bp.x, bp.y, bp.z, direction, level, shouldIncrease, emptySections);
    }

    private void propagateFromEmptySections(int x, int y, int z, Direction direction, int level,
                                            boolean shouldIncrease, int emptySections) {
        if (emptySections == 0) return;
        if (!crossedSectionEdge(direction, SectionPos.sectionRelative(x), SectionPos.sectionRelative(z))) return;

        int l = SectionPos.blockToSectionCoord(x);
        int i1 = SectionPos.blockToSectionCoord(z);
        int j1 = SectionPos.blockToSectionCoord(y) - 1;
        int k1 = j1 - emptySections + 1;

        while (j1 >= k1) {
            if (!storageStoringLightForSection(HashUtil.hashSection(l, j1, i1))) {
                j1--;
            } else {
                int l1 = SectionPos.sectionToBlockCoord(j1);
                for (int i2 = 15; i2 >= 0; i2--) {
                    long j2 = BlockPos.asLong(x, l1 + i2, z);
                    HashUtil.putBlock(j2, new IntBlockPos(x, l1 + i2, z));
                    if (shouldIncrease) {
                        storageSetStoredLevel(j2, level);
                        if (level > 1) {
                            callEnqueueInc(j2,
                                LightEngine.QueueEntry.increaseSkipOneDirection(level, true, direction.getOpposite()));
                        }
                    } else {
                        storageSetStoredLevel(j2, 0);
                        callEnqueueDec(j2,
                            LightEngine.QueueEntry.decreaseSkipOneDirection(level, direction.getOpposite()));
                    }
                }
                j1--;
            }
        }
    }

    // === propagateIncrease ===

    @Overwrite
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        IntBlockPos src = getBlockPos(packedPos); // CHM read for source (from queue)
        int sx = src.x, sy = src.y, sz = src.z;
        int i = thisCountEmptySectionsBelowIfAtBorder(sx, sy, sz);
        net.minecraft.world.level.block.state.BlockState blockstate = null;
        for (Direction direction : propDirs()) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, direction)) {
                int nx = sx + direction.getStepX();
                int ny = sy + direction.getStepY();
                int nz = sz + direction.getStepZ();
                // Direct DataLayer access — bypass CHM entirely
                long nSec = HashUtil.hashSection(nx>>4, ny>>4, nz>>4);
                int rx = SectionPos.sectionRelative(nx), ry = SectionPos.sectionRelative(ny), rz = SectionPos.sectionRelative(nz);
                if (storageStoringLightForSection(nSec)) {
                    DataLayer dl = HashUtil.callGetDataLayer(refStorage(), nSec, true);
                    if (dl == null) continue;
                    int k = dl.get(rx, ry, rz);
                    int l = lightLevel - 1;
                    if (l > k) {
                        this.mutablePos.set(nx, ny, nz);
                        net.minecraft.world.level.block.state.BlockState blockstate1 = callGetState(this.mutablePos);
                        int i1 = lightLevel - callGetOpacity(blockstate1, this.mutablePos);
                        if (i1 > k) {
                            if (blockstate == null) {
                                blockstate = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                                    ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                                    : callGetState(this.mutablePos.set(sx, sy, sz));
                            }
                            long nKey = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
                            if (!callShapeOccludes(packedPos, blockstate, nKey, blockstate1, direction)) {
                                dl.set(rx, ry, rz, i1); // direct DataLayer write
                                if (i1 > 1) {
                                    HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz)); // put only for queue downstream
                                    callEnqueueInc(nKey, LightEngine.QueueEntry.increaseSkipOneDirection(i1, callIsEmptyShape(blockstate1), direction.getOpposite()));
                                }
                                propagateFromEmptySections(nx, ny, nz, direction, i1, true, i);
                            }
                        }
                    }
                }
            }
        }
    }

    // === propagateDecrease ===

    @Overwrite
    protected void propagateDecrease(long packedPos, long lightLevel) {
        IntBlockPos src = getBlockPos(packedPos);
        int sx = src.x, sy = src.y, sz = src.z;
        int i = thisCountEmptySectionsBelowIfAtBorder(sx, sy, sz);
        int j = LightEngine.QueueEntry.getFromLevel(lightLevel);
        for (Direction direction : propDirs()) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, direction)) {
                int nx = sx + direction.getStepX();
                int ny = sy + direction.getStepY();
                int nz = sz + direction.getStepZ();
                long nSec = HashUtil.hashSection(nx>>4, ny>>4, nz>>4);
                int rx = SectionPos.sectionRelative(nx), ry = SectionPos.sectionRelative(ny), rz = SectionPos.sectionRelative(nz);
                if (storageStoringLightForSection(nSec)) {
                    DataLayer dl = HashUtil.callGetDataLayer(refStorage(), nSec, true);
                    if (dl == null) continue;
                    int k = dl.get(rx, ry, rz);
                    if (k != 0) {
                        if (k <= j - 1) {
                            dl.set(rx, ry, rz, 0);
                            long nKey = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
                            HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz));
                            callEnqueueDec(nKey, LightEngine.QueueEntry.decreaseSkipOneDirection(k, direction.getOpposite()));
                            propagateFromEmptySections(nx, ny, nz, direction, k, false, i);
                        } else {
                            long nKey = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
                            HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz));
                            callEnqueueInc(nKey, LightEngine.QueueEntry.increaseOnlyOneDirection(k, false, direction.getOpposite()));
                        }
                    }
                }
            }
        }
    }

    // Inlined version of countEmptySectionsBelowIfAtBorder that takes int coords
    // (avoids double CHM lookup from the propagate methods)
    private int thisCountEmptySectionsBelowIfAtBorder(int x, int y, int z) {
        int j = SectionPos.sectionRelative(y);
        if (j != 0) return 0;
        int i1 = SectionPos.sectionRelative(x);
        int j1 = SectionPos.sectionRelative(z);
        if (i1 != 0 && i1 != 15 && j1 != 0 && j1 != 15) return 0;
        int k1 = SectionPos.blockToSectionCoord(x);
        int l1 = SectionPos.blockToSectionCoord(y);
        int i2 = SectionPos.blockToSectionCoord(z);
        int j2 = 0;
        while (!storageStoringLightForSection(HashUtil.hashSection(k1, l1 - j2 - 1, i2))
                && storageHasLightDataAtOrBelow(l1 - j2 - 1)) {
            j2++;
        }
        return j2;
    }

    // === addSourcesAbove: eliminate SectionPos.y(i1) ===

    // === removeSourcesBelow: explicit CHM put for downstream getBlockPos ===

    @Overwrite
    private void removeSourcesBelow(int x, int z, int minY, int bottomSectionY) {
        if (minY > bottomSectionY) {
            int i = SectionPos.blockToSectionCoord(x);
            int j = SectionPos.blockToSectionCoord(z);
            int k = minY - 1;
            int rx = SectionPos.sectionRelative(x), rz = SectionPos.sectionRelative(z);
            for (int l = SectionPos.blockToSectionCoord(k); storageHasLightDataAtOrBelow(l); l--) {
                long nSec = HashUtil.hashSection(i, l, j);
                if (storageStoringLightForSection(nSec)) {
                    DataLayer dl = HashUtil.callGetDataLayer(refStorage(), nSec, true);
                    if (dl == null) continue;
                    int i1 = SectionPos.sectionToBlockCoord(l);
                    int j1 = i1 + 15;
                    for (int k1 = Math.min(j1, k); k1 >= i1; k1--) {
                        int ry = SectionPos.sectionRelative(k1);
                        int level = dl.get(rx, ry, rz);
                        if (callIsSourceLevel(level)) return;
                        dl.set(rx, ry, rz, 0);
                        long l1 = BlockPos.asLong(x, k1, z);
                        HashUtil.putBlock(l1, new IntBlockPos(x, k1, z));
                        callEnqueueDec(l1, k1 == minY - 1 ? REMOVE_TOP_SKY_SOURCE_ENTRY : REMOVE_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    @Overwrite
    private void addSourcesAbove(int x, int z, int maxY, int bottomSectionY) {
        int i = SectionPos.blockToSectionCoord(x);
        int j = SectionPos.blockToSectionCoord(z);
        int k = Math.max(
            Math.max(this.getLowestSourceY(x - 1, z, Integer.MIN_VALUE), this.getLowestSourceY(x + 1, z, Integer.MIN_VALUE)),
            Math.max(this.getLowestSourceY(x, z - 1, Integer.MIN_VALUE), this.getLowestSourceY(x, z + 1, Integer.MIN_VALUE))
        );
        int l = Math.max(maxY, bottomSectionY);
        int secY = SectionPos.blockToSectionCoord(l);
        for (long i1 = HashUtil.hashSection(i, secY, j);
             !storageIsAboveData(i1);
             i1 = SectionPos.offset(i1, Direction.UP), secY++) {
            if (storageStoringLightForSection(i1)) {
                int j1 = SectionPos.sectionToBlockCoord(secY);
                int k1 = j1 + 15;
                for (int l1 = Math.max(j1, l); l1 <= k1; l1++) {
                    long i2 = BlockPos.asLong(x, l1, z);
                    HashUtil.putBlock(i2, new IntBlockPos(x, l1, z));
                    if (callIsSourceLevel(storageGetStoredLevel(i2))) return;
                    storageSetStoredLevel(i2, 15);
                    if (l1 < k || l1 == maxY) {
                        callEnqueueInc(i2, ADD_SKY_SOURCE_ENTRY);
                    }
                }
            }
        }
    }

    // === propagateLightSources: explicit CHM put for chunk init sky light ===
    private net.minecraft.world.level.chunk.LightChunkGetter getChunkSource() {
        try {
            java.lang.reflect.Field f = LightEngine.class.getDeclaredField("chunkSource");
            f.setAccessible(true);
            return (net.minecraft.world.level.chunk.LightChunkGetter) f.get(this);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    @Shadow private ChunkSkyLightSources emptyChunkSources;

    // --- Additional reflection bridges for propagateLightSources ---
    private static final java.lang.reflect.Method M_SET_LIGHT_ENABLED;
    private static final java.lang.reflect.Method M_GET_TOP_SECTION_Y;
    private static final java.lang.reflect.Method M_GET_BOTTOM_SECTION_Y;
    static {
        try {
            M_SET_LIGHT_ENABLED = LayerLightSectionStorage.class.getDeclaredMethod("setLightEnabled", Long.TYPE, Boolean.TYPE); M_SET_LIGHT_ENABLED.setAccessible(true);
            M_GET_TOP_SECTION_Y = SkyLightSectionStorage.class.getDeclaredMethod("getTopSectionY", Long.TYPE); M_GET_TOP_SECTION_Y.setAccessible(true);
            M_GET_BOTTOM_SECTION_Y = SkyLightSectionStorage.class.getDeclaredMethod("getBottomSectionY"); M_GET_BOTTOM_SECTION_Y.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private void storageSetLightEnabled(long s, boolean b) { try { M_SET_LIGHT_ENABLED.invoke(refStorage(), s, b); } catch (Exception e) { throw new RuntimeException(e); } }
    private int storageGetTopSectionY(long s) { try { return (int) M_GET_TOP_SECTION_Y.invoke(refStorage(), s); } catch (Exception e) { throw new RuntimeException(e); } }
    private int storageGetBottomSectionY() { try { return (int) M_GET_BOTTOM_SECTION_Y.invoke(refStorage()); } catch (Exception e) { throw new RuntimeException(e); } }

    @Overwrite
    public void propagateLightSources(net.minecraft.world.level.ChunkPos chunkPos) {
        long i = SectionPos.getZeroNode(chunkPos.x, chunkPos.z);
        storageSetLightEnabled(i, true);
        net.minecraft.world.level.lighting.ChunkSkyLightSources s0 = getChunkSourcesOrEmpty(chunkPos.x, chunkPos.z);
        net.minecraft.world.level.lighting.ChunkSkyLightSources s1 = getChunkSourcesOrEmpty(chunkPos.x, chunkPos.z - 1);
        net.minecraft.world.level.lighting.ChunkSkyLightSources s2 = getChunkSourcesOrEmpty(chunkPos.x, chunkPos.z + 1);
        net.minecraft.world.level.lighting.ChunkSkyLightSources s3 = getChunkSourcesOrEmpty(chunkPos.x - 1, chunkPos.z);
        net.minecraft.world.level.lighting.ChunkSkyLightSources s4 = getChunkSourcesOrEmpty(chunkPos.x + 1, chunkPos.z);
        int j = storageGetTopSectionY(i);
        int k = storageGetBottomSectionY();
        int l = SectionPos.sectionToBlockCoord(chunkPos.x);
        int i1 = SectionPos.sectionToBlockCoord(chunkPos.z);
        for (int j1 = j - 1; j1 >= k; j1--) {
            long k1 = HashUtil.hashSection(chunkPos.x, j1, chunkPos.z);
            DataLayer dl = callGetDLToWrite(k1);
            if (dl != null) {
                int l1 = SectionPos.sectionToBlockCoord(j1); int i2 = l1 + 15; boolean flag = false;
                for (int j2 = 0; j2 < 16; j2++) { for (int k2 = 0; k2 < 16; k2++) {
                    int l2 = s0.getLowestSourceY(k2, j2);
                    if (l2 <= i2) {
                        int i3 = j2 == 0 ? s1.getLowestSourceY(k2, 15) : s0.getLowestSourceY(k2, j2 - 1);
                        int j3 = j2 == 15 ? s2.getLowestSourceY(k2, 0) : s0.getLowestSourceY(k2, j2 + 1);
                        int k3 = k2 == 0 ? s3.getLowestSourceY(15, j2) : s0.getLowestSourceY(k2 - 1, j2);
                        int l3 = k2 == 15 ? s4.getLowestSourceY(0, j2) : s0.getLowestSourceY(k2 + 1, j2);
                        int i4 = Math.max(Math.max(i3, j3), Math.max(k3, l3));
                        for (int j4 = i2; j4 >= Math.max(l1, l2); j4--) {
                            dl.set(k2, SectionPos.sectionRelative(j4), j2, 15);
                            if (j4 == l2 || j4 < i4) {
                                int bx = l + k2, by = j4, bz = i1 + j2;
                                long k4 = BlockPos.asLong(bx, by, bz);
                                HashUtil.putBlock(k4, new IntBlockPos(bx, by, bz));
                                callEnqueueInc(k4, LightEngine.QueueEntry.increaseSkySourceInDirections(j4 == l2, j4 < i3, j4 < j3, j4 < k3, j4 < l3));
                            }
                        }
                        if (l2 < l1) flag = true;
                    }
                }}
                if (!flag) break;
            }
        }
    }
    private net.minecraft.world.level.lighting.ChunkSkyLightSources getChunkSourcesOrEmpty(int cx, int cz) {
        net.minecraft.world.level.chunk.LightChunk lc = getChunkSource().getChunkForLighting(cx, cz);
        return lc != null ? lc.getSkyLightSources() : emptyChunkSources;
    }
    private DataLayer callGetDLToWrite(long sec) {
        try {
            java.lang.reflect.Method m = net.minecraft.world.level.lighting.LayerLightSectionStorage.class.getDeclaredMethod("getDataLayerToWrite", Long.TYPE);
            m.setAccessible(true);
            return (DataLayer) m.invoke(refStorage(), sec);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
