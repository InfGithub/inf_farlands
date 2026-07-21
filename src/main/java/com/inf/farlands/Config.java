package com.inf.farlands;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER =
        new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue BETA_TERRAIN =
        BUILDER.comment(
            "Use beta 1.7.3 terrain density function instead of vanilla noise."
        ).define("betaTerrain", true);

    private static final ModConfigSpec.IntValue BORDER_ABSOLUTE_MAX =
        BUILDER.comment(
            "The maximum world border size (both absoluteMaxSize and current size).",
            "Default 2147483647 (Integer.MAX_VALUE). Vanilla is 29999984."
        ).defineInRange(
            "borderAbsoluteMax",
            Integer.MAX_VALUE,
            1,
            Integer.MAX_VALUE
        );

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean betaTerrain = true;
    public static int borderAbsoluteMax = Integer.MAX_VALUE;

    static void onLoad(final ModConfigEvent event) {
        betaTerrain = BETA_TERRAIN.get();
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
    }
}
