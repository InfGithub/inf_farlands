package com.inf.farlands;

public class IntSectionPos {
    public final int x, y, z;

    public IntSectionPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static int blockToSectionCoord(int blockCoord) {
        return blockCoord >> 4;
    }

    public static int sectionRelative(int blockCoord) {
        return blockCoord & 15;
    }

    public int minBlockX() { return x << 4; }
    public int minBlockY() { return y << 4; }
    public int minBlockZ() { return z << 4; }

    public int compareTo(IntSectionPos o) {
        int c = Integer.compare(this.x, o.x);
        if (c != 0) return c;
        c = Integer.compare(this.y, o.y);
        if (c != 0) return c;
        return Integer.compare(this.z, o.z);
    }

    public IntSectionPos offset(int dx, int dy, int dz) {
        return new IntSectionPos(x + dx, y + dy, z + dz);
    }
}
