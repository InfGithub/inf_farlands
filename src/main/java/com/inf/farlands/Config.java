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

    private static final ModConfigSpec.BooleanValue BETA_TERRAIN = BUILDER.comment(
            "Use beta 1.7.3 terrain density function instead of vanilla noise.").define("betaTerrain", true);

    private static final ModConfigSpec.IntValue VERTICAL_RENDER_DISTANCE = BUILDER.comment(
            "Vertical render distance in sections (16 blocks each).",
            "The loaded Y range is player section ± this value.",
            "Must be ≥ 17 to match the 34-section window; default 17 (35 grid slots).")
            .defineInRange("verticalRenderDistance", 17, 1, 34);

    private static final ModConfigSpec.IntValue WORLD_GEN_MAX_Y = BUILDER.comment(
            "Maximum absolute Y coordinate for world generation (terrain, features, structures).",
            "Sections above this Y will not generate.",
            "Default 2147483632 (MAX_BLOCK = 2^31-16, the first buffer-band block; the last",
            "playable block is MAX_BLOCK-1 = 2147483631). The 16-block band up to Integer.MAX_VALUE",
            "is the world edge buffer, excluded from normal logic.",
            "This is NOT the world border - it only limits terrain generation height.")
            .defineInRange("worldGenMaxY", Constants.MAX_BLOCK, -Constants.MAX_BLOCK, Constants.MAX_BLOCK);

    private static final ModConfigSpec.IntValue WORLD_GEN_MIN_Y = BUILDER.comment(
            "Minimum absolute Y coordinate for world generation (terrain, features, structures).",
            "Sections below this Y will not generate.",
            "Default -2147483632 (-MAX_BLOCK, the last playable block on the negative side;",
            "the 16-block band down to Integer.MIN_VALUE is the world edge buffer).",
            "This is NOT the world border - it only limits terrain generation depth.")
            .defineInRange("worldGenMinY", -Constants.MAX_BLOCK, -Constants.MAX_BLOCK, Constants.MAX_BLOCK);

    private static final ModConfigSpec.IntValue SECTION_SEND_BYTES_PER_TICK = BUILDER.comment(
            "Max bytes of window-slide section data sent per tick per player (window.md 4.2).",
            "Default 131072 (128KB/tick).")
            .defineInRange("sectionSendBytesPerTick", 131072, 1024, 1048576);

    private static final ModConfigSpec.IntValue MAX_CAP_ITER = BUILDER.comment(
            "Maximum iteration/search cap for extreme-Y loops: sky-light section search,",
            "pathfinding support-block scan, block-entity Y search, heightmap-adjacent caps.",
            "Prevents 2.14B-iteration loops when vanilla finite-height assumptions break",
            "(light uses section units, scans use block units - the same value applies to both).",
            "Default 512.")
            .defineInRange("maxCapIter", 512, 1, 65536);

    private static final ModConfigSpec.BooleanValue OUTSIDE = BUILDER.comment(
            "Outside-world mode: when true, the six-sided boundary mechanisms (physical walls,",
            "out-of-border damage, wall rendering, red vignette) are all disabled so entities",
            "can leave the world (packet clamps still hold as the last line against int overflow).",
            "When false (default), all six faces enforce the boundary like vanilla XZ does.")
            .define("outside", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int borderAbsoluteMax = Integer.MAX_VALUE;
    public static boolean disableFluidSpread = true;
    public static boolean betaTerrain = true;
    public static int verticalRenderDistance = 17;
    public static int worldGenMaxY = Constants.MAX_BLOCK;
    public static int worldGenMinY = -Constants.MAX_BLOCK;
    public static int sectionSendBytesPerTick = 131072;
    public static boolean outside = false;
    public static int maxCapIter = 512;

    static void onLoad(final ModConfigEvent event) {
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
        disableFluidSpread = DISABLE_FLUID_SPREAD.get();
        betaTerrain = BETA_TERRAIN.get();
        verticalRenderDistance = VERTICAL_RENDER_DISTANCE.get();
        worldGenMaxY = WORLD_GEN_MAX_Y.get();
        worldGenMinY = WORLD_GEN_MIN_Y.get();
        sectionSendBytesPerTick = SECTION_SEND_BYTES_PER_TICK.get();
        outside = OUTSIDE.get();
        maxCapIter = MAX_CAP_ITER.get();
    }
}
