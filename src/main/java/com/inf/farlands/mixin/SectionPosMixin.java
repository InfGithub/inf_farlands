package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.InfFarlands;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionPos.class)
public abstract class SectionPosMixin {

    // ---- Hash ----

    @Overwrite
    public long asLong() {
        int x = ((SectionPos)(Object)this).x();
        int y = ((SectionPos)(Object)this).y();
        int z = ((SectionPos)(Object)this).z();
        long key = hashSection((long)x, (long)y, (long)z);
        HashUtil.sectionLookup.putIfAbsent(key, new IntSectionPos(x, y, z));
        return key;
    }

    @Overwrite
    public static long asLong(int x, int y, int z) {
        long key = hashSection((long)x, (long)y, (long)z);
        HashUtil.sectionLookup.putIfAbsent(key, new IntSectionPos(x, y, z));
        return key;
    }

    private static long hashSection(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= Long.rotateLeft(z * 0x9E3779B97F4A7C15L, 21);
        h ^= Long.rotateLeft(y * 0x9E3779B97F4A7C15L, 42);
        return h;
    }

    // ---- Unpack from side channel ----

    @Overwrite
    public static int x(long packed) {
        IntSectionPos p = HashUtil.sectionLookup.get(packed);
        if (p != null) { p.lastAccess = InfFarlands.getServerTickCount(); return p.x; }
        return (int)(packed >> 42);
    }

    @Overwrite
    public static int y(long packed) {
        IntSectionPos p = HashUtil.sectionLookup.get(packed);
        if (p != null) { p.lastAccess = InfFarlands.getServerTickCount(); return p.y; }
        return (int)(packed << 44 >> 44);
    }

    @Overwrite
    public static int z(long packed) {
        IntSectionPos p = HashUtil.sectionLookup.get(packed);
        if (p != null) { p.lastAccess = InfFarlands.getServerTickCount(); return p.z; }
        return (int)(packed << 22 >> 42);
    }

    @Overwrite
    public static SectionPos of(long packed) {
        IntSectionPos p = HashUtil.sectionLookup.get(packed);
        if (p != null) return SectionPos.of(p.x, p.y, p.z);
        return SectionPos.of(x(packed), y(packed), z(packed));
    }

    // ---- Offset with side channel ----

    @Overwrite
    public static long offset(long packed, Direction direction) {
        return offset(packed, direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    @Overwrite
    public static long offset(long packed, int dx, int dy, int dz) {
        IntSectionPos p = HashUtil.sectionLookup.get(packed);
        int nx, ny, nz;
        if (p != null) { nx = p.x + dx; ny = p.y + dy; nz = p.z + dz; }
        else { nx = x(packed) + dx; ny = y(packed) + dy; nz = z(packed) + dz; }
        long key = hashSection((long)nx, (long)ny, (long)nz);
        HashUtil.sectionLookup.put(key, new IntSectionPos(nx, ny, nz));
        return key;
    }

    // ---- blockToSection: uses blockLookup to resolve block-level key ----

    @Overwrite
    public static long blockToSection(long levelPos) {
        IntBlockPos bp = HashUtil.blockLookup.get(levelPos);
        int sx, sy, sz;
        if (bp != null) {
            sx = bp.x >> 4;
            sy = bp.y >> 4;
            sz = bp.z >> 4;
        } else {
            sx = BlockPos.getX(levelPos) >> 4;
            sy = BlockPos.getY(levelPos) >> 4;
            sz = BlockPos.getZ(levelPos) >> 4;
        }
        long key = hashSection((long)sx, (long)sy, (long)sz);
        HashUtil.sectionLookup.put(key, new IntSectionPos(sx, sy, sz));
        return key;
    }

    // ---- getZeroNode: extract column key from section key ----

    @Overwrite
    public static long getZeroNode(long packed) {
        IntSectionPos p = HashUtil.sectionLookup.get(packed);
        long cx, cz;
        if (p != null) { cx = (long)p.x; cz = (long)p.z; }
        else { cx = (long)x(packed); cz = (long)z(packed); }
        long key = hashSection(cx, 0, cz);
        HashUtil.sectionLookup.put(key, new IntSectionPos((int)cx, 0, (int)cz));
        return key;
    }

    @Overwrite
    public static long getZeroNode(int x, int z) {
        long key = hashSection((long)x, 0, (long)z);
        HashUtil.sectionLookup.put(key, new IntSectionPos(x, 0, z));
        return key;
    }

    // ---- relativeToBlock: prevent int overflow at 2.14B coordinates ----

    @Shadow
    public abstract int x();
    @Shadow
    public abstract int y();
    @Shadow
    public abstract int z();

    @Shadow
    public static int sectionRelativeX(short packed) { return 0; }
    @Shadow
    public static int sectionRelativeY(short packed) { return 0; }
    @Shadow
    public static int sectionRelativeZ(short packed) { return 0; }

    @Overwrite
    public int relativeToBlockX(short local) {
        return (int)(((long)x() << 4) + (long)sectionRelativeX(local));
    }

    @Overwrite
    public int relativeToBlockY(short local) {
        return (int)(((long)y() << 4) + (long)sectionRelativeY(local));
    }

    @Overwrite
    public int relativeToBlockZ(short local) {
        return (int)(((long)z() << 4) + (long)sectionRelativeZ(local));
    }

    @Overwrite
    public net.minecraft.core.BlockPos relativeToBlockPos(short local) {
        int bx = (int)(((long)x() << 4) + (long)sectionRelativeX(local));
        int by = (int)(((long)y() << 4) + (long)sectionRelativeY(local));
        int bz = (int)(((long)z() << 4) + (long)sectionRelativeZ(local));
        return new net.minecraft.core.BlockPos(bx, by, bz);
    }

    // ---- minBlock/maxBlock: safe getters ----

    @Overwrite
    public int minBlockX() { return x() << 4; }
    @Overwrite
    public int minBlockY() { return y() << 4; }
    @Overwrite
    public int minBlockZ() { return z() << 4; }
    @Overwrite
    public int maxBlockX() { return (x() << 4) + 15; }
    @Overwrite
    public int maxBlockY() { return (y() << 4) + 15; }
    @Overwrite
    public int maxBlockZ() { return (z() << 4) + 15; }
}
