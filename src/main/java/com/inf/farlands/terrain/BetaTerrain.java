package com.inf.farlands.terrain;

import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Singleton holder for {@link BetaTerrainNoise}.
 * <p>
 * Call {@link #initialize(long)} once during server startup
 * with the world seed.
 */
public final class BetaTerrain {

    private static volatile BetaTerrainNoise instance;
    private static final ThreadLocal<ChunkAccess> CURRENT_CHUNK = new ThreadLocal<>();

    private BetaTerrain() {}

    public static void initialize(long seed) {
        if (instance == null) {
            instance = new BetaTerrainNoise(seed);
        }
    }

    public static BetaTerrainNoise get() {
        return instance;
    }

    public static void setCurrentChunk(ChunkAccess c) { CURRENT_CHUNK.set(c); }
    static ChunkAccess getCurrentChunk() { return CURRENT_CHUNK.get(); }
}
