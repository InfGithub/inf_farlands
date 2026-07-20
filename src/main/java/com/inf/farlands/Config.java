package com.inf.farlands;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {

    private static final ModConfigSpec.Builder BUILDER =
        new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLE_FAR_LANDS =
        BUILDER.comment(
            "Set to false to completely disable Far Lands restoration."
        ).define("enableFarLands", true);

    private static final ModConfigSpec.IntValue OCTAVE_ACCEL_THRESHOLD =
        BUILDER.comment(
            "The octave index (0-based) after which inputFactor growth accelerates.",
            "Default 12 keeps the first 12 octaves at 2x growth and accelerates the rest.",
            "Lower values = stronger Far Lands closer to spawn. Range: 0-30"
        ).defineInRange("octaveAccelThreshold", 12, 0, 30);

    private static final ModConfigSpec.DoubleValue ACCEL_MULTIPLIER =
        BUILDER.comment(
            "The multiplier applied to inputFactor after the threshold octave.",
            "Default 4.0. 2.0 = vanilla behaviour (no acceleration).",
            "Higher values = stronger Far Lands effects. Range: 1.0-16.0"
        ).defineInRange("accelMultiplier", 4.0, 1.0, 16.0);

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

    private static final ModConfigSpec.IntValue VERTICAL_VIEW_DISTANCE =
        BUILDER.comment(
            "Vertical cube loading radius (in cubes, 1 cube = 16 blocks).",
            "Only cubes within this vertical distance of a player are loaded for ticking.",
            "Default 8 (128 blocks)."
        ).defineInRange("verticalViewDistance", 8, 1, 64);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enableFarLands = true;
    public static int octaveAccelThreshold = 12;
    public static double accelMultiplier = 4.0;
    public static int borderAbsoluteMax = Integer.MAX_VALUE;
    public static int verticalViewDistance = 8;

    static void onLoad(final ModConfigEvent event) {
        enableFarLands = ENABLE_FAR_LANDS.get();
        octaveAccelThreshold = OCTAVE_ACCEL_THRESHOLD.get();
        accelMultiplier = ACCEL_MULTIPLIER.get();
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
        verticalViewDistance = VERTICAL_VIEW_DISTANCE.get();
    }
}
