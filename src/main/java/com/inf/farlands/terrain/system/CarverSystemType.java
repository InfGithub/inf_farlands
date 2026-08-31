package com.inf.farlands.terrain.system;

/** 雕刻系统类型，由 Config 三维度配置（overworldCarverSystem/theNetherCarverSystem/theEndCarverSystem）指定。
 * 本次仅实现三维度 vanilla（1.21.1 biome json 注册的 carver 列表）；旧版/自研 carver 留作未来扩展。 */
public enum CarverSystemType {
    /** vanilla 1.21.1 主世界雕刻：biome json 的 CARVERS（CAVE/CAVE_EXTRA_UNDERGROUND/CANYON）。 */
    VANILLA_OVERWORLD,
    /** vanilla 1.21.1 下界雕刻：biome json 的 NETHER_CAVE。 */
    VANILLA_THE_NETHER,
    /** vanilla 1.21.1 末地雕刻：末地 biome json 无 carver（空）。 */
    VANILLA_THE_END
}
