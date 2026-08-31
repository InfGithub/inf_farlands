package com.inf.farlands.terrain.system;

import com.inf.farlands.Config;
import com.inf.farlands.terrain.BiomeSystem;
import com.inf.farlands.terrain.system.misc.biomeVoid.VoidBiomeSystem;

/**
 * 群系系统注册表：按维度懒建单例（仿 NoiseSystemRegistry 模式）。
 *
 * 三维度各自独立系统：主世界 Config.overworldBiomeSystem、下界 Config.theNetherBiomeSystem、
 * 末地 Config.theEndBiomeSystem。每个维度可选任意 BiomeSystemType（create 完整 switch）；
 * VOID 可配到任意维度（配 VOID 全空气地形语义正确）。
 * 线程安全：volatile + 双重检查锁——各维度 NoiseFiller.fill 在 genPool 线程读，
 * 首次创建在首个 fill 时；vanilla/void 实现无状态，单例共享安全。
 */
public final class BiomeSystemRegistry {

    private static volatile BiomeSystem overworld;
    private static volatile BiomeSystem nether;
    private static volatile BiomeSystem end;

    private BiomeSystemRegistry() {
    }

    /** 主世界群系系统。 */
    public static BiomeSystem getOverworld() {
        BiomeSystem s = overworld;
        if (s == null) {
            synchronized (BiomeSystemRegistry.class) {
                s = overworld;
                if (s == null) {
                    s = create(Config.overworldBiomeSystem);
                    overworld = s;
                }
            }
        }
        return s;
    }

    /** 下界群系系统。 */
    public static BiomeSystem getTheNether() {
        BiomeSystem s = nether;
        if (s == null) {
            synchronized (BiomeSystemRegistry.class) {
                s = nether;
                if (s == null) {
                    s = create(Config.netherBiomeSystem);
                    nether = s;
                }
            }
        }
        return s;
    }

    /** 末地群系系统。 */
    public static BiomeSystem getTheEnd() {
        BiomeSystem s = end;
        if (s == null) {
            synchronized (BiomeSystemRegistry.class) {
                s = end;
                if (s == null) {
                    s = create(Config.endBiomeSystem);
                    end = s;
                }
            }
        }
        return s;
    }

    /** 完整类型 switch：任一 BiomeSystemType 均可选，任意维度通用。 */
    private static BiomeSystem create(BiomeSystemType type) {
        return switch (type) {
            case VANILLA_OVERWORLD -> new com.inf.farlands.terrain.system.overworld.biomeVanilla.VanillaBiomeSystem();
            case VANILLA_THE_NETHER -> new com.inf.farlands.terrain.system.theNether.biomeVanilla.VanillaBiomeSystem();
            case VANILLA_THE_END -> new com.inf.farlands.terrain.system.theEnd.biomeVanilla.VanillaBiomeSystem();
            case VOID -> new VoidBiomeSystem();
        };
    }
}
