package com.inf.farlands.mixin.threeInt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;

import net.minecraft.core.SectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LightEngine;

import net.minecraft.world.level.lighting.LayerLightSectionStorage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Mixin(BlockLightEngine.class)
public abstract class BlockLightEngineMixin {
    private static final Field F_PROP_DIRS;
    private static final Field F_STORAGE;
    private static final Method M_STORING;
    private static final Method M_GET_STATE;
    private static final Method M_GET_OPACITY;
    private static final Method M_SHAPE_OCCLUDES;
    private static final Method M_IS_EMPTY_SHAPE;
    private static final Method M_ENQUEUE_INC;
    private static final Method M_GET_EMISSION;
    private static final Method M_ENQUEUE_DEC;

    // -----------------------------------------

    static {
        try {
            Class<?> le = LightEngine.class;
            F_PROP_DIRS = le.getDeclaredField("PROPAGATION_DIRECTIONS");
            F_PROP_DIRS.setAccessible(true);
            F_STORAGE = le.getDeclaredField("storage");
            F_STORAGE.setAccessible(true);
            M_GET_STATE = le.getDeclaredMethod("getState", BlockPos.class);
            M_GET_STATE.setAccessible(true);
            M_GET_OPACITY = le.getDeclaredMethod("getOpacity", BlockState.class, BlockPos.class);
            M_GET_OPACITY.setAccessible(true);
            M_SHAPE_OCCLUDES = le.getDeclaredMethod("shapeOccludes", Long.TYPE, BlockState.class, Long.TYPE,
                    BlockState.class, Direction.class);
            M_SHAPE_OCCLUDES.setAccessible(true);
            M_IS_EMPTY_SHAPE = le.getDeclaredMethod("isEmptyShape", BlockState.class);
            M_IS_EMPTY_SHAPE.setAccessible(true);
            M_ENQUEUE_INC = le.getDeclaredMethod("enqueueIncrease", Long.TYPE, Long.TYPE);
            M_ENQUEUE_INC.setAccessible(true);
            M_ENQUEUE_DEC = le.getDeclaredMethod("enqueueDecrease", Long.TYPE, Long.TYPE);
            M_ENQUEUE_DEC.setAccessible(true);
            // -----------------------------------------

            Class<?> ble = BlockLightEngine.class;
            M_GET_EMISSION = ble.getDeclaredMethod("getEmission", Long.TYPE, BlockState.class);
            M_GET_EMISSION.setAccessible(true);
            // -----------------------------------------

            Class<?> lls = LayerLightSectionStorage.class;
            M_STORING = lls.getDeclaredMethod("storingLightForSection", Long.TYPE);
            M_STORING.setAccessible(true);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    // -----------------------------------------
    private Object refStorage() {
        try {
            return F_STORAGE.get(this);
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

    private boolean storageStoring(long s) {
        try {
            return (boolean) M_STORING.invoke(refStorage(), s);
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

    private void callEnqueueInc(long p, long q) {
        try {
            M_ENQUEUE_INC.invoke(this, p, q);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private int callGetEmission(long p, BlockState s) {
        try {
            return (int) M_GET_EMISSION.invoke(this, p, s);
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
    // -----------------------------------------

    @Shadow
    private BlockPos.MutableBlockPos mutablePos;

    @SuppressWarnings("null")
    @Overwrite
    protected void propagateIncrease(long packedPos, long queueEntry, int lightLevel) {
        IntBlockPos src = IntBlockPos.getBlockPos(packedPos);
        int sx = src.x;
        int sy = src.y;
        int sz = src.z;
        BlockState blockstate = null;
        Direction[] dirs = propDirs();

        for (Direction dir : dirs) {
            if (!LightEngine.QueueEntry.shouldPropagateInDirection(queueEntry, dir)) {
                continue;
            }
            int nx = sx + dir.getStepX();
            int ny = sy + dir.getStepY();
            int nz = sz + dir.getStepZ();
            long nSec = HashUtil.hashSection(nx >> 4, ny >> 4, nz >> 4);
            if (!storageStoring(nSec)) {
                continue;
            }

            int rx = SectionPos.sectionRelative(nx);
            int ry = SectionPos.sectionRelative(ny);
            int rz = SectionPos.sectionRelative(nz);

            DataLayer dataLayer = HashUtil.callGetDataLayer(refStorage(), nSec, true);
            if (dataLayer == null)
                continue;

            int data = dataLayer.get(rx, ry, rz);
            int lightLess = lightLevel - 1;

            if (lightLess <= data) {
                continue;
            }

            mutablePos.set(nx, ny, nz);
            BlockState bs1 = callGetState(mutablePos);
            int LightLoss = lightLevel - callGetOpacity(bs1, mutablePos);
            if (LightLoss <= data) {
                continue;
            }

            if (blockstate == null) {
                blockstate = LightEngine.QueueEntry.isFromEmptyShape(queueEntry)
                        ? Blocks.AIR.defaultBlockState()
                        : callGetState(mutablePos.set(sx, sy, sz));
            }

            long key = HashUtil.hashPos((long) nx, (long) ny, (long) nz);
            if (callShapeOccludes(packedPos, blockstate, key, bs1, dir)) {
                continue;
            }

            dataLayer.set(rx, ry, rz, LightLoss);
            if (LightLoss > 1) {
                HashUtil.putBlock(key, new IntBlockPos(nx, ny, nz));
                callEnqueueInc(key, LightEngine.QueueEntry.increaseSkipOneDirection(LightLoss,
                        callIsEmptyShape(bs1), dir.getOpposite()));
            }
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    protected void propagateDecrease(long packedPos, long lightLevel) {
        IntBlockPos src = IntBlockPos.getBlockPos(packedPos);
        int sx = src.x;
        int sy = src.y;
        int sz = src.z;
        int entry = LightEngine.QueueEntry.getFromLevel(lightLevel);
        Direction[] dirs = propDirs();

        for (Direction dir : dirs) {
            if (!LightEngine.QueueEntry.shouldPropagateInDirection(lightLevel, dir)) {
                continue;
            }

            int nx = sx + dir.getStepX();
            int ny = sy + dir.getStepY();
            int nz = sz + dir.getStepZ();
            long nSec = HashUtil.hashSection(nx >> 4, ny >> 4, nz >> 4);
            if (!storageStoring(nSec)) {
                continue;
            }

            int rx = SectionPos.sectionRelative(nx);
            int ry = SectionPos.sectionRelative(ny);
            int rz = SectionPos.sectionRelative(nz);

            DataLayer dataLayer = HashUtil.callGetDataLayer(refStorage(), nSec, true);
            if (dataLayer == null)
                continue;

            int data = dataLayer.get(rx, ry, rz);
            if (data == 0) {
                continue;
            }

            long key = HashUtil.hashPos((long) nx, (long) ny, (long) nz);
            HashUtil.putBlock(key, new IntBlockPos(nx, ny, nz));
            if (data <= entry - 1) {
                BlockState state = callGetState(mutablePos.set(nx, ny, nz));
                int emis = callGetEmission(HashUtil.hashPos((long) nx, (long) ny, (long) nz), state);
                dataLayer.set(rx, ry, rz, 0);

                if (emis < data) {
                    callEnqueueDec(key,
                            LightEngine.QueueEntry.decreaseSkipOneDirection(data, dir.getOpposite()));
                }

                if (emis > 0) {
                    callEnqueueInc(key,
                            LightEngine.QueueEntry.increaseLightFromEmission(emis, callIsEmptyShape(state)));
                }
            } else {
                callEnqueueInc(
                        key,
                        LightEngine.QueueEntry.increaseOnlyOneDirection(
                                data,
                                false,
                                dir.getOpposite()));
            }
        }
    }
}
