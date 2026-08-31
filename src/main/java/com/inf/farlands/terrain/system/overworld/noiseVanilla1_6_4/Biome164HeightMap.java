package com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.6.4 biome 高度场映射表（BIOME_HEIGHT_MAP.md 落地）。
 *
 * A 区：1.6.4 原版 23 个 biome 中直接对应现代的 16 个，值照搬原版
 * （BiomeGenBase.setMinMaxHeight 源码事实）。
 * B 区（死代码，注释保留）：1.6.4 有、现代已移除的 7 个 hills 变体——
 * desertHills/forestHills/taigaHills/jungleHills(1.8/0.5 原版反值)/extremeHillsEdge/
 * iceMountains/mushroomIslandShore。现代世界不会生成，不参与映射。
 * C 区：现代新增 biome 的用户审定值。
 *
 * 未收录 biome → 默认 (0.1, 0.3)（= 1.6.4 默认 minHeight/maxHeight）。
 */
@SuppressWarnings("null")
public final class Biome164HeightMap {

    /** [minHeight, maxHeight]，1.6.4 默认构造值（BiomeGenBase L102-103）。 */
    public static final float[] DEFAULT = { 0.1F, 0.3F };

    private static final Map<String, float[]> MAP = new HashMap<>();

    private Biome164HeightMap() {
    }

    private static void put(String key, float minH, float maxH) {
        MAP.put(key, new float[] { minH, maxH });
    }

    static {
        // ---- A 区：1.6.4 原版 16 个直接对应（照搬原值）----
        put("ocean", -1.0F, 0.4F);
        put("plains", 0.1F, 0.3F);        // 默认值（原版未调 setMinMaxHeight）
        put("desert", 0.1F, 0.2F);
        put("windswept_hills", 0.3F, 1.5F);   // extremeHills
        put("forest", 0.1F, 0.3F);        // 默认值
        put("taiga", 0.1F, 0.4F);
        put("swamp", -0.2F, 0.1F);        // swampland
        put("river", -0.5F, 0.0F);
        put("nether_wastes", 0.1F, 0.3F); // hell（不参与高度场，补默认）
        put("the_end", 0.1F, 0.3F);       // sky（不参与高度场，补默认）
        put("frozen_ocean", -1.0F, 0.5F);
        put("frozen_river", -0.5F, 0.0F);
        put("snowy_plains", 0.1F, 0.3F);  // icePlains 默认值
        put("mushroom_fields", 0.2F, 1.0F);   // mushroomIsland
        put("beach", 0.0F, 0.1F);
        put("jungle", 0.2F, 0.4F);

        // ---- C 区：现代新增（用户审定值）----

        // 山系
        put("jagged_peaks", 0.3F, 1.6F);
        put("frozen_peaks", 0.3F, 1.5F);
        put("stony_peaks", 0.3F, 1.5F);
        put("snowy_slopes", 0.3F, 1.0F);
        put("grove", 0.2F, 0.8F);
        put("meadow", 0.1F, 0.4F);

        // 林系
        put("birch_forest", 0.1F, 0.4F);
        put("old_growth_birch_forest", 0.2F, 0.6F);
        put("dark_forest", 0.1F, 0.5F);
        put("flower_forest", 0.1F, 0.4F);
        put("cherry_grove", 0.2F, 0.6F);
        put("old_growth_pine_taiga", 0.2F, 0.7F);
        put("old_growth_spruce_taiga", 0.2F, 0.7F);
        put("snowy_taiga", 0.1F, 0.5F);
        put("bamboo_jungle", 0.2F, 0.5F);
        put("sparse_jungle", 0.2F, 0.5F);

        // 草原/高原/恶地
        put("sunflower_plains", 0.1F, 0.3F);
        put("savanna", 0.1F, 0.3F);
        put("savanna_plateau", 0.3F, 0.8F);
        put("windswept_savanna", 0.1F, 0.4F);
        put("windswept_forest", 0.2F, 0.7F);
        put("windswept_gravelly_hills", 0.3F, 1.2F);
        put("badlands", 0.1F, 0.4F);
        put("wooded_badlands", 0.1F, 0.5F);
        put("eroded_badlands", 0.1F, 0.5F);

        // 海岸/冰雪
        put("snowy_beach", 0.0F, 0.1F);
        put("stony_shore", 0.0F, 0.1F);
        put("ice_spikes", 0.1F, 0.6F);

        // 海洋（deep 变体深海底）
        put("cold_ocean", -1.0F, 0.4F);
        put("lukewarm_ocean", -1.0F, 0.4F);
        put("warm_ocean", -1.0F, 0.4F);
        put("deep_ocean", -1.2F, 0.3F);
        put("deep_cold_ocean", -1.2F, 0.3F);
        put("deep_lukewarm_ocean", -1.2F, 0.3F);
        put("deep_frozen_ocean", -1.2F, 0.3F);

        // 湿地/洞穴
        put("mangrove_swamp", -0.2F, 0.1F);
        put("lush_caves", 0.1F, 0.3F);
        put("dripstone_caves", 0.1F, 0.3F);
        put("deep_dark", 0.1F, 0.3F);

        // 其他维度（不参与高度场，补默认）
        put("basalt_deltas", 0.1F, 0.3F);
        put("crimson_forest", 0.1F, 0.3F);
        put("soul_sand_valley", 0.1F, 0.3F);
        put("warped_forest", 0.1F, 0.3F);
        put("end_barrens", 0.1F, 0.3F);
        put("end_highlands", 0.1F, 0.3F);
        put("end_midlands", 0.1F, 0.3F);
        put("small_end_islands", 0.1F, 0.3F);
        put("the_void", 0.1F, 0.3F);

        // ---- B 区（死代码，注释保留；现代不会生成）----
        // desertHills (0.3, 0.8)
        // forestHills (0.3, 0.7)
        // taigaHills (0.3, 0.8)
        // jungleHills (1.8, 0.5) ★ 原版反值 bug，完全移植保留
        // extremeHillsEdge (0.2, 0.8)
        // iceMountains (0.3, 1.3)
        // mushroomIslandShore (-1.0, 0.1)
    }

    /** 查 biome 的 [minHeight, maxHeight]；未收录 → 默认 (0.1, 0.3)。 */
    public static float[] get(Holder<Biome> biome) {
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) {
            return DEFAULT;
        }
        float[] v = MAP.get(key.location().getPath());
        return v != null ? v : DEFAULT;
    }
}
