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
    private static final double MID_Y = 64.0;
    private static final double MID_Y_CELL = MID_Y / CELL_V;
    private static final double heightScale = 256.0 / 65536.0;

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

        double tFactor = 1.0 - humidity * temperature;
        tFactor = tFactor * tFactor;
        tFactor = tFactor * tFactor;
        tFactor = 1.0 - tFactor;
        double noiseFactor = noise.sample(3, cx, 0.0, cz) / 512.0 + 0.5;
        noiseFactor = clamp(noiseFactor, 0.0, 1.0);
        tFactor = tFactor * noiseFactor + 0.5;

        double hSea = humidity;
        hSea = adjustHumiditySeaLevel(hSea);
        double yMidCell = MID_Y_CELL + hSea * 0.5;

        double bias = (cy - yMidCell) * 12.0;
        bias /= tFactor;
        if (cy < yMidCell) bias *= 4.0;

        return height - bias;
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
