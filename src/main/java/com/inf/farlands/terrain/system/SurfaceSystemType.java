package com.inf.farlands.terrain.system;

/** 地表系统类型，由 Config 三维度配置（overworldSurfaceSystem/theNetherSurfaceSystem/theEndSurfaceSystem）指定。
 * 本次仅实现三维度 vanilla（1.21.1 地表规则，settings.surfaceRule()）；旧版地表
 * （beta 1.7.3 / 1.6.4 replaceBlocksForBiome 体系）留作未来扩展。 */
public enum SurfaceSystemType {
    /** vanilla 1.21.1 主世界地表规则（settings.surfaceRule()）。 */
    VANILLA_OVERWORLD,
    /** vanilla 1.21.1 下界地表规则（settings.surfaceRule()）。 */
    VANILLA_THE_NETHER,
    /** vanilla 1.21.1 末地地表规则（settings.surfaceRule()）。 */
    VANILLA_THE_END
}
