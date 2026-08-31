package com.inf.farlands.util.maps;

import com.inf.farlands.InfSFarlands;

public class Common {
    private static volatile long lastConflictInfo = 0;

    public static void conflict(String kind, long key, int ox, int oy, int oz, int nx, int ny, int nz) {
        long now = System.currentTimeMillis();
        if (now - lastConflictInfo < 1000) {
            return;
        }
        lastConflictInfo = now;
        InfSFarlands.LOGGER.warn("HASHCONFLICT {} key=0x{} old={},{},{} new={},{},{}",
                kind, Long.toHexString(key), ox, oy, oz, nx, ny, nz);
    }
}
