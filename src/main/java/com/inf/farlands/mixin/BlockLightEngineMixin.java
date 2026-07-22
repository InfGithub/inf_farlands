package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {

    @Shadow private BlockPos.MutableBlockPos mutablePos;

    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp : new IntBlockPos(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
    }

    private static final java.lang.reflect.Method M_GET_EMISSION, M_GET_STATE, M_GET_OPACITY, M_SHAPE_OCCLUDES, M_IS_EMPTY_SHAPE;
    private static final java.lang.reflect.Method M_ENQUEUE_DEC, M_ENQUEUE_INC, M_STORING;
    private static final java.lang.reflect.Field F_STORAGE, F_PROP_DIRS;
    static {
        try {
            Class<?> le = LightEngine.class, ble = BlockLightEngine.class, lls = net.minecraft.world.level.lighting.LayerLightSectionStorage.class;
            M_GET_EMISSION = ble.getDeclaredMethod("getEmission", Long.TYPE, BlockState.class); M_GET_EMISSION.setAccessible(true);
            M_GET_STATE = le.getDeclaredMethod("getState", BlockPos.class); M_GET_STATE.setAccessible(true);
            M_GET_OPACITY = le.getDeclaredMethod("getOpacity", BlockState.class, BlockPos.class); M_GET_OPACITY.setAccessible(true);
            M_SHAPE_OCCLUDES = le.getDeclaredMethod("shapeOccludes", Long.TYPE, BlockState.class, Long.TYPE, BlockState.class, Direction.class); M_SHAPE_OCCLUDES.setAccessible(true);
            M_IS_EMPTY_SHAPE = le.getDeclaredMethod("isEmptyShape", BlockState.class); M_IS_EMPTY_SHAPE.setAccessible(true);
            M_ENQUEUE_DEC = le.getDeclaredMethod("enqueueDecrease", Long.TYPE, Long.TYPE); M_ENQUEUE_DEC.setAccessible(true);
            M_ENQUEUE_INC = le.getDeclaredMethod("enqueueIncrease", Long.TYPE, Long.TYPE); M_ENQUEUE_INC.setAccessible(true);
            M_STORING = lls.getDeclaredMethod("storingLightForSection", Long.TYPE); M_STORING.setAccessible(true);
            F_STORAGE = le.getDeclaredField("storage"); F_STORAGE.setAccessible(true);
            F_PROP_DIRS = le.getDeclaredField("PROPAGATION_DIRECTIONS"); F_PROP_DIRS.setAccessible(true);
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private Object refStorage() { try { return F_STORAGE.get(this); } catch (Exception e) { throw new RuntimeException(e); } }
    private Direction[] propDirs() { try { return (Direction[]) F_PROP_DIRS.get(null); } catch (Exception e) { throw new RuntimeException(e); } }
    private int callGetEmission(long p, BlockState s)    { try { return (int) M_GET_EMISSION.invoke(this, p, s); } catch (Exception e) { throw new RuntimeException(e); } }
    private BlockState callGetState(BlockPos p)           { try { return (BlockState) M_GET_STATE.invoke(this, p); } catch (Exception e) { throw new RuntimeException(e); } }
    private int callGetOpacity(BlockState s, BlockPos p) { try { return (int) M_GET_OPACITY.invoke(this, s, p); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callShapeOccludes(long a, BlockState sa, long b, BlockState sb, Direction d) { try { return (boolean) M_SHAPE_OCCLUDES.invoke(this, a, sa, b, sb, d); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean callIsEmptyShape(BlockState s)       { try { return (boolean) M_IS_EMPTY_SHAPE.invoke(null, s); } catch (Exception e) { throw new RuntimeException(e); } }
    private void callEnqueueDec(long p, long q)           { try { M_ENQUEUE_DEC.invoke(this, p, q); } catch (Exception e) { throw new RuntimeException(e); } }
    private void callEnqueueInc(long p, long q)           { try { M_ENQUEUE_INC.invoke(this, p, q); } catch (Exception e) { throw new RuntimeException(e); } }
    private boolean storageStoring(long s)               { try { return (boolean) M_STORING.invoke(refStorage(), s); } catch (Exception e) { throw new RuntimeException(e); } }

    @Overwrite
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        IntBlockPos src = getBlockPos(packedPos);
        int sx = src.x, sy = src.y, sz = src.z;
        BlockState blockstate = null;
        Direction[] dirs = propDirs();
        for (Direction dir : dirs) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, dir)) {
                int nx = sx + dir.getStepX(), ny = sy + dir.getStepY(), nz = sz + dir.getStepZ();
                long nSec = HashUtil.hashSection(nx>>4, ny>>4, nz>>4);
                int rx = SectionPos.sectionRelative(nx), ry = SectionPos.sectionRelative(ny), rz = SectionPos.sectionRelative(nz);
                if (storageStoring(nSec)) {
                    DataLayer dl = HashUtil.callGetDataLayer(refStorage(), nSec, true);
                    if (dl == null) continue;
                    int k = dl.get(rx, ry, rz), l = lightLevel - 1;
                    if (l > k) {
                        mutablePos.set(nx, ny, nz);
                        BlockState bs1 = callGetState(mutablePos);
                        int i1 = lightLevel - callGetOpacity(bs1, mutablePos);
                        if (i1 > k) {
                            if (blockstate == null)
                                blockstate = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                                    ? Blocks.AIR.defaultBlockState() : callGetState(mutablePos.set(sx, sy, sz));
                            long nKey = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
                            if (!callShapeOccludes(packedPos, blockstate, nKey, bs1, dir)) {
                                dl.set(rx, ry, rz, i1);
                                if (i1 > 1) {
                                    HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz));
                                    callEnqueueInc(nKey, LightEngine.QueueEntry.increaseSkipOneDirection(i1, callIsEmptyShape(bs1), dir.getOpposite()));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Overwrite
    protected void propagateDecrease(long packedPos, long lightLevel) {
        IntBlockPos src = getBlockPos(packedPos);
        int sx = src.x, sy = src.y, sz = src.z;
        int i = LightEngine.QueueEntry.getFromLevel(lightLevel);
        Direction[] dirs = propDirs();
        for (Direction dir : dirs) {
            if (LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, dir)) {
                int nx = sx + dir.getStepX(), ny = sy + dir.getStepY(), nz = sz + dir.getStepZ();
                long nSec = HashUtil.hashSection(nx>>4, ny>>4, nz>>4);
                int rx = SectionPos.sectionRelative(nx), ry = SectionPos.sectionRelative(ny), rz = SectionPos.sectionRelative(nz);
                if (storageStoring(nSec)) {
                    DataLayer dl = HashUtil.callGetDataLayer(refStorage(), nSec, true);
                    if (dl == null) continue;
                    int k = dl.get(rx, ry, rz);
                    if (k != 0) {
                        if (k <= i - 1) {
                            BlockState bs = callGetState(mutablePos.set(nx, ny, nz));
                            int l = callGetEmission(HashUtil.hashPos((long)nx, (long)ny, (long)nz), bs);
                            dl.set(rx, ry, rz, 0);
                            long nKey = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
                            HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz));
                            if (l < k) callEnqueueDec(nKey, LightEngine.QueueEntry.decreaseSkipOneDirection(k, dir.getOpposite()));
                            if (l > 0) callEnqueueInc(nKey, LightEngine.QueueEntry.increaseLightFromEmission(l, callIsEmptyShape(bs)));
                        } else {
                            long nKey = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
                            HashUtil.putBlock(nKey, new IntBlockPos(nx, ny, nz));
                            callEnqueueInc(nKey, LightEngine.QueueEntry.increaseOnlyOneDirection(k, false, dir.getOpposite()));
                        }
                    }
                }
            }
        }
    }
}
