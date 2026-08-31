package com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3;

import net.minecraft.world.level.chunk.ChunkAccess;

public final class BetaTerrain {

    private static volatile BetaTerrainNoise instance;
    private static final ThreadLocal<ChunkAccess> CURRENT_CHUNK = new ThreadLocal<>();

    private BetaTerrain() {
    }

    public static void initialize(long seed) {
        if (instance == null) {
            instance = new BetaTerrainNoise(seed);
        }
    }

    public static BetaTerrainNoise get() {
        return instance;
    }

    public static void setCurrentChunk(ChunkAccess c) {
        CURRENT_CHUNK.set(c);
    }

    static ChunkAccess getCurrentChunk() {
        return CURRENT_CHUNK.get();
    }
}
