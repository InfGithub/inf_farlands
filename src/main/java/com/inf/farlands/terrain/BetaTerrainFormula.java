package com.inf.farlands.terrain;

/**
 * Translates beta 1.7.3 {@code ChunkProviderGenerate.func_4061_a}
 * into a per-cell density function.
 * <p>
 * Positive density → stone, negative → air.
 */
public final class BetaTerrainFormula {

    private static final double CELL_H = 4.0;
    private static final double CELL_V = 8.0;
    /** Beta's effective surface center (cellsY/2 = 17/2 = 8.5 cells → y=68). */
    private static final double MID_Y = 68.0;
    private static final double MID_Y_CELL = MID_Y / CELL_V;
    // = 1/256.  Beta used noise/512 (=1/512) for its 128-block world;
    // modern MC has 384 blocks so the scale doubles proportionally.
    private static final double heightScale = 256.0 / 65536.0;
    // Beta clamps the top 4 cells (top 32 blocks) to force air.
    // For modern 384-block world, clamp from y=288 (cy=center of cell 36).
    private static final double CLAMP_START_CELL = 35.0;
    private static final double CLAMP_RANGE = 4.0;

    private BetaTerrainFormula() {}

    public static double density(int blockX, int blockY, int blockZ,
                                  BetaTerrainNoise noise,
                                  double temperature, double humidity) {
        double cx = blockX / CELL_H;
        double cy = blockY / CELL_V;
        double cz = blockZ / CELL_H;

        // Integer cell coords for channels 0/1 — matches beta's cell-grid sampling.
        // Channel 2 uses floating (low freq) for smooth lerp transitions.
        int cellX = Math.floorDiv(blockX, (int)CELL_H);
        int cellY = Math.floorDiv(blockY, (int)CELL_V);
        int cellZ = Math.floorDiv(blockZ, (int)CELL_H);

        double lower  = noise.sample(0, (double)cellX, (double)cellY, (double)cellZ);
        double upper  = noise.sample(1, (double)cellX, (double)cellY, (double)cellZ);
        double blend  = noise.sample(2, cx, cy, cz) / 20.0 + 0.5;

        double height = lerp(lower, upper, blend) * heightScale;

        // Beta's WorldChunkManager temperature/humidity were in [0, 1].
        // Modern MC biome temperature can be negative (frozen) or >1 (desert),
        // which would make var25 negative and tFactor negative, flipping the
        // bias direction and sending terrain to the sky.
        double climateProduct = Math.max(0.0, Math.min(1.0, humidity * temperature));
        double tFactor = 1.0 - climateProduct;
        tFactor = tFactor * tFactor;
        tFactor = tFactor * tFactor;
        tFactor = 1.0 - tFactor;
        double noiseFactor = noise.sample(3, cx, 0.0, cz) / 512.0 + 0.5;
        noiseFactor = clamp(noiseFactor, 0.0, 1.0);
        tFactor = tFactor * noiseFactor + 0.5;

        // Beta uses noise humidity (field_4181_h / 8000.0) for sea-level shift,
        // not biome humidity.  Range ~[-8, 8] before adjustment → ±12 blocks.
        double noiseHumidity = noise.sample(4, cx, 0.0, cz) / 8000.0;
        double hSea = adjustHumiditySeaLevel(noiseHumidity);
        // Beta: when humidity noise < 0 (dry), var27 = 0.0; ... var27 += 0.5
        if (noiseHumidity < 0.0) tFactor = 0.5;
        double yMidCell = MID_Y_CELL + hSea * 4.0;

        double bias = (cy - yMidCell) * 12.0;
        bias /= tFactor;
        if (cy < yMidCell) bias *= 4.0;

        double density = height - bias;

        // Beta: top cells ramp density to -10 to force air at world ceiling.
        if (cy > CLAMP_START_CELL) {
            double t = Math.min(1.0, (cy - CLAMP_START_CELL) / CLAMP_RANGE);
            density = density * (1.0 - t) + (-10.0) * t;
        }

        return density;
    }

    private static double adjustHumiditySeaLevel(double h) {
        if (h < 0.0) {
            h = -h * 0.3;
            h /= 2.0;
            if (h < -1.0) h = -1.0;
            h /= 1.4;
            h /= 2.0;
        } else {
            if (h > 1.0) h = 1.0;
            h /= 8.0;
        }
        return h;
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
