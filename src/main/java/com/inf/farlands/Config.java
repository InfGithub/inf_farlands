package com.inf.farlands;

import com.inf.farlands.terrain.system.BiomeSystemType;
import com.inf.farlands.terrain.system.CarverSystemType;
import com.inf.farlands.terrain.system.SurfaceSystemType;
import com.inf.farlands.terrain.system.TerrainSystemType;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- 世界生成 / 地形 ----

    private static final ModConfigSpec.EnumValue<TerrainSystemType> OVERWORLD_TERRAIN_SYSTEM = BUILDER.comment(
            "",
            " Terrain system for overworld generation.",
            "",
            " 主世界地形系统。",
            "")
            .defineEnum("overworldTerrainSystem", TerrainSystemType.BETA_1_7_3_OVERWORLD);

    private static final ModConfigSpec.EnumValue<TerrainSystemType> NETHER_TERRAIN_SYSTEM = BUILDER.comment(
            "",
            " Terrain system for the nether generation.",
            "",
            " 下界地形系统。",
            "")
            .defineEnum("theNetherTerrainSystem", TerrainSystemType.VANILLA_THE_NETHER);

    private static final ModConfigSpec.EnumValue<TerrainSystemType> END_TERRAIN_SYSTEM = BUILDER.comment(
            "",
            " Terrain system for the end generation.",
            "",
            " 末地地形系统。",
            "")
            .defineEnum("theEndTerrainSystem", TerrainSystemType.VANILLA_THE_END);

    private static final ModConfigSpec.EnumValue<BiomeSystemType> OVERWORLD_BIOME_SYSTEM = BUILDER.comment(
            "",
            " Biome system for overworld biomes.",
            "",
            " 主世界群系系统。",
            "")
            .defineEnum("overworldBiomeSystem", BiomeSystemType.VANILLA_OVERWORLD);

    private static final ModConfigSpec.EnumValue<BiomeSystemType> NETHER_BIOME_SYSTEM = BUILDER.comment(
            "",
            " Biome system for the nether biomes.",
            "",
            " 下界群系系统。",
            "")
            .defineEnum("theNetherBiomeSystem", BiomeSystemType.VANILLA_THE_NETHER);

    private static final ModConfigSpec.EnumValue<BiomeSystemType> END_BIOME_SYSTEM = BUILDER.comment(
            "",
            " Biome system for the end biomes.",
            "",
            " 末地群系系统。",
            "")
            .defineEnum("theEndBiomeSystem", BiomeSystemType.VANILLA_THE_END);

    private static final ModConfigSpec.EnumValue<SurfaceSystemType> OVERWORLD_SURFACE_SYSTEM = BUILDER.comment(
            "",
            " Surface system for overworld terrain.",
            "",
            " 主世界地表系统。",
            "")
            .defineEnum("overworldSurfaceSystem", SurfaceSystemType.VANILLA_OVERWORLD);

    private static final ModConfigSpec.EnumValue<SurfaceSystemType> NETHER_SURFACE_SYSTEM = BUILDER.comment(
            "",
            " Surface system for the nether terrain.",
            "",
            " 下界地表系统。",
            "")
            .defineEnum("theNetherSurfaceSystem", SurfaceSystemType.VANILLA_THE_NETHER);

    private static final ModConfigSpec.EnumValue<SurfaceSystemType> END_SURFACE_SYSTEM = BUILDER.comment(
            "",
            " Surface system for the end terrain.",
            "",
            " 末地地表系统。",
            "")
            .defineEnum("theEndSurfaceSystem", SurfaceSystemType.VANILLA_THE_END);

    private static final ModConfigSpec.EnumValue<CarverSystemType> OVERWORLD_CARVER_SYSTEM = BUILDER.comment(
            "",
            " Carver system for overworld terrain.",
            "",
            " 主世界雕刻系统。",
            "")
            .defineEnum("overworldCarverSystem", CarverSystemType.VANILLA_OVERWORLD);

    private static final ModConfigSpec.EnumValue<CarverSystemType> NETHER_CARVER_SYSTEM = BUILDER.comment(
            "",
            " Carver system for the nether terrain.",
            "",
            " 下界雕刻系统。",
            "")
            .defineEnum("theNetherCarverSystem", CarverSystemType.VANILLA_THE_NETHER);

    private static final ModConfigSpec.EnumValue<CarverSystemType> END_CARVER_SYSTEM = BUILDER.comment(
            "",
            " Carver system for the end terrain.",
            "",
            " 末地雕刻系统。",
            "")
            .defineEnum("theEndCarverSystem", CarverSystemType.VANILLA_THE_END);

    private static final ModConfigSpec.BooleanValue TOP_FADE_ENABLED = BUILDER.comment(
            "",
            " Enable the top fade terms in terrain density formulas.",
            "",
            " 地形密度公式的顶部渐消项开关。",
            "")
            .define("topFadeEnabled", false);

    private static final ModConfigSpec.BooleanValue BOTTOM_FADE_ENABLED = BUILDER.comment(
            "",
            " Enable the bottom fade terms in terrain density formulas.",
            "",
            " 地形密度公式的底部渐消项开关。",
            "")
            .define("bottomFadeEnabled", false);

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
            " Disable water and lava spreading.",
            "",
            " 禁用水和岩浆蔓延。",
            "")
            .define("disableFluidSpread", false);

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
            " Vertical render distance.",
            "",
            " 垂直渲染距离。",
            "")
            .defineInRange("verticalRenderDistance", 17, 1, 34);

    private static final ModConfigSpec.IntValue VERTICAL_SIMULATION_DISTANCE = BUILDER.comment(
            "",
            " Radius of the vertical simulation distance.",
            "",
            " 垂直模拟距离的半径。",
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

    // ---- 地形管线 ----

    private static final ModConfigSpec.IntValue MAX_GEN_TASKS_PER_TICK = BUILDER.comment(
            "",
            " Max terrain-pipeline gen tasks consumed per wake (loose cap).",
            "",
            " 地形管线每次唤醒消费的生成任务上限。",
            "")
            .defineInRange("maxGenTasksPerTick", 2000, 1, 65536);

    private static final ModConfigSpec.IntValue GEN_WORKER_THREADS = BUILDER.comment(
            "",
            " Terrain generation worker threads. 0 = auto: half of CPU logical processors;",
            " 1 = single background thread; N = exactly N threads.",
            "",
            " 地形生成线程数。0 = 自动：CPU 逻辑线程数一半；1 = 单个后台线程；",
            " N = 恰好 N 个线程。",
            "")
            .defineInRange("genWorkerThreads", 0, 0, 64);

    // ---- fsa 序列化 ----

    private static final ModConfigSpec.IntValue FSA_CACHE_LIMIT = BUILDER.comment(
            "",
            " Maximum number of open fsa files kept in cache.",
            "",
            " 打开的 fsa 文件缓存上限。",
            "")
            .defineInRange("fsaCacheLimit", 128, 16, 1024);

    private static final ModConfigSpec.IntValue FSA_CLEANUP_MARGIN = BUILDER.comment(
            "",
            " Sections outside all player windows plus this margin are cleanup candidates.",
            "",
            " 玩家窗口并集外加上该余量的 section 为清理候选。",
            "")
            .defineInRange("fsaCleanupMargin", 8, 0, 64);

    private static final ModConfigSpec.LongValue FSA_PERSIST_INTERVAL = BUILDER.comment(
            "",
            " Interval in ticks between periodic persistence of dirty sections.",
            "",
            " 定期持久化脏 section 的间隔，单位 tick。",
            "")
            .defineInRange("fsaPersistInterval", 6000L, 100L, 120000L);

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

    public static BiomeSystemType overworldBiomeSystem = BiomeSystemType.VANILLA_OVERWORLD;
    public static BiomeSystemType netherBiomeSystem = BiomeSystemType.VANILLA_THE_NETHER;
    public static BiomeSystemType endBiomeSystem = BiomeSystemType.VANILLA_THE_END;
    public static TerrainSystemType overworldTerrainSystem = TerrainSystemType.BETA_1_7_3_OVERWORLD;
    public static TerrainSystemType netherTerrainSystem = TerrainSystemType.VANILLA_THE_NETHER;
    public static TerrainSystemType endTerrainSystem = TerrainSystemType.VANILLA_THE_END;
    public static SurfaceSystemType overworldSurfaceSystem = SurfaceSystemType.VANILLA_OVERWORLD;
    public static SurfaceSystemType netherSurfaceSystem = SurfaceSystemType.VANILLA_THE_NETHER;
    public static SurfaceSystemType endSurfaceSystem = SurfaceSystemType.VANILLA_THE_END;
    public static CarverSystemType overworldCarverSystem = CarverSystemType.VANILLA_OVERWORLD;
    public static CarverSystemType netherCarverSystem = CarverSystemType.VANILLA_THE_NETHER;
    public static CarverSystemType endCarverSystem = CarverSystemType.VANILLA_THE_END;
    public static boolean topFadeEnabled = false;
    public static boolean bottomFadeEnabled = false;
    public static int worldGenMaxY = Constants.MAX_BLOCK;
    public static int worldGenMinY = -Constants.MAX_BLOCK;
    public static boolean disableFluidSpread = true;
    public static int borderAbsoluteMax = Integer.MAX_VALUE;
    public static boolean outside = false;
    public static int verticalRenderDistance = 17;
    public static int verticalSimulationDistance = 17;
    public static int sectionSendBytesPerTick = 131072;
    public static int maxCapIter = 512;
    public static int maxGenTasksPerTick = 2000;
    public static int genWorkerThreads = 0;
    public static int fsaCacheLimit = 128;
    public static int fsaCleanupMargin = 8;
    public static long fsaPersistInterval = 6000;
    public static int parallelLightThreads = 0;
    public static int maxLightTasksPerTick = 4;

    static void onLoad(final ModConfigEvent event) {
        overworldBiomeSystem = OVERWORLD_BIOME_SYSTEM.get();
        netherBiomeSystem = NETHER_BIOME_SYSTEM.get();
        endBiomeSystem = END_BIOME_SYSTEM.get();
        overworldTerrainSystem = OVERWORLD_TERRAIN_SYSTEM.get();
        netherTerrainSystem = NETHER_TERRAIN_SYSTEM.get();
        endTerrainSystem = END_TERRAIN_SYSTEM.get();
        overworldSurfaceSystem = OVERWORLD_SURFACE_SYSTEM.get();
        netherSurfaceSystem = NETHER_SURFACE_SYSTEM.get();
        endSurfaceSystem = END_SURFACE_SYSTEM.get();
        overworldCarverSystem = OVERWORLD_CARVER_SYSTEM.get();
        netherCarverSystem = NETHER_CARVER_SYSTEM.get();
        endCarverSystem = END_CARVER_SYSTEM.get();
        topFadeEnabled = TOP_FADE_ENABLED.get();
        bottomFadeEnabled = BOTTOM_FADE_ENABLED.get();
        worldGenMaxY = WORLD_GEN_MAX_Y.get();
        worldGenMinY = WORLD_GEN_MIN_Y.get();
        disableFluidSpread = DISABLE_FLUID_SPREAD.get();
        borderAbsoluteMax = BORDER_ABSOLUTE_MAX.get();
        outside = OUTSIDE.get();
        verticalRenderDistance = VERTICAL_RENDER_DISTANCE.get();
        verticalSimulationDistance = VERTICAL_SIMULATION_DISTANCE.get();
        sectionSendBytesPerTick = SECTION_SEND_BYTES_PER_TICK.get();
        maxCapIter = MAX_CAP_ITER.get();
        maxGenTasksPerTick = MAX_GEN_TASKS_PER_TICK.get();
        genWorkerThreads = GEN_WORKER_THREADS.get();
        fsaCacheLimit = FSA_CACHE_LIMIT.get();
        fsaCleanupMargin = FSA_CLEANUP_MARGIN.get();
        fsaPersistInterval = FSA_PERSIST_INTERVAL.get();
        parallelLightThreads = PARALLEL_LIGHT_THREADS.get();
        maxLightTasksPerTick = MAX_LIGHT_TASKS_PER_TICK.get();
    }
}