package com.inf.farlands.util.tick;

import com.inf.farlands.util.block.BlockUtil;
import com.inf.farlands.InfSFarlands;

public class TickEnd {
    private static final int TRIM_INTERVAL = 200;

    public static void end(int tickCount) {
        if (tickCount % TRIM_INTERVAL == 0) {
            int size = BlockUtil.getSize();
            InfSFarlands.LOGGER.info("Swaping blockLookup, count: {}", size);
            BlockUtil.swapBlockLookup();
        }
    }
}
