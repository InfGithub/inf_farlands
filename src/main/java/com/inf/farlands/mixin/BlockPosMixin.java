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
        long key = HashUtil.hashPos((long)self.getX(), (long)self.getY(), (long)self.getZ());
        HashUtil.blockLookup.putIfAbsent(key, new IntBlockPos(self.getX(), self.getY(), self.getZ()));
        return key;
    }

    @Overwrite
    public static long asLong(int x, int y, int z) {
        long key = HashUtil.hashPos((long)x, (long)y, (long)z);
        // Leak is acceptable here — aquifer uses low volume; LightEngine
        // uses a different path (checkBlock / offset) for registration.
        HashUtil.blockLookup.put(key, new IntBlockPos(x, y, z));
        return key;
    }

    @Overwrite
    public static int getX(long packedPos) {
        IntBlockPos pos = HashUtil.blockLookup.get(packedPos);
        if (pos != null) return pos.x;
        return (int)(packedPos << 64 - X_OFFSET - PACKED_X_LENGTH >> 64 - PACKED_X_LENGTH);
    }

    @Overwrite
    public static int getY(long packedPos) {
        IntBlockPos pos = HashUtil.blockLookup.get(packedPos);
        if (pos != null) return pos.y;
        return (int)(packedPos << 64 - PACKED_Y_LENGTH >> 64 - PACKED_Y_LENGTH);
    }

    @Overwrite
    public static int getZ(long packedPos) {
        IntBlockPos pos = HashUtil.blockLookup.get(packedPos);
        if (pos != null) return pos.z;
        return (int)(packedPos << 64 - Z_OFFSET - PACKED_Z_LENGTH >> 64 - PACKED_Z_LENGTH);
    }

    @Overwrite
    public static long offset(long packedPos, Direction direction) {
        return offset(packedPos, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    @Overwrite
    public static long offset(long packedPos, int dx, int dy, int dz) {
        IntBlockPos pos = HashUtil.blockLookup.get(packedPos);
        int nx, ny, nz;
        if (pos != null) { nx = pos.x + dx; ny = pos.y + dy; nz = pos.z + dz; }
        else { nx = getX(packedPos) + dx; ny = getY(packedPos) + dy; nz = getZ(packedPos) + dz; }
        long key = HashUtil.hashPos((long)nx, (long)ny, (long)nz);
        HashUtil.blockLookup.put(key, new IntBlockPos(nx, ny, nz));
        return key;
    }

    @Overwrite
    public static BlockPos of(long packedPos) {
        IntBlockPos pos = HashUtil.blockLookup.get(packedPos);
        if (pos != null) return new BlockPos(pos.x, pos.y, pos.z);
        return new BlockPos(getX(packedPos), getY(packedPos), getZ(packedPos));
    }

    @Overwrite
    public static long getFlatIndex(long packedPos) {
        IntBlockPos pos = HashUtil.blockLookup.get(packedPos);
        if (pos != null) return HashUtil.hashPos((long)pos.x, 0, (long)pos.z);
        return packedPos & -16L;
    }
}
