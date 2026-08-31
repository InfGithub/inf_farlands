package com.inf.farlands.terrain.system;

/** 群系系统类型，由 Config 三维度配置（overworldBiomeSystem/theNetherBiomeSystem/theEndBiomeSystem）指定。 */
public enum BiomeSystemType {
    /** vanilla 1.21.1 主世界群系：MultiNoiseBiomeSource 6 通道参数表（原版布局）。 */
    VANILLA_OVERWORLD,
    /** vanilla 1.21.1 下界群系：MultiNoiseBiomeSource nether 参数表（温湿度二维分布）。 */
    VANILLA_THE_NETHER,
    /** vanilla 1.21.1 末地群系：TheEndBiomeSource（endIslands erosion 分带 + 中心圆区）。 */
    VANILLA_THE_END,
    /** 虚空群系：整个世界的 biome 恒为 the_void（配 VOID 全空气地形）。 */
    VOID
}
