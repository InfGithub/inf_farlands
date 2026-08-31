package com.inf.farlands.terrain.system;

import com.inf.farlands.Config;
import com.inf.farlands.terrain.BlockSystem;
import com.inf.farlands.terrain.NoiseSystem;
import com.inf.farlands.terrain.TerrainSystem;
import com.inf.farlands.terrain.system.misc.blockDiorite.DioriteBlockSystem;
import com.inf.farlands.terrain.system.misc.blockMandelbox.MandelboxBlockSystem;
import com.inf.farlands.terrain.system.misc.blockMandelbulb.MandelbulbBlockSystem;
import com.inf.farlands.terrain.system.misc.blockMaze.MazeBlockSystem;
import com.inf.farlands.terrain.system.misc.blockMenger.MengerSpongeBlockSystem;
import com.inf.farlands.terrain.system.misc.blockSierpinski.SierpinskiPyramidBlockSystem;
import com.inf.farlands.terrain.system.misc.blockWaterWorld.WaterWorldBlockSystem;
import com.inf.farlands.terrain.system.misc.noiseVoid.VoidNoiseSystem;
import com.inf.farlands.terrain.system.overworld.blockInfdev_20100226.Infdev20100226BlockSystem;
import com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3.Beta173NoiseSystem;
import com.inf.farlands.terrain.system.overworld.noiseVanilla.VanillaNoiseSystem;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 地形系统注册表：按维度懒建单例并转发生命周期回调。
 *
 * 三维度各自独立系统：主世界 Config.overworldTerrainSystem、下界
 * Config.theNetherTerrainSystem、末地 Config.theEndTerrainSystem。
 * 每个维度可选任意 TerrainSystemType（create 完整 switch）；VANILLA_THE_NETHER/
 * VANILLA_THE_END 是维度专用值（各自 vanilla 链），其余类型（主世界系统/杂项）任一维度
 * 均可选择——维度专用值配到非对应维度时仍按该类型自身构造（无兜底）。
 * 线程安全：volatile + 双重检查锁，仿 GenQueue.filler——onLevelLoad 主线程首建，
 * createFinalDensity/onChunkFillStart 在 genPool 线程读。provider 在首次 get 时定型，
 * 运行中改配置不重建，切配置需重启/重进世界。
 */
public final class NoiseSystemRegistry {

    private static volatile TerrainSystem overworld;
    private static volatile TerrainSystem nether;
    private static volatile TerrainSystem end;

    private NoiseSystemRegistry() {
    }

    /** 主世界系统。 */
    public static TerrainSystem getOverworld() {
        TerrainSystem s = overworld;
        if (s == null) {
            synchronized (NoiseSystemRegistry.class) {
                s = overworld;
                if (s == null) {
                    s = create(Config.overworldTerrainSystem);
                    overworld = s;
                }
            }
        }
        return s;
    }

    /** 下界系统。 */
    public static TerrainSystem getTheNether() {
        TerrainSystem s = nether;
        if (s == null) {
            synchronized (NoiseSystemRegistry.class) {
                s = nether;
                if (s == null) {
                    s = create(Config.netherTerrainSystem);
                    nether = s;
                }
            }
        }
        return s;
    }

    /** 末地系统。 */
    public static TerrainSystem getTheEnd() {
        TerrainSystem s = end;
        if (s == null) {
            synchronized (NoiseSystemRegistry.class) {
                s = end;
                if (s == null) {
                    s = create(Config.endTerrainSystem);
                    end = s;
                }
            }
        }
        return s;
    }

    /** 完整类型 switch：任一 TerrainSystemType 均可选，任意维度通用。 */
    private static TerrainSystem create(TerrainSystemType type) {
        return switch (type) {
            case VANILLA_OVERWORLD -> new VanillaNoiseSystem();
            case BETA_1_7_3_OVERWORLD -> new Beta173NoiseSystem();
            case BETA_1_7_3_SKY_DIMENSION -> new com.inf.farlands.terrain.system.skyDimension.noiseBeta1_7_3.SkyDimensionNoiseSystem();
            case VANILLA_1_6_4_OVERWORLD -> new com.inf.farlands.terrain.system.overworld.noiseVanilla1_6_4.Noise164System();
            case VANILLA_1_16_5_OVERWORLD -> new com.inf.farlands.terrain.system.overworld.noiseVanilla1_16_5.Noise1165System();
            case VOID -> new VoidNoiseSystem();
            case MENGER_SPONGE -> new MengerSpongeBlockSystem();
            case SIERPINSKI_PYRAMID -> new SierpinskiPyramidBlockSystem();
            case MAZE_3D -> new MazeBlockSystem();
            case MANDELBULB -> new MandelbulbBlockSystem();
            case INFDEV_20100226_OVERWORLD -> new Infdev20100226BlockSystem();
            case MANDELBOX -> new MandelboxBlockSystem();
            case DIORITE -> new DioriteBlockSystem();
            case WATER_WORLD -> new WaterWorldBlockSystem();
            case VANILLA_THE_NETHER -> new com.inf.farlands.terrain.system.theNether.noiseVanilla.VanillaNoiseSystem();
            case VANILLA_THE_END -> new com.inf.farlands.terrain.system.theEnd.noiseVanilla.VanillaNoiseSystem();
        };
    }

    public static void onLevelLoad(long seed) {
        getOverworld().onLevelLoad(seed);
    }

    public static void onChunkFillStart(ChunkAccess chunk) {
        getOverworld().onChunkFillStart(chunk);
    }
}
