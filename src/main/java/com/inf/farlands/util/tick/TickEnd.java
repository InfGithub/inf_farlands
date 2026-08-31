package com.inf.farlands.util.tick;

import com.inf.farlands.InfSFarlands;
import com.inf.farlands.util.maps.block.BlockUtil;
import com.inf.farlands.util.maps.aquifer.AquiferUtil;

public class TickEnd {
    private static final int INTERVAL = 200;
    private static int now = 0;

    private static void swapBlockLookup(int tickCount) {
        int size = BlockUtil.size();
        InfSFarlands.LOGGER.info("Swapping BlockUtil.lookup, size: {}", size);
        BlockUtil.swap();
    }

    private static void trimAquiferLookup(int tickCount) {
        int beforeSize = AquiferUtil.size();
        AquiferUtil.trim(tickCount);
        int afterSize = AquiferUtil.size();
        InfSFarlands.LOGGER.info("Trimmed AquiferUtil.lookup, before: {}, after: {}", beforeSize, afterSize);
    }

    public static void end(int tickCount) {
        now = tickCount;
        if (tickCount % INTERVAL == 0) {
            swapBlockLookup(tickCount);
            trimAquiferLookup(tickCount);
        }
    }

    public static int getNow() {
        return now;
    }
}
