package com.inf.farlands.terrain.system;

import com.inf.farlands.Config;
import com.inf.farlands.terrain.SurfaceSystem;

/**
 * 地表系统注册表：按维度懒建单例（仿 NoiseSystemRegistry/BiomeSystemRegistry 模式）。
 *
 * 三维度各自独立系统：主世界 Config.overworldSurfaceSystem、下界 Config.theNetherSurfaceSystem、
 * 末地 Config.theEndSurfaceSystem。每个维度可选任意 SurfaceSystemType（create 完整 switch）；
 * 本次仅三维度 vanilla 值（旧版地表留待未来扩展）。
 * 线程安全：volatile + 双重检查锁——SurfaceFiller 在 genPool 线程读，首次创建在首个
 * surface 任务时；vanilla 实现无状态，单例共享安全。
 */
public final class SurfaceSystemRegistry {

    private static volatile SurfaceSystem overworld;
    private static volatile SurfaceSystem nether;
    private static volatile SurfaceSystem end;

    private SurfaceSystemRegistry() {
    }

    /** 主世界地表系统。 */
    public static SurfaceSystem getOverworld() {
        SurfaceSystem s = overworld;
        if (s == null) {
            synchronized (SurfaceSystemRegistry.class) {
                s = overworld;
                if (s == null) {
                    s = create(Config.overworldSurfaceSystem);
                    overworld = s;
                }
            }
        }
        return s;
    }

    /** 下界地表系统。 */
    public static SurfaceSystem getTheNether() {
        SurfaceSystem s = nether;
        if (s == null) {
            synchronized (SurfaceSystemRegistry.class) {
                s = nether;
                if (s == null) {
                    s = create(Config.netherSurfaceSystem);
                    nether = s;
                }
            }
        }
        return s;
    }

    /** 末地地表系统。 */
    public static SurfaceSystem getTheEnd() {
        SurfaceSystem s = end;
        if (s == null) {
            synchronized (SurfaceSystemRegistry.class) {
                s = end;
                if (s == null) {
                    s = create(Config.endSurfaceSystem);
                    end = s;
                }
            }
        }
        return s;
    }

    /** 完整类型 switch：任一 SurfaceSystemType 均可选，任意维度通用。 */
    private static SurfaceSystem create(SurfaceSystemType type) {
        return switch (type) {
            case VANILLA_OVERWORLD -> new com.inf.farlands.terrain.system.overworld.surfaceVanilla.VanillaSurfaceSystem();
            case VANILLA_THE_NETHER -> new com.inf.farlands.terrain.system.theNether.surfaceVanilla.VanillaSurfaceSystem();
            case VANILLA_THE_END -> new com.inf.farlands.terrain.system.theEnd.surfaceVanilla.VanillaSurfaceSystem();
        };
    }
}
