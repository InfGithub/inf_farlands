package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.HashUtil;
import com.inf.farlands.InfFarlands;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.Objects;

@Mixin(SectionPos.class)
public abstract class SectionPosMixin {

    // 2个getPos方法

    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp
                : new IntBlockPos(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
    }

    private static IntSectionPos getSectionPos(long key) {
        IntSectionPos sp = HashUtil.getSection(key);
        return sp != null ? sp
                : new IntSectionPos(x(key), y(key), z(key));
    }

    // 哈希
    private static long hashSection(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= Long.rotateLeft(z * 0x9E3779B97F4A7C15L, 21);
        h ^= Long.rotateLeft(y * 0x9E3779B97F4A7C15L, 42);
        return h;
    }

    // 方法：
    // public long asLong() {
    // return asLong(this.x(), this.y(), this.z());
    // }

    @Overwrite
    public long asLong() {
        int x = ((SectionPos) (Object) this).x();
        int y = ((SectionPos) (Object) this).y();
        int z = ((SectionPos) (Object) this).z();
        long key = hashSection((long) x, (long) y, (long) z);
        HashUtil.putSection(key, new IntSectionPos(x, y, z));
        return key;
    }

    // 方法：
    // public static long asLong(int x, int y, int z) {
    // long i = 0L;
    // i |= ((long) x & 4194303L) << 42;
    // i |= ((long) y & 1048575L) << 0;
    // return i | ((long) z & 4194303L) << 20;
    // }

    @Overwrite
    public static long asLong(int x, int y, int z) {
        long key = hashSection((long) x, (long) y, (long) z);
        HashUtil.putSection(key, new IntSectionPos(x, y, z));
        return key;
    }

    // --------------------------------

    // public static int x(long packed) {
    // return (int) (packed << 0 >> 42);
    // }
    @Overwrite
    public static int x(long packed) {
        IntSectionPos p = HashUtil.getSection(packed);
        if (p != null) {
            p.lastAccess = InfFarlands.getServerTickCount();
            return p.x;
        }
        return (int) (packed >> 42);
    }

    // public static int y(long packed) {
    // return (int) (packed << 44 >> 44);
    // }
    @Overwrite
    public static int y(long packed) {
        IntSectionPos p = HashUtil.getSection(packed);
        if (p != null) {
            p.lastAccess = InfFarlands.getServerTickCount();
            return p.y;
        }
        return (int) (packed << 44 >> 44);
    }

    // public static int z(long packed) {
    // return (int) (packed << 22 >> 42);
    // }
    @Overwrite
    public static int z(long packed) {
        IntSectionPos p = HashUtil.getSection(packed);
        if (p != null) {
            p.lastAccess = InfFarlands.getServerTickCount();
            return p.z;
        }
        return (int) (packed << 22 >> 42);
    }

    // public static SectionPos of(long packed) {
    // return new SectionPos(x(packed), y(packed), z(packed));
    // }
    @Overwrite
    public static SectionPos of(long packed) {
        IntSectionPos p = getSectionPos(packed);
        return SectionPos.of(p.x, p.y, p.z);
    }

    // offset

    // public static long offset(long packed, Direction direction) {
    // return offset(packed, direction.getStepX(), direction.getStepY(),
    // direction.getStepZ());
    // }
    @Overwrite
    public static long offset(long packed, Direction direction) {
        return offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    // public static long offset(long packed, int dx, int dy, int dz) {
    // return asLong(x(packed) + dx, y(packed) + dy, z(packed) + dz);
    // }
    @Overwrite
    public static long offset(long packed, int dx, int dy, int dz) {
        IntSectionPos p = getSectionPos(packed);
        int nx = p.x + dx, ny = p.y + dy, nz = p.z + dz;
        long key = hashSection((long) nx, (long) ny, (long) nz);
        HashUtil.putSection(key, new IntSectionPos(nx, ny, nz));
        return key;
    }

    // block to section

    // public static long blockToSection(long levelPos) {
    // return asLong(
    // blockToSectionCoord(BlockPos.getX(levelPos)),
    // blockToSectionCoord(BlockPos.getY(levelPos)),
    // blockToSectionCoord(BlockPos.getZ(levelPos)));
    // }
    @Overwrite
    public static long blockToSection(long levelPos) {
        IntBlockPos bp = getBlockPos(levelPos);
        int sx = bp.x >> 4, sy = bp.y >> 4, sz = bp.z >> 4;
        long key = hashSection((long) sx, (long) sy, (long) sz);
        HashUtil.putSection(key, new IntSectionPos(sx, sy, sz));
        return key;
    }

    // public static long getZeroNode(long pos) {
    // return pos & -1048576L;
    // }
    @Overwrite
    public static long getZeroNode(long packed) {
        IntSectionPos p = getSectionPos(packed);
        long key = hashSection((long) p.x, 0, (long) p.z);
        HashUtil.putSection(key, new IntSectionPos(p.x, 0, p.z));
        return key;
    }

    // public static long getZeroNode(int x, int z) {
    // return getZeroNode(asLong(x, 0, z));
    // }

    @Overwrite
    public static long getZeroNode(int x, int z) {
        long key = hashSection((long) x, 0, (long) z);
        HashUtil.putSection(key, new IntSectionPos(x, 0, z));
        return key;
    }

    // relative
    @Shadow
    public abstract int x();

    @Shadow
    public abstract int y();

    @Shadow
    public abstract int z();

    @Shadow
    public static int sectionRelativeX(short packed) {
        return 0;
    }

    @Shadow
    public static int sectionRelativeY(short packed) {
        return 0;
    }

    @Shadow
    public static int sectionRelativeZ(short packed) {
        return 0;
    }

    // 为了防止溢出使用 long

    @Overwrite
    public int relativeToBlockX(short local) {
        return (int) (((long) x() << 4) + (long) sectionRelativeX(local));
    }

    @Overwrite
    public int relativeToBlockY(short local) {
        return (int) (((long) y() << 4) + (long) sectionRelativeY(local));
    }

    @Overwrite
    public int relativeToBlockZ(short local) {
        return (int) (((long) z() << 4) + (long) sectionRelativeZ(local));
    }

    // public BlockPos relativeToBlockPos(short pos) {
    // return new BlockPos(this.relativeToBlockX(pos), this.relativeToBlockY(pos),
    // this.relativeToBlockZ(pos));
    // }
    @Overwrite
    public BlockPos relativeToBlockPos(short local) {
        int bx = (int) (((long) x() << 4) + (long) sectionRelativeX(local));
        int by = (int) (((long) y() << 4) + (long) sectionRelativeY(local));
        int bz = (int) (((long) z() << 4) + (long) sectionRelativeZ(local));
        return new BlockPos(bx, by, bz);
    }

    // public static void aroundAndAtBlockPos(long pos, LongConsumer consumer) {
    // aroundAndAtBlockPos(BlockPos.getX(pos), BlockPos.getY(pos),
    // BlockPos.getZ(pos), consumer);
    // }
    @Overwrite
    public static void aroundAndAtBlockPos(long pos, LongConsumer consumer) {
        IntBlockPos bp = getBlockPos(pos);
        SectionPos.aroundAndAtBlockPos(new BlockPos(bp.x, bp.y, bp.z), Objects.requireNonNull(consumer));
    }
}
