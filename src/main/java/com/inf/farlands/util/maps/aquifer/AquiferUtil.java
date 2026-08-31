package com.inf.farlands.util.maps.aquifer;

import com.inf.farlands.util.pos.AquiferPos;
import com.inf.farlands.util.map.Long2ObjectStripedMap;
import com.inf.farlands.util.tick.TickEnd;

public class AquiferUtil {
    private static final Long2ObjectStripedMap<AquiferPos> lookup = new Long2ObjectStripedMap<>(1 << 20);

    public static AquiferPos get(long key) {
        AquiferPos bp = lookup.get(key);
        if (bp != null) {
            bp.lastAccess = TickEnd.getNow();
        }
        return bp;
    }

    public static void put(long key, int x, int y, int z) {
        AquiferPos prev = lookup.get(key);
        if (prev != null) {
            // 保活配合 TTL 600 trim：与现状每次 put 更新 lastAccess 语义一致
            prev.lastAccess = TickEnd.getNow();
            return;
        }
        AquiferPos np = new AquiferPos(x, y, z);
        np.lastAccess = TickEnd.getNow();
        AquiferPos race = lookup.putIfAbsent(key, np);
        if (race != null) {
            // 竞态：并发注册，np 成垃圾；现有条目保活
            race.lastAccess = TickEnd.getNow();
        }
    }

    public static void trim(long currentTick) {
        long cutoff = currentTick - 600;
        lookup.removeIf(p -> p.lastAccess < cutoff);
    }
}
