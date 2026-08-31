package com.inf.farlands.util.tick;

import com.inf.farlands.InfSFarlands;
import com.inf.farlands.util.maps.block.BlockUtil;

public class TickEnd {
    private static final int TRIM_INTERVAL = 200;
    private static int now = 0;

    public static void end(int tickCount) {
        now = tickCount;
        if (tickCount % TRIM_INTERVAL == 0) {
            int size = BlockUtil.size();
            InfSFarlands.LOGGER.info("Swaping blockLookup, count: {}", size);
            BlockUtil.swap();
        }
    }

    public static int getNow() {
        return now;
    }
}
