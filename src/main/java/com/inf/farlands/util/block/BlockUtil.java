package com.inf.farlands.util.block;

import com.inf.farlands.util.map.Long2ObjectStripedMap;
import com.inf.farlands.util.pos.IntBlockPos;
import com.inf.farlands.InfSFarlands;

public class BlockUtil {
    private static final int INITIAL_BLOCK_CAP = 1 << 23; // 8M 初始容量

    private static volatile Long2ObjectStripedMap<IntBlockPos> blockLookup = new Long2ObjectStripedMap<>(
            INITIAL_BLOCK_CAP);
    private static volatile Long2ObjectStripedMap<IntBlockPos> oldBlockLookup;

    public static void putBlock(long key, int x, int y, int z) {
        IntBlockPos prev = blockLookup.get(key);
        if (prev != null) {
            if (prev.x != x || prev.y != y || prev.z != z) {
                conflictInfo("block", key, prev.x, prev.y, prev.z, x, y, z);
            }
            return;
        }
        IntBlockPos np = new IntBlockPos(x, y, z);
        prev = blockLookup.putIfAbsent(key, np);
        if (prev != null) {
            // 竞态，并发注册，np 成垃圾
            if (prev.x != x || prev.y != y || prev.z != z) {
                conflictInfo("block", key, prev.x, prev.y, prev.z, x, y, z);
            }
        }
    }

    public static IntBlockPos getBlock(long key) {
        IntBlockPos bp = blockLookup.get(key);
        if (bp != null)
            return bp;
        Long2ObjectStripedMap<IntBlockPos> old = oldBlockLookup;
        return old != null ? old.get(key) : null;
    }

    public static int getSize() {
        return blockLookup.size();
    }

    // --------------------------------------------

    public static void swapBlockLookup() {
        oldBlockLookup = blockLookup;
        blockLookup = new Long2ObjectStripedMap<>(INITIAL_BLOCK_CAP);
    }

    private static volatile long lastConflictInfo = 0;

    private static void conflictInfo(String kind, long key, int ox, int oy, int oz, int nx, int ny, int nz) {
        long now = System.currentTimeMillis();
        if (now - lastConflictInfo < 1000) {
            return;
        }
        lastConflictInfo = now;
        InfSFarlands.LOGGER.info("HASHCONFLICT {} key=0x{} old={},{},{} new={},{},{}",
                kind, Long.toHexString(key), ox, oy, oz, nx, ny, nz);
    }
}
