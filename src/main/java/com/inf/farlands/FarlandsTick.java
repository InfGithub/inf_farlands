package com.inf.farlands;

import com.inf.farlands.util.maps.AquiferUtil;
import com.inf.farlands.util.maps.BlockUtil;
import com.inf.farlands.util.maps.SectionUtil;

public class FarlandsTick {
    private static final int INTERVAL = 200;
    private static volatile int now = 0;

    public static int getNow() {
        return now;
    }

    private static void swapBlockLookup(int tickCount) {
        int size = BlockUtil.size();
        InfSFarlands.LOGGER.info("Swapping BlockUtil.lookup, size: {}", size);
        BlockUtil.swap();
    }

    public static void trimSectionLookup(int tickCount) {
        int beforeSize = SectionUtil.size();
        SectionUtil.trim(tickCount);
        int afterSize = SectionUtil.size();
        InfSFarlands.LOGGER.info("Trimmed SectionUtil.lookup, before: {}, after: {}", beforeSize, afterSize);
    }

    private static void trimAquiferLookup(int tickCount) {
        int beforeSize = AquiferUtil.size();
        AquiferUtil.trim(tickCount);
        int afterSize = AquiferUtil.size();
        InfSFarlands.LOGGER.info("Trimmed AquiferUtil.lookup, before: {}, after: {}", beforeSize, afterSize);
    }

    public static void atEnd(int tickCount) {
        now = tickCount;
        if (tickCount % INTERVAL == 0) {
            swapBlockLookup(tickCount);
            trimSectionLookup(tickCount);
            trimAquiferLookup(tickCount);
        }
    }
}
