package com.inf.farlands.terrain.system.overworld.noiseVanilla1_16_5;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * 1.16.5 biome depth/scale 高度场映射表。
 *
 * 1.16.5 主世界高度场（fillNoiseColumn）用 biome.getDepth()/getScale() 做 5×5
 * 加权（depth→baseDensity、scale→ySlope）。现代 1.21.1 biome 无此字段，运行时
 * 查询到的是 1.21.1 biome → 查本表取 1.16.5 语义的 depth/scale。
 *
 * 分组（key = 1.21.1 biome path）：
 * - A 区：1.16.5 同名 biome，值照搬 1.16.5 原版（Biomes.java 注册参数 + VanillaBiomes 内部值）
 * - B 区：1.21.1 改名 biome → 1.16.5 近义 biome 值（windswept 系/old_growth 系/snowy_plains 等）
 * - C 区：1.16.5 真没有的 1.21.1 biome（meadow/grove/peaks 山系等），用户审定值
 *
 * 未收录 biome → 默认 (0.1, 0.3)（= 1.16.5 BiomeGenBase 默认 depth/scale，
 * 1.6.4 移植 DEFAULT 同值）。
 */
@SuppressWarnings("null")
public final class Biome1165HeightMap {

    /** [depth, scale]，1.16.5 默认构造值。 */
    public static final float[] DEFAULT = { 0.1F, 0.3F };

    private static final Map<String, float[]> MAP = new HashMap<>();

    private Biome1165HeightMap() {
    }

    private static void put(String key, float depth, float scale) {
        MAP.put(key, new float[] { depth, scale });
    }

    static {
        // ---- A 区：1.16.5 同名 biome，原版值照搬 ----

        // 海洋/河流
        put("ocean", -1.0F, 0.1F);
        put("deep_ocean", -1.8F, 0.1F);
        put("frozen_ocean", -1.0F, 0.1F);
        put("deep_frozen_ocean", -1.8F, 0.1F);
        put("cold_ocean", -1.0F, 0.1F);
        put("deep_cold_ocean", -1.8F, 0.1F);
        put("lukewarm_ocean", -1.0F, 0.1F);
        put("deep_lukewarm_ocean", -1.8F, 0.1F);
        put("warm_ocean", -1.0F, 0.1F);
        put("deep_warm_ocean", -1.8F, 0.1F);
        put("river", -0.5F, 0.0F);
        put("frozen_river", -0.5F, 0.0F);

        // 海岸
        put("beach", 0.0F, 0.025F);
        put("snowy_beach", 0.0F, 0.025F);

        // 沙漠/平原
        put("desert", 0.125F, 0.05F);
        put("plains", 0.125F, 0.05F);
        put("sunflower_plains", 0.125F, 0.05F);

        // 森林
        put("forest", 0.1F, 0.2F);
        put("flower_forest", 0.1F, 0.4F);
        put("birch_forest", 0.1F, 0.2F);
        put("dark_forest", 0.1F, 0.2F);
        put("taiga", 0.2F, 0.2F);
        put("snowy_taiga", 0.2F, 0.2F);
        put("swamp", -0.2F, 0.1F);

        // 丛林
        put("jungle", 0.1F, 0.2F);
        put("bamboo_jungle", 0.1F, 0.2F);

        // 草原/高原/恶地
        put("savanna", 0.125F, 0.05F);
        put("savanna_plateau", 1.5F, 0.025F);
        put("badlands", 0.1F, 0.2F);
        put("eroded_badlands", 0.1F, 0.2F);
        put("wooded_badlands", 1.5F, 0.025F);

        // 其他
        put("ice_spikes", 0.425F, 0.45F);
        put("mushroom_fields", 0.2F, 0.3F);

        // ---- B 区：1.21.1 改名 → 1.16.5 近义 biome 值 ----

        put("snowy_plains", 0.125F, 0.05F);         // ← snowy_tundra
        put("stony_shore", 0.1F, 0.8F);             // ← stone_shore
        put("windswept_hills", 1.0F, 0.5F);         // ← mountains
        put("windswept_forest", 1.0F, 0.5F);        // ← wooded_mountains
        put("windswept_gravelly_hills", 1.0F, 0.5F);// ← gravelly_mountains
        put("windswept_savanna", 0.3625F, 1.225F);  // ← shattered_savanna
        put("old_growth_pine_taiga", 0.2F, 0.2F);   // ← giant_tree_taiga
        put("old_growth_spruce_taiga", 0.2F, 0.2F); // ← giant_spruce_taiga
        put("old_growth_birch_forest", 0.2F, 0.4F); // ← tall_birch_forest

        // ---- C 区：1.16.5 没有的 1.21.1 biome（用户审定值）----

        // 山系（相对关系 jagged > frozen=stony > snowy_slopes > grove > meadow，
        // 参考 1.16.5 mountains=1.0/0.5 与 1.6.4 审定值比例）
        put("meadow", 0.3F, 0.2F);           // 山麓草甸：中等海拔、低起伏
        put("grove", 0.45F, 0.3F);           // 山麓林：高于 meadow、低于 mountains
        put("snowy_slopes", 0.6F, 0.4F);     // 高山雪坡：高于 grove、低于峰顶
        put("frozen_peaks", 1.0F, 0.5F);     // 冰峰：与 mountains 同级
        put("stony_peaks", 1.0F, 0.5F);      // 石峰：同上
        put("jagged_peaks", 1.2F, 0.6F);     // 尖峰：最高最陡，高于 mountains

        // 其他新群系
        put("cherry_grove", 0.1F, 0.2F);     // 低海拔樱花林：同 forest 级
        put("sparse_jungle", 0.1F, 0.2F);    // 稀疏丛林：同 jungle 级
        put("mangrove_swamp", -0.2F, 0.1F);  // 红树林：同 swamp 级
    }

    /** 查 biome 的 [depth, scale]；未收录 → 默认 (0.1, 0.3)。 */
    public static float[] get(Holder<Biome> biome) {
        ResourceKey<Biome> key = biome.unwrapKey().orElse(null);
        if (key == null) {
            return DEFAULT;
        }
        float[] v = MAP.get(key.location().getPath());
        return v != null ? v : DEFAULT;
    }
}
