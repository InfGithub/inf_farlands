package com.inf.farlands.mixin.expand.xyz.pos;

import it.unimi.dsi.fastutil.longs.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.inf.farlands.FarlandsTick;
import com.inf.farlands.util.hash.HashMath;
import com.inf.farlands.util.maps.SectionUtil;
import com.inf.farlands.util.pos.IntBlockPos;
import com.inf.farlands.util.pos.IntSectionPos;

@Mixin(SectionPos.class)
public abstract class SectionPosMixin {

    @Overwrite
    public long asLong() {
        int x = ((SectionPos) (Object) this).x();
        int y = ((SectionPos) (Object) this).y();
        int z = ((SectionPos) (Object) this).z();
        long key = HashMath.hash(x, y, z);
        SectionUtil.put(key, x, y, z);
        return key;
    }

    @Overwrite
    public static long asLong(int x, int y, int z) {
        long key = HashMath.hash(x, y, z);
        SectionUtil.put(key, x, y, z);
        return key;
    }

    @Overwrite
    public static int x(long packed) {
        IntSectionPos p = SectionUtil.get(packed);
        if (p != null) {
            p.lastAccess = FarlandsTick.getNow();
            return p.x;
        }
        return (int) (packed >> 42);
    }

    @Overwrite
    public static int y(long packed) {
        IntSectionPos p = SectionUtil.get(packed);
        if (p != null) {
            p.lastAccess = FarlandsTick.getNow();
            return p.y;
        }
        return (int) (packed << 44 >> 44);
    }

    @Overwrite
    public static int z(long packed) {
        IntSectionPos p = SectionUtil.get(packed);
        if (p != null) {
            p.lastAccess = FarlandsTick.getNow();
            return p.z;
        }
        return (int) (packed << 22 >> 42);
    }

    @Overwrite
    public static SectionPos of(long packed) {
        IntSectionPos p = IntSectionPos.getSectionPos(packed);
        return SectionPos.of(p.x, p.y, p.z);
    }

    @Overwrite
    public static long offset(long packed, Direction direction) {
        return offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    @Overwrite
    public static long offset(long packed, int dx, int dy, int dz) {
        IntSectionPos p = IntSectionPos.getSectionPos(packed);
        int nx = p.x + dx, ny = p.y + dy, nz = p.z + dz;
        long key = HashMath.hash(nx, ny, nz);
        SectionUtil.put(key, nx, ny, nz);
        return key;
    }

    @Overwrite
    public static long blockToSection(long levelPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(levelPos);
        int sx = bp.x >> 4, sy = bp.y >> 4, sz = bp.z >> 4;
        long key = HashMath.hash(sx, sy, sz);
        SectionUtil.put(key, sx, sy, sz);
        return key;
    }

    @Overwrite
    public static long getZeroNode(long packed) {
        IntSectionPos p = IntSectionPos.getSectionPos(packed);
        long key = HashMath.hash(p.x, 0, p.z);
        SectionUtil.put(key, p.x, 0, p.z);
        return key;
    }

    @Overwrite
    public static long getZeroNode(int x, int z) {
        long key = HashMath.hash(x, 0, z);
        SectionUtil.put(key, x, 0, z);
        return key;
    }

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

    @Overwrite
    public BlockPos relativeToBlockPos(short local) {
        int bx = (int) (((long) x() << 4) + (long) sectionRelativeX(local));
        int by = (int) (((long) y() << 4) + (long) sectionRelativeY(local));
        int bz = (int) (((long) z() << 4) + (long) sectionRelativeZ(local));
        return new BlockPos(bx, by, bz);
    }

    @Overwrite
    public static void aroundAndAtBlockPos(long pos, LongConsumer consumer) {
        IntBlockPos bp = IntBlockPos.getBlockPos(pos);
        SectionPos.aroundAndAtBlockPos(new BlockPos(bp.x, bp.y, bp.z), consumer);
    }
}
