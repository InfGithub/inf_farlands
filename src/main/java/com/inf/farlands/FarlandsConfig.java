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

    public static final ConfigEntry<Integer> WORLD_GEN_MIN_Y = Config.register(
            "worldGenMinY",
            int.class,
            -FarlandsConstant.MAX_BLOCK,
            Map.of("en_us", "Minimum absolute Y for world generation", "zh_cn", "世界生成的绝对最小 Y"));
    public static final int worldGenMinY;

    public static final ConfigEntry<Integer> WORLD_GEN_MAX_Y = Config.register(
            "worldGenMaxY",
            int.class,
            FarlandsConstant.MAX_BLOCK,
            Map.of("en_us", "Maximum absolute Y for world generation", "zh_cn", "世界生成的绝对最大 Y"));
    public static final int worldGenMaxY;

    static {
        Config.init();
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
        worldGenMinY = WORLD_GEN_MIN_Y.get();
        worldGenMaxY = WORLD_GEN_MAX_Y.get();
    }
}
