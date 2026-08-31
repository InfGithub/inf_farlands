package com.inf.farlands.terrain.system;

/** 地形系统类型，由 Config 三维度配置（overworldTerrainSystem/netherTerrainSystem/endTerrainSystem）指定。两类互斥范式：
 * 密度链噪声（VANILLA_OVERWORLD/BETA_1_7_3_OVERWORLD/VOID）与逐块几何（MENGER_SPONGE/SIERPINSKI_PYRAMID/MAZE_3D/MANDELBULB）。 */
public enum TerrainSystemType {
    /** 纯空气：什么都不生成，用于 VOID 虚空世界。 */
    VOID,

    /** vanilla 1.21.1 噪声链：由 worldgen settings 派生，无 surface rules，即管线裸地形。 */
    VANILLA_OVERWORLD,
    /** 下界 vanilla 1.21.1 链：BlendedNoise + slideNetherLike，无 surface（管线裸地形）。 */
    VANILLA_THE_NETHER,
    /** 末地 vanilla 1.21.1 链：endIslands + slopedCheese + slideEnd。 */
    VANILLA_THE_END,

    /** Infdev 20100226：第一个无限世界版本（2010-02-26）的原样地形——4×4 周期微丘草面（64±4）+ y<0 基岩，无洞穴/水/树。 */
    INFDEV_20100226_OVERWORLD,
    /** beta 1.7.3 五通道八度噪声：LegacyPerlinNoise 生成边境之地地形。 */
    BETA_1_7_3_OVERWORLD,
    /** 天域（Sky Dimension）：b1.7.3 ChunkProviderSky 密度公式移植——浮空岛 + 边境之地。
     * 无高度衰减 + 固定 −8 阈值 → 3D 噪声正区域悬浮成岛；XZ 8 格 / Y 4 格"竖过来"网格。 */
    BETA_1_7_3_SKY_DIMENSION,
    /** 正式版 1.6.4 地形：4 组 octaves + biome 高度场（minH/maxH 加权）+ 128/63 语义。 */
    VANILLA_1_6_4_OVERWORLD,
    /** 1.7.0~1.16.5 旧生成器谱系巅峰：3D 密度场（fillNoiseColumn）+ depth/scale 高度场 + slide。 */
    VANILLA_1_16_5_OVERWORLD,

    /** 谢尔宾斯基海绵：全局三进制分形，黑曜石实心，任意坐标直接判定。 */
    MENGER_SPONGE,
    /** 谢尔宾斯基棱锥：巨型黑曜石镂空棱锥分形，底 y=-2^30 → 顶 y=2^30-1，居中。 */
    SIERPINSKI_PYRAMID,
    /** Mandelbulb（n=8）：整个 ±2.14B 世界 = 一个巨型分形花朵，纯多项式判定、与种子无关。 */
    MANDELBULB,
    /** Mandelbox：整个世界一个分形（box fold + sphere fold），scale=2^30，纯折叠/缩放无超越函数。 */
    MANDELBOX,

    /** 3D 确定性单元迷宫：单一连通、壳上仅两口（入口 x=y=z=-2.14B / 出口 x=y=z=2.14B）。 */
    MAZE_3D,

    /** 闪长岩填充：整个世界所有坐标全部填满闪长岩（与 VOID 纯空气相反），普通测试用基底。 */
    DIORITE,
    /** WaterWorld：整个世界所有坐标全部是水，无一例外。 */
    WATER_WORLD
}
