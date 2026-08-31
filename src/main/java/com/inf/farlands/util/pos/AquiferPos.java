package com.inf.farlands.util.pos;

/**
 * Aquifer 专用位置载体，含 lastAccess TTL 保活。
 */
public class AquiferPos {
    public final int x, y, z;
    public volatile long lastAccess;

    public AquiferPos(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
