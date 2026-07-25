package com.inf.farlands.mixin.threeInt;

import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.LightChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(SkyLightEngine.class)
public abstract class SkyLightEngineMixin {
    private static final Field F_STORAGE;
    private static final Field F_PULL_LIGHT;
    private static final Field F_PROP_DIRS;
    private static final Method STORAGE_LIGHT_ON_IN_SECTION;
    private static final Method STORAGE_STORING_LIGHT_FOR_SECTION;
    private static final Method STORAGE_GET_STORED_LEVEL;
    private static final Method STORAGE_SET_STORED_LEVEL;
    private static final Method STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW;
    private static final Method STORAGE_IS_ABOVE_DATA;
    private static final Method M_ENQUEUE_DEC;
    private static final Method M_ENQUEUE_INC;
    private static final Method M_GET_STATE;
    private static final Method M_GET_OPACITY;
    private static final Method M_SHAPE_OCCLUDES;
    private static final Method M_IS_EMPTY_SHAPE;
    private static final Method M_IS_SOURCE_LEVEL;
    private static final Method M_SET_LIGHT_ENABLED;
    private static final Method M_GET_TOP_SECTION_Y;
    private static final Method M_GET_BOTTOM_SECTION_Y;

    static {
        try {
            STORAGE_LIGHT_ON_IN_SECTION = LayerLightSectionStorage.class
                    .getDeclaredMethod("lightOnInSection", Long.TYPE);
            STORAGE_LIGHT_ON_IN_SECTION.setAccessible(true);
            STORAGE_STORING_LIGHT_FOR_SECTION = LayerLightSectionStorage.class
                    .getDeclaredMethod("storingLightForSection", Long.TYPE);
            STORAGE_STORING_LIGHT_FOR_SECTION.setAccessible(true);
            STORAGE_GET_STORED_LEVEL = LayerLightSectionStorage.class
                    .getDeclaredMethod("getStoredLevel", Long.TYPE);
            STORAGE_GET_STORED_LEVEL.setAccessible(true);
            STORAGE_SET_STORED_LEVEL = LayerLightSectionStorage.class
                    .getDeclaredMethod("setStoredLevel", Long.TYPE, Integer.TYPE);
            STORAGE_SET_STORED_LEVEL.setAccessible(true);
            STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW = net.minecraft.world.level.lighting.SkyLightSectionStorage.class
                    .getDeclaredMethod("hasLightDataAtOrBelow", Integer.TYPE);
            STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW.setAccessible(true);
            STORAGE_IS_ABOVE_DATA = net.minecraft.world.level.lighting.SkyLightSectionStorage.class
                    .getDeclaredMethod("isAboveData", Long.TYPE);
            STORAGE_IS_ABOVE_DATA.setAccessible(true);

            // --------------------------------

            Class<?> le = LightEngine.class;
            F_STORAGE = le.getDeclaredField("storage");
            F_STORAGE.setAccessible(true);
            F_PULL_LIGHT = le.getDeclaredField("PULL_LIGHT_IN_ENTRY");
            F_PULL_LIGHT.setAccessible(true);
            F_PROP_DIRS = le.getDeclaredField("PROPAGATION_DIRECTIONS");
            F_PROP_DIRS.setAccessible(true);
            M_ENQUEUE_DEC = le.getDeclaredMethod("enqueueDecrease", Long.TYPE, Long.TYPE);
            M_ENQUEUE_DEC.setAccessible(true);
            M_ENQUEUE_INC = le.getDeclaredMethod("enqueueIncrease", Long.TYPE, Long.TYPE);
            M_ENQUEUE_INC.setAccessible(true);
            M_GET_STATE = le.getDeclaredMethod("getState", BlockPos.class);
            M_GET_STATE.setAccessible(true);
            M_GET_OPACITY = le.getDeclaredMethod("getOpacity", BlockState.class, BlockPos.class);
            M_GET_OPACITY.setAccessible(true);
            M_SHAPE_OCCLUDES = le.getDeclaredMethod("shapeOccludes", Long.TYPE, BlockState.class, Long.TYPE,
                    BlockState.class, Direction.class);
            M_SHAPE_OCCLUDES.setAccessible(true);
            M_IS_EMPTY_SHAPE = le.getDeclaredMethod("isEmptyShape", BlockState.class);
            M_IS_EMPTY_SHAPE.setAccessible(true);
            M_IS_SOURCE_LEVEL = SkyLightEngine.class.getDeclaredMethod("isSourceLevel", Integer.TYPE);
            M_IS_SOURCE_LEVEL.setAccessible(true);
            M_SET_LIGHT_ENABLED = LayerLightSectionStorage.class.getDeclaredMethod("setLightEnabled", Long.TYPE,
                    Boolean.TYPE);
            M_SET_LIGHT_ENABLED.setAccessible(true);
            M_GET_TOP_SECTION_Y = SkyLightSectionStorage.class.getDeclaredMethod("getTopSectionY", Long.TYPE);
            M_GET_TOP_SECTION_Y.setAccessible(true);
            M_GET_BOTTOM_SECTION_Y = SkyLightSectionStorage.class.getDeclaredMethod("getBottomSectionY");
            M_GET_BOTTOM_SECTION_Y.setAccessible(true);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    private SkyLightSectionStorage refStorage() {
        try {
            return (SkyLightSectionStorage) F_STORAGE.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Direction[] propDirs() {
        try {
            return (Direction[]) F_PROP_DIRS.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean storageLightOnInSection(long sec) {
        try {
            return (boolean) STORAGE_LIGHT_ON_IN_SECTION.invoke(refStorage(), sec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean storageStoringLightForSection(long sec) {
        try {
            return (boolean) STORAGE_STORING_LIGHT_FOR_SECTION.invoke(refStorage(), sec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean storageHasLightDataAtOrBelow(int y) {
        try {
            return (boolean) STORAGE_HAS_LIGHT_DATA_AT_OR_BELOW.invoke(refStorage(), y);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean storageIsAboveData(long sec) {
        try {
            return (boolean) STORAGE_IS_ABOVE_DATA.invoke(refStorage(), sec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int storageGetStoredLevel(long pos) {
        try {
            return (int) STORAGE_GET_STORED_LEVEL.invoke(refStorage(), pos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int storageGetTopSectionY(long s) {
        try {
            return (int) M_GET_TOP_SECTION_Y.invoke(refStorage(), s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int storageGetBottomSectionY() {
        try {
            return (int) M_GET_BOTTOM_SECTION_Y.invoke(refStorage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long pullLightEntry() {
        try {
            return F_PULL_LIGHT.getLong(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void storageSetStoredLevel(long pos, int level) {
        try {
            STORAGE_SET_STORED_LEVEL.invoke(refStorage(), pos, level);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void storageSetLightEnabled(long s, boolean b) {
        try {
            M_SET_LIGHT_ENABLED.invoke(refStorage(), s, b);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void callEnqueueDec(long p, long q) {
        try {
            M_ENQUEUE_DEC.invoke(this, p, q);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void callEnqueueInc(long p, long q) {
        try {
            M_ENQUEUE_INC.invoke(this, p, q);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private BlockState callGetState(BlockPos p) {
        try {
            return (BlockState) M_GET_STATE.invoke(this, p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int callGetOpacity(BlockState s, BlockPos p) {
        try {
            return (int) M_GET_OPACITY.invoke(this, s, p);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean callShapeOccludes(long a, BlockState sa, long b, BlockState sb, Direction d) {
        try {
            return (boolean) M_SHAPE_OCCLUDES.invoke(this, a, sa, b, sb, d);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean callIsEmptyShape(BlockState s) {
        try {
            return (boolean) M_IS_EMPTY_SHAPE.invoke(null, s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean callIsSourceLevel(int l) {
        try {
            return (boolean) M_IS_SOURCE_LEVEL.invoke(null, l);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private LightChunkGetter getChunkSource() {
        try {
            Field field = LightEngine.class.getDeclaredField("chunkSource");
            field.setAccessible(true);
            return (LightChunkGetter) field.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private DataLayer callGetDLToWrite(long sec) {
        try {
            java.lang.reflect.Method m = net.minecraft.world.level.lighting.LayerLightSectionStorage.class
                    .getDeclaredMethod("getDataLayerToWrite", Long.TYPE);
            m.setAccessible(true);
            return (DataLayer) m.invoke(refStorage(), sec);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ----------------------------------------------------
    @Shadow
    protected abstract int getLowestSourceY(int x, int z, int defaultReturnValue);

    @Shadow
    private void updateSourcesInColumn(int x, int z, int lowestY) {
    }

    @Shadow
    private static long REMOVE_SKY_SOURCE_ENTRY;

    @Shadow
    private static long ADD_SKY_SOURCE_ENTRY;

    private void checkNodeWithPos(int x, int y, int z, long levelPos) {
        long secPos = SectionPos.blockToSection(levelPos);

        int lowY = storageLightOnInSection(secPos) ? this.getLowestSourceY(x, z, Integer.MAX_VALUE) : Integer.MAX_VALUE;

        if (lowY != Integer.MAX_VALUE) {
            this.updateSourcesInColumn(x, z, lowY);
        }

        if (storageStoringLightForSection(secPos)) {
            if (y >= lowY) {
                callEnqueueDec(levelPos, REMOVE_SKY_SOURCE_ENTRY);
                callEnqueueInc(levelPos, ADD_SKY_SOURCE_ENTRY);
            } else {
                int level = storageGetStoredLevel(levelPos);
                if (level > 0) {
                    storageSetStoredLevel(levelPos, 0);
                    callEnqueueDec(levelPos, LightEngine.QueueEntry.decreaseAllDirections(level));
                } else {
                    callEnqueueDec(levelPos, pullLightEntry());
                }
            }
        }
    }

    @Overwrite
    protected void checkNode(long levelPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(levelPos);
        checkNodeWithPos(bp.x, bp.y, bp.z, levelPos);
    }

    // ----------------------------------------------------

    @Overwrite
    private int countEmptySectionsBelowIfAtBorder(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        return countEmptySectionsBelowIfAtBorder(bp.x, bp.y, bp.z);
    }

    private boolean hasLowerLightData(long x, long y, long z) {
        return !storageStoringLightForSection(
                HashUtil.hashSection(x, y, z)) && storageHasLightDataAtOrBelow((int) y);
    }

    private int countEmptySectionsBelowIfAtBorder(int x, int y, int z) {
        int secY = SectionPos.sectionRelative(y);
        if (secY != 0) {
            return 0;
        }
        int secX = SectionPos.sectionRelative(x);
        int secZ = SectionPos.sectionRelative(z);

        if (secX != 0 && secX != 15 && secZ != 0 && secZ != 15) {
            return 0;
        }

        int coordX = SectionPos.blockToSectionCoord(x);
        int coordY = SectionPos.blockToSectionCoord(y);
        int coordZ = SectionPos.blockToSectionCoord(z);
        int value = 0;
        while (hasLowerLightData(coordX, coordY - value - 1, coordZ)) {
            value++;
        }
        return value;
    }

    // ----------------------------------------------------
    @Shadow
    private static boolean crossedSectionEdge(Direction direction, int x, int z) {
        return false;
    }

    @Overwrite
    private void propagateFromEmptySections(
            long packedPos,
            Direction direction,
            int level,
            boolean shouldIncrease,
            int emptySections) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        propagateFromEmptySections(bp.x, bp.y, bp.z, direction, level, shouldIncrease, emptySections);
    }

    @SuppressWarnings("null")
    private void propagateFromEmptySections(
            int x,
            int y,
            int z,
            Direction direction,
            int level,
            boolean shouldIncrease,
            int emptySections) {
        if (emptySections == 0) {
            return;
        }
        if (!crossedSectionEdge(
                direction,
                SectionPos.sectionRelative(x),
                SectionPos.sectionRelative(z))) {
            return;
        }
        int coordX = SectionPos.blockToSectionCoord(x);
        int coordY = SectionPos.blockToSectionCoord(y) - 1;
        int coordZ = SectionPos.blockToSectionCoord(z);
        int k = coordY - emptySections + 1;

        while (coordY >= k) {
            if (!storageStoringLightForSection(
                    HashUtil.hashSection(
                            coordX,
                            coordY,
                            coordZ))) {
                coordY--;
                continue;
            }
            int coordBlockZ = SectionPos.sectionToBlockCoord(coordY);
            for (int index = 15; index >= 0; index--) {
                long pos = BlockPos.asLong(x, coordBlockZ + index, z);
                if (shouldIncrease) {
                    storageSetStoredLevel(pos, level);
                    if (level > 1) {
                        callEnqueueInc(
                                pos,
                                LightEngine.QueueEntry.increaseSkipOneDirection(
                                        level,
                                        true,
                                        direction.getOpposite()));
                    }
                } else {
                    storageSetStoredLevel(pos, 0);
                    callEnqueueDec(
                            pos,
                            LightEngine.QueueEntry.decreaseSkipOneDirection(
                                    level,
                                    direction.getOpposite()));
                }
            }
            coordY--;
        }
    }

    // ----------------------------------------------------

    @Shadow
    private BlockPos.MutableBlockPos mutablePos;

    @SuppressWarnings("null")
    @Overwrite
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        IntBlockPos src = IntBlockPos.getBlockPos(packedPos); // CHM read for source (from queue)
        int sx = src.x;
        int sy = src.y;
        int sz = src.z;

        BlockState lazyBlockState = null;
        int countEmptySections = countEmptySectionsBelowIfAtBorder(sx, sy, sz);
        for (Direction direction : propDirs()) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, direction)) {
                int nx = sx + direction.getStepX();
                int ny = sy + direction.getStepY();
                int nz = sz + direction.getStepZ();

                long nSec = HashUtil.hashSection(nx >> 4, ny >> 4, nz >> 4);
                if (!storageStoringLightForSection(nSec)) {
                    continue;
                }
                int rx = SectionPos.sectionRelative(nx);
                int ry = SectionPos.sectionRelative(ny);
                int rz = SectionPos.sectionRelative(nz);

                DataLayer dataLayer = HashUtil.callGetDataLayer(refStorage(), nSec, true);
                if (dataLayer == null) {
                    continue;
                }

                int data = dataLayer.get(rx, ry, rz);
                if (lightLevel <= data + 1) {
                    continue;
                }

                this.mutablePos.set(nx, ny, nz);
                BlockState blockState = callGetState(this.mutablePos);
                int lightLoss = lightLevel - callGetOpacity(blockState, this.mutablePos);
                if (lightLoss <= data) {
                    continue;
                }

                long nKey = HashUtil.hashPos((long) nx, (long) ny, (long) nz);
                if (lazyBlockState == null) {
                    lazyBlockState = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                            ? Blocks.AIR.defaultBlockState()
                            : callGetState(this.mutablePos.set(sx, sy, sz));
                }

                if (!callShapeOccludes(
                        packedPos,
                        lazyBlockState,
                        nKey,
                        blockState,
                        direction)) {
                    dataLayer.set(rx, ry, rz, lightLoss);
                    if (lightLoss > 1) {
                        HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz));
                        callEnqueueInc(
                                nKey,
                                LightEngine.QueueEntry.increaseSkipOneDirection(lightLoss,
                                        callIsEmptyShape(blockState),
                                        direction.getOpposite()));
                    }
                    propagateFromEmptySections(
                            nx,
                            ny,
                            nz,
                            direction,
                            lightLoss,
                            true,
                            countEmptySections);
                }
            }
        }
    }

    // ----------------------------------------------------
    @SuppressWarnings("null")
    @Overwrite
    protected void propagateDecrease(long packedPos, long lightLevel) {
        IntBlockPos src = IntBlockPos.getBlockPos(packedPos); // CHM read for source (from queue)
        int sx = src.x;
        int sy = src.y;
        int sz = src.z;

        int countEmptySections = countEmptySectionsBelowIfAtBorder(sx, sy, sz);
        int entry = LightEngine.QueueEntry.getFromLevel(lightLevel);

        for (Direction direction : propDirs()) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, direction)) {
                int nx = sx + direction.getStepX();
                int ny = sy + direction.getStepY();
                int nz = sz + direction.getStepZ();

                long nSec = HashUtil.hashSection(nx >> 4, ny >> 4, nz >> 4);
                if (!storageStoringLightForSection(nSec)) {
                    continue;
                }
                int rx = SectionPos.sectionRelative(nx);
                int ry = SectionPos.sectionRelative(ny);
                int rz = SectionPos.sectionRelative(nz);

                DataLayer dataLayer = HashUtil.callGetDataLayer(refStorage(), nSec, true);
                if (dataLayer == null) {
                    continue;
                }

                int data = dataLayer.get(rx, ry, rz);
                if (data == 0) {
                    continue;
                }

                long nKey = HashUtil.hashPos((long) nx, (long) ny, (long) nz);
                HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz));
                if (data < entry) {
                    dataLayer.set(rx, ry, rz, 0);
                    callEnqueueDec(
                            nKey,
                            LightEngine.QueueEntry.decreaseSkipOneDirection(data, direction.getOpposite()));
                    propagateFromEmptySections(nx,
                            ny,
                            nz,
                            direction,
                            data,
                            false,
                            countEmptySections);
                } else {
                    callEnqueueInc(nKey,
                            LightEngine.QueueEntry.increaseOnlyOneDirection(data, false, direction.getOpposite()));
                }

            }
        }
    }

    // ----------------------------------------------------

    @Shadow
    private static long REMOVE_TOP_SKY_SOURCE_ENTRY;

    @Overwrite
    private void removeSourcesBelow(int x, int z, int minY, int bottomSectionY) {
        if (minY <= bottomSectionY) {
            return;
        }
        int coordX = SectionPos.blockToSectionCoord(x);
        int coordZ = SectionPos.blockToSectionCoord(z);
        int rx = SectionPos.sectionRelative(x);
        int rz = SectionPos.sectionRelative(z);
        int keyY = minY - 1;

        for (int index = SectionPos.blockToSectionCoord(keyY); storageHasLightDataAtOrBelow(index); index--) {
            long nSec = HashUtil.hashSection(coordX, index, coordZ);
            if (!storageStoringLightForSection(nSec)) {
                continue;
            }

            DataLayer dataLayer = HashUtil.callGetDataLayer(refStorage(), nSec, true);
            if (dataLayer == null) {
                continue;
            }

            int coordIndex = SectionPos.sectionToBlockCoord(index);
            int coordIndexLarger = coordIndex + 15;

            for (int y = Math.min(coordIndexLarger, keyY); y >= coordIndex; y--) {
                int ry = SectionPos.sectionRelative(y);
                int level = dataLayer.get(rx, ry, rz);
                if (callIsSourceLevel(level)) {
                    return;
                }
                dataLayer.set(rx, ry, rz, 0);
                long pos = BlockPos.asLong(x, y, z);
                callEnqueueDec(pos, y == minY - 1 ? REMOVE_TOP_SKY_SOURCE_ENTRY : REMOVE_SKY_SOURCE_ENTRY);
            }
        }
    }

    @Overwrite
    private void addSourcesAbove(int x, int z, int maxY, int bottomSectionY) {
        int coordX = SectionPos.blockToSectionCoord(x);
        int coordZ = SectionPos.blockToSectionCoord(z);
        int keyY = Math.max(
                Math.max(this.getLowestSourceY(x - 1, z, Integer.MIN_VALUE),
                        this.getLowestSourceY(x + 1, z, Integer.MIN_VALUE)),
                Math.max(this.getLowestSourceY(x, z - 1, Integer.MIN_VALUE),
                        this.getLowestSourceY(x, z + 1, Integer.MIN_VALUE)));
        int varY = Math.max(maxY, bottomSectionY);
        int coordY = SectionPos.blockToSectionCoord(varY);

        for (long i = HashUtil.hashSection(coordX, coordY, coordZ); !storageIsAboveData(i); i = SectionPos.offset(i,
                Direction.UP), coordY++) {
            if (!storageStoringLightForSection(i)) {
                continue;
            }
            int blockCoordY = SectionPos.sectionToBlockCoord(coordY);
            int blockCoordYLarger = blockCoordY + 15;
            for (int j = Math.max(blockCoordY, varY); j <= blockCoordYLarger; j++) {
                long pos = BlockPos.asLong(x, j, z);
                if (callIsSourceLevel(storageGetStoredLevel(pos)))
                    return;

                storageSetStoredLevel(pos, 15);
                if (j < keyY || j == maxY) {
                    callEnqueueInc(pos, ADD_SKY_SOURCE_ENTRY);
                }
            }
        }
    }

    // ----------------------------------------------------
    @Shadow
    private ChunkSkyLightSources emptyChunkSources;

    private ChunkSkyLightSources getChunkSourcesOrEmpty(int cx, int cz) {
        LightChunk chunk = getChunkSource().getChunkForLighting(cx, cz);
        return chunk != null ? chunk.getSkyLightSources() : emptyChunkSources;
    }

    @Overwrite
    public void propagateLightSources(net.minecraft.world.level.ChunkPos chunkPos) {
        long zeroNode = SectionPos.getZeroNode(chunkPos.x, chunkPos.z);
        storageSetLightEnabled(zeroNode, true);

        ChunkSkyLightSources s0 = getChunkSourcesOrEmpty(chunkPos.x, chunkPos.z);
        ChunkSkyLightSources s1 = getChunkSourcesOrEmpty(chunkPos.x, chunkPos.z - 1);
        ChunkSkyLightSources s2 = getChunkSourcesOrEmpty(chunkPos.x, chunkPos.z + 1);
        ChunkSkyLightSources s3 = getChunkSourcesOrEmpty(chunkPos.x - 1, chunkPos.z);
        ChunkSkyLightSources s4 = getChunkSourcesOrEmpty(chunkPos.x + 1, chunkPos.z);

        int topSecY = storageGetTopSectionY(zeroNode);
        int bottomSecY = storageGetBottomSectionY();
        int coordX = SectionPos.sectionToBlockCoord(chunkPos.x);
        int coordZ = SectionPos.sectionToBlockCoord(chunkPos.z);

        for (int i = topSecY - 1; i >= bottomSecY; i--) {
            long sec = HashUtil.hashSection(chunkPos.x, i, chunkPos.z);
            DataLayer dataLayer = callGetDLToWrite(sec);
            if (dataLayer == null) {
                continue;
            }

            int blockCoordY = SectionPos.sectionToBlockCoord(i);
            int blockCoordYLarger = blockCoordY + 15;
            boolean flag = false;

            for (int j = 0; j < 16; j++) {
                for (int k = 0; k < 16; k++) {
                    int sourceY = s0.getLowestSourceY(k, j);
                    if (sourceY > blockCoordYLarger) {
                        continue;
                    }

                    int low0 = j == 0 ? s1.getLowestSourceY(k, 15) : s0.getLowestSourceY(k, j - 1);
                    int low1 = j == 15 ? s2.getLowestSourceY(k, 0) : s0.getLowestSourceY(k, j + 1);
                    int low2 = k == 0 ? s3.getLowestSourceY(15, j) : s0.getLowestSourceY(k - 1, j);
                    int low3 = k == 15 ? s4.getLowestSourceY(0, j) : s0.getLowestSourceY(k + 1, j);
                    int maxLow = Math.max(Math.max(low0, low1), Math.max(low2, low3));

                    for (int l = blockCoordYLarger; l >= Math.max(blockCoordY, sourceY); l--) {
                        dataLayer.set(k, SectionPos.sectionRelative(l), j, 15);
                        if (l != sourceY && l >= maxLow) {
                            continue;
                        }
                        int bx = coordX + k;
                        int by = l;
                        int bz = coordZ + j;
                        long pos = BlockPos.asLong(bx, by, bz);
                        callEnqueueInc(pos, LightEngine.QueueEntry.increaseSkySourceInDirections(l == sourceY,
                                l < low0, l < low1, l < low2, l < low3));
                    }
                    if (sourceY < blockCoordY) {
                        flag = true;
                    }
                }
            }
            if (!flag) {
                break;
            }
        }
    }
}
