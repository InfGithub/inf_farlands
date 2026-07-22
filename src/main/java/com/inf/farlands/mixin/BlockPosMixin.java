package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

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
    public long asLong() {
        BlockPos self = (BlockPos)(Object) this;
        return HashUtil.hashPos((long)self.getX(), (long)self.getY(), (long)self.getZ());
    }

    // === method3: block-level key → IntBlockPos ===
    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp : new IntBlockPos(getX(key), getY(key), getZ(key));
    }

    @Overwrite
    public static long asLong(int x, int y, int z) {
        return HashUtil.hashPos((long)x, (long)y, (long)z);
    }

    @Overwrite
    public static int getX(long packedPos) {
        IntBlockPos pos = HashUtil.getBlock(packedPos);
        if (pos != null) return pos.x;
        return (int)(packedPos << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH);
    }

    @Overwrite
    public static int getY(long packedPos) {
        IntBlockPos pos = HashUtil.getBlock(packedPos);
        if (pos != null) return pos.y;
        return (int)(packedPos << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
    }

    @Overwrite
    public static int getZ(long packedPos) {
        IntBlockPos pos = HashUtil.getBlock(packedPos);
        if (pos != null) return pos.z;
        return (int)(packedPos << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH);
    }

    @Overwrite
    public static long offset(long packedPos, Direction direction) {
        return offset(packedPos, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    @Overwrite
    public static long offset(long packedPos, int dx, int dy, int dz) {
        IntBlockPos pos = getBlockPos(packedPos);
        int nx = pos.x + dx, ny = pos.y + dy, nz = pos.z + dz;
        long key = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
        HashUtil.putBlock(key, new IntBlockPos(nx, ny, nz));
        return key;
    }

    @Overwrite
    public static BlockPos of(long packedPos) {
        IntBlockPos pos = getBlockPos(packedPos);
        return new BlockPos(pos.x, pos.y, pos.z);
    }

    @Overwrite
    public static long getFlatIndex(long packedPos) {
        IntBlockPos pos = getBlockPos(packedPos);
        return HashUtil.hashPos((long)pos.x, 0, (long)pos.z);
    }
}
