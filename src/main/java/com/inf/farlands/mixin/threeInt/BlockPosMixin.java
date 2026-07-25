package com.inf.farlands.mixin.threeInt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;

import net.minecraft.core.BlockPos;

@Mixin(BlockPos.class)
public abstract class BlockPosMixin {
    @Shadow
    private static int PACKED_X_LENGTH;
    @Shadow
    private static int PACKED_Y_LENGTH;
    @Shadow
    private static int PACKED_Z_LENGTH;
    @Shadow
    private static int Y_OFFSET;
    @Shadow
    private static int Z_OFFSET;
    @Shadow
    private static int X_OFFSET;

    @Overwrite
    public static int getX(long packedPos) {
        IntBlockPos pos = HashUtil.getBlock(packedPos);
        if (pos != null) {
            return pos.x;
        }
        return (int) (packedPos << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH);
    }

    @Overwrite
    public static int getY(long packedPos) {
        IntBlockPos pos = HashUtil.getBlock(packedPos);
        if (pos != null) {
            return pos.y;
        }
        return (int) (packedPos << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
    }

    @Overwrite
    public static int getZ(long packedPos) {
        IntBlockPos pos = HashUtil.getBlock(packedPos);
        if (pos != null) {
            return pos.z;
        }
        return (int) (packedPos << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH);
    }

    // --------------------------------

    // 方法：
    // public static long offset(long pos, int dx, int dy, int dz) {
    // return asLong(getX(pos) + dx, getY(pos) + dy, getZ(pos) + dz);
    // }

    @Overwrite
    public static long offset(long packedPos, int dx, int dy, int dz) {
        IntBlockPos pos = IntBlockPos.getBlockPos(packedPos);
        int nx = pos.x + dx;
        int ny = pos.y + dy;
        int nz = pos.z + dz;
        // 存 CHM
        long key = HashUtil.hashPos((long) nx, (long) ny, (long) nz);
        HashUtil.putBlock(key, new IntBlockPos(nx, ny, nz));
        return key;
    }

    // 方法：
    // public static BlockPos of(long packedPos) {
    // return new BlockPos(getX(packedPos), getY(packedPos), getZ(packedPos));
    // }

    @Overwrite
    public static BlockPos of(long packedPos) {
        IntBlockPos pos = IntBlockPos.getBlockPos(packedPos);
        return new BlockPos(pos.x, pos.y, pos.z);
    }

    // 方法：
    // public static long getFlatIndex(long packedPos) {
    // return packedPos & -16L;
    // }

    @Overwrite
    public static long getFlatIndex(long packedPos) {
        IntBlockPos pos = IntBlockPos.getBlockPos(packedPos);
        return HashUtil.hashPos((long) pos.x, 0, (long) pos.z);
    }

    // --------------------------------

    // 方法：
    // public long asLong() {
    // return asLong(this.getX(), this.getY(), this.getZ());
    // }

    @Overwrite
    public long asLong() {
        BlockPos self = (BlockPos) (Object) this;
        return HashUtil.hashPos((long) self.getX(), (long) self.getY(), (long) self.getZ());
    }

    // 方法：
    // public static long asLong(int x, int y, int z) {
    // long i = 0L;
    // i |= ((long) x & PACKED_X_MASK) << X_OFFSET;
    // i |= ((long) y & PACKED_Y_MASK) << 0;
    // return i | ((long) z & PACKED_Z_MASK) << Z_OFFSET;
    // }
    @Overwrite
    public static long asLong(int x, int y, int z) {
        return HashUtil.hashPos((long) x, (long) y, (long) z);
    }
}
