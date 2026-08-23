package com.inf.farlands;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- 世界生成 / 地形 ----

    private static final ModConfigSpec.BooleanValue BETA_TERRAIN = BUILDER.comment(
            "",
            " Use beta 1.7.3 terrain density function instead of vanilla noise.",
            "",
            " 使用 beta 1.7.3 地形密度函数替代 vanilla 噪声。",
            "")
            .define("betaTerrain", true);

    private static final ModConfigSpec.IntValue WORLD_GEN_MAX_Y = BUILDER.comment(
            "",
            " Maximum absolute Y for world generation.",
            "",
            " 世界生成的绝对最大 Y。",
            "")
            .defineInRange("worldGenMaxY", Constants.MAX_BLOCK, -Constants.MAX_BLOCK, Constants.MAX_BLOCK);

    private static final ModConfigSpec.IntValue WORLD_GEN_MIN_Y = BUILDER.comment(
            "",
            " Minimum absolute Y for world generation.",
            "",
            " 世界生成的绝对最小 Y。",
            "")
            .defineInRange("worldGenMinY", -Constants.MAX_BLOCK, -Constants.MAX_BLOCK, Constants.MAX_BLOCK);

    private static final ModConfigSpec.BooleanValue DISABLE_FLUID_SPREAD = BUILDER.comment(
            "",
            " Disable water and lava spreading. Useful when massive water bodies",
            " in Far Lands cause severe TPS drops.",
            "",
            " 禁用水和岩浆蔓延。边境之地大量水体造成严重 TPS 下降时有用。",
            "")
            .define("disableFluidSpread", true);

    // ---- 世界边界 ----

    private static final ModConfigSpec.IntValue BORDER_ABSOLUTE_MAX = BUILDER.comment(
            "",
            " Maximum world border size.",
            "",
            " 世界边界最大尺寸。",
            "")
            .defineInRange("borderAbsoluteMax", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    private static final ModConfigSpec.BooleanValue OUTSIDE = BUILDER.comment(
            "",
            " Allow entities to leave the world, disabling the six-sided boundary.",
            "",
            " 允许实体离开世界，禁用六面边界。",
            "")
            .define("outside", false);

    // ---- 渲染 / 加载 ----

    private static final ModConfigSpec.IntValue VERTICAL_RENDER_DISTANCE = BUILDER.comment(
            "",
            " Vertical render distance, in sections.",
            "",
            " 垂直渲染距离，单位 section。",
            "")
            .defineInRange("verticalRenderDistance", 17, 1, 34);

    private static final ModConfigSpec.IntValue VERTICAL_SIMULATION_DISTANCE = BUILDER.comment(
            "",
            " Vertical simulation distance, in sections (single-sided radius).",
            " Controls the loaded/simulated Y window, entity ticking, random ticks and light.",
            "",
            " 垂直模拟距离，单位 section（单侧半径）。",
            " 控制加载/模拟的 Y 窗口、实体 tick、随机刻与光照。",
            "")
            .defineInRange("verticalSimulationDistance", 17, 1, 64);

    private static final ModConfigSpec.IntValue SECTION_SEND_BYTES_PER_TICK = BUILDER.comment(
            "",
            " Max window-slide section bytes sent per tick per player.",
            "",
            " 每 tick 每玩家发送的窗口滑动 section 最大字节数。",
            "")
            .defineInRange("sectionSendBytesPerTick", 131072, 1024, 1048576);

    // ---- 通用循环上限 ----

    private static final ModConfigSpec.IntValue MAX_CAP_ITER = BUILDER.comment(
            "",
            " Iteration/search cap for extreme-Y loops.",
            "",
            " 极端 Y 循环的迭代/搜索上限。",
            "")
            .defineInRange("maxCapIter", 512, 1, 65536);

    // ---- 光照 ----

    private static final ModConfigSpec.IntValue PARALLEL_LIGHT_THREADS = BUILDER.comment(
            "",
            " Server-side light propagation threads. 0 = auto: half of CPU logical processors;",
            " 1 = single background thread; N = exactly N threads.",
            "",
            " 服务端光照传播线程数。0 = 自动：CPU 逻辑线程数一半；1 = 单个后台线程；",
            " N = 恰好 N 个线程。",
            "")
            .defineInRange("parallelLightThreads", 0, 0, 64);

    private static final ModConfigSpec.IntValue MAX_LIGHT_TASKS_PER_TICK = BUILDER.comment(
            "",
            " Max light tasks scheduled per tick.",
            "",
            " 每 tick 调度的光照任务最大数。",
            "")
            .defineInRange("maxLightTasksPerTick", 4, 1, 64);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean betaTerrain = true;
    public static int worldGenMaxY = Constants.MAX_BLOCK;
    public static int worldGenMinY = -Constants.MAX_BLOCK;
    public static boolean disableFluidSpread = true;
    public static int borderAbsoluteMax = Integer.MAX_VALUE;
    public static boolean outside = false;
    public static int verticalRenderDistance = 17;
    public static int verticalSimulationDistance = 17;
    public static int sectionSendBytesPerTick = 131072;
    public static int maxCapIter = 512;
    public static int parallelLightThreads = 0;
    public static int maxLightTasksPerTick = 4;

    static void onLoad(final ModConfigEvent event) {
        betaTerrain = BETA_TERRAIN.get();
        worldGenMaxY = WORLD_GEN_MAX_Y.get();
        worldGenMinY = WORLD_GEN_MIN_Y.get();
        disableFluidSpread = DISABLE_FLUID_SPREAD.get();
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
        outside = OUTSIDE.get();
        verticalRenderDistance = VERTICAL_RENDER_DISTANCE.get();
        verticalSimulationDistance = VERTICAL_SIMULATION_DISTANCE.get();
        sectionSendBytesPerTick = SECTION_SEND_BYTES_PER_TICK.get();
        maxCapIter = MAX_CAP_ITER.get();
        parallelLightThreads = PARALLEL_LIGHT_THREADS.get();
        maxLightTasksPerTick = MAX_LIGHT_TASKS_PER_TICK.get();
    }
}