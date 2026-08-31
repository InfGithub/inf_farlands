package com.inf.farlands;

import java.util.Map;

import com.inf.farlands.util.config.Config;
import com.inf.farlands.util.config.ConfigEntry;

public class FarlandsConfig {
    public static final ConfigEntry<Integer> BORDER_ABSOLUTE_MAX = Config.register(
            "borderAbsoluteMax",
            int.class,
            FarlandsConstant.MAX_BLOCK,
            Map.of("en_us", "Maximum world border size", "zh_cn", "世界边界最大尺寸"));
    public static final int borderAbsoluteMax;

    static {
        Config.init();
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
    }
}
