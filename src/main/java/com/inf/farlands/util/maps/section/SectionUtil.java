package com.inf.farlands.util.maps.section;

import com.inf.farlands.util.map.Long2ObjectStripedMap;
import com.inf.farlands.util.pos.IntSectionPos;
import com.inf.farlands.util.maps.Common;

public class SectionUtil {
    public static final Long2ObjectStripedMap<IntSectionPos> lookup = new Long2ObjectStripedMap<>(1 << 20);

    public static void put(long key, int x, int y, int z) {
        IntSectionPos prev = lookup.get(key);
        if (prev != null) {
            if (prev.x != x || prev.y != y || prev.z != z) {
                Common.conflict("section", key, prev.x, prev.y, prev.z, x, y, z);
            }
            return;
        }
        IntSectionPos np = new IntSectionPos(x, y, z);
        prev = lookup.putIfAbsent(key, np);
        if (prev != null) {
            if (prev.x != x || prev.y != y || prev.z != z) {
                Common.conflict("section", key, prev.x, prev.y, prev.z, x, y, z);
            }
        }
    }

    public static IntSectionPos get(long key) {
        return lookup.get(key);
    }
}
