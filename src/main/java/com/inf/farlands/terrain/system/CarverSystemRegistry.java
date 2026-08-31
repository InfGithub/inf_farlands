package com.inf.farlands.terrain.system;

import com.inf.farlands.Config;
import com.inf.farlands.terrain.CarverSystem;

/**
 * 雕刻系统注册表：按维度懒建单例（仿 NoiseSystemRegistry/BiomeSystemRegistry/SurfaceSystemRegistry 模式）。
 *
 * 三维度各自独立系统：主世界 Config.overworldCarverSystem、下界 Config.theNetherCarverSystem、
 * 末地 Config.theEndCarverSystem。每个维度可选任意 CarverSystemType（create 完整 switch）；
 * 本次仅三维度 vanilla 值（旧版/自研 carver 留待未来扩展）。
 * 线程安全：volatile + 双重检查锁——CarverFiller 在 genPool 线程读，首次创建在首个
 * carver 任务时；vanilla 实现无状态，单例共享安全。
 */
public final class CarverSystemRegistry {

    private static volatile CarverSystem overworld;
    private static volatile CarverSystem nether;
    private static volatile CarverSystem end;

    private CarverSystemRegistry() {
    }

    /** 主世界雕刻系统。 */
    public static CarverSystem getOverworld() {
        CarverSystem s = overworld;
        if (s == null) {
            synchronized (CarverSystemRegistry.class) {
                s = overworld;
                if (s == null) {
                    s = create(Config.overworldCarverSystem);
                    overworld = s;
                }
            }
        }
        return s;
    }

    /** 下界雕刻系统。 */
    public static CarverSystem getTheNether() {
        CarverSystem s = nether;
        if (s == null) {
            synchronized (CarverSystemRegistry.class) {
                s = nether;
                if (s == null) {
                    s = create(Config.netherCarverSystem);
                    nether = s;
                }
            }
        }
        return s;
    }

    /** 末地雕刻系统。 */
    public static CarverSystem getTheEnd() {
        CarverSystem s = end;
        if (s == null) {
            synchronized (CarverSystemRegistry.class) {
                s = end;
                if (s == null) {
                    s = create(Config.endCarverSystem);
                    end = s;
                }
            }
        }
        return s;
    }

    /** 完整类型 switch：任一 CarverSystemType 均可选，任意维度通用。 */
    private static CarverSystem create(CarverSystemType type) {
        return switch (type) {
            case VANILLA_OVERWORLD -> new com.inf.farlands.terrain.system.overworld.carverVanilla.VanillaCarverSystem();
            case VANILLA_THE_NETHER -> new com.inf.farlands.terrain.system.theNether.carverVanilla.VanillaCarverSystem();
            case VANILLA_THE_END -> new com.inf.farlands.terrain.system.theEnd.carverVanilla.VanillaCarverSystem();
        };
    }
}
