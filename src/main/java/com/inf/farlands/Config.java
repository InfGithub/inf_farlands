package com.inf.farlands;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.IntValue BORDER_ABSOLUTE_MAX = BUILDER.comment(
            "The maximum world border size (both absoluteMaxSize and current size).",
            "Default 2147483647 (Integer.MAX_VALUE). Vanilla is 29999984 or 30000000.").defineInRange(
                    "borderAbsoluteMax",
                    Integer.MAX_VALUE,
                    1,
                    Integer.MAX_VALUE);
    private static final ModConfigSpec.BooleanValue DISABLE_FLUID_SPREAD = BUILDER.comment(
            "Completely disable fluid (water/lava) spreading.",
            "Useful in Far Lands where massive water bodies cause severe TPS drops.")
            .define("disableFluidSpread", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int borderAbsoluteMax = Integer.MAX_VALUE;
    public static boolean disableFluidSpread = true;

    static void onLoad(final ModConfigEvent event) {
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
        disableFluidSpread = DISABLE_FLUID_SPREAD.get();
    }
}
