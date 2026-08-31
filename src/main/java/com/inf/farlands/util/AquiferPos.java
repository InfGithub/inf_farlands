package com.inf.farlands.util;

/**
 * Aquifer 专用位置载体，含 lastAccess TTL 保活。
 *
 * 与 {@link IntBlockPos} 分离：block 空间的 blockLookup 用 swap 窗口清理、
 * 无 lastAccess；aquifer 的 aquiferLocationCache 跨 tick
 * 长期反查需要 TTL 保活，生成期活跃不清理——lastAccess 曾误加在 IntBlockPos
 * 通用类，本类隔离，恢复 IntBlockPos 无 lastAccess 设计。
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
