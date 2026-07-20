package com.inf.farlands;

/**
 * Global coordinate bounds. Chunk coordinates beyond MAX_CHUNK cause
 * {@code getMinBlockX() = chunkX << 4} to overflow int, making the
 * world meaningless. There is no way around this — it's a hard
 * mathematical limit of the Minecraft coordinate system.
 */
public final class FarLandsConstants {
    private FarLandsConstants() {}

    /** Last chunk coordinate where getMinBlockX() does not overflow int. */
    public static final int MAX_CHUNK = 134_217_726;

    /** Last block coordinate that fits safely in int (MAX_CHUNK * 16). */
    public static final int MAX_BLOCK = MAX_CHUNK * 16;
}
