package com.inf.farlands.terrain;

public final class BetaTerrainFormula {

    private static final double CELL_H = 4.0;
    private static final double CELL_V = 8.0;
    private static final double MID_Y = 68.0;
    private static final double MID_Y_CELL = MID_Y / CELL_V;
    // = 1/256。Beta 用 noise/512（=1/512）适配其 128 格世界；现代 MC 有 384 格，
    // 比例相应加倍。
    private static final double heightScale = 256.0 / 65536.0;
    // Beta 将顶部 4 个 cell（顶部 32 格）钳制为空气。现代 384 格世界从 y=288 开始钳制。
    private static final double CLAMP_START_CELL = 35.0;
    private static final double CLAMP_RANGE = 4.0;

    private BetaTerrainFormula() {
    }

    public static double density(int blockX, int blockY, int blockZ,
            BetaTerrainNoise noise,
            double temperature, double humidity) {
        double cx = blockX / CELL_H;
        double cy = blockY / CELL_V;
        double cz = blockZ / CELL_H;

        // 通道 0/1 用整数 cell 坐标，匹配 beta 的 cell 网格采样。
        // 通道 2 用浮点坐标（低频），保证 lerp 过渡平滑。
        int cellX = Math.floorDiv(blockX, (int) CELL_H);
        int cellY = Math.floorDiv(blockY, (int) CELL_V);
        int cellZ = Math.floorDiv(blockZ, (int) CELL_H);

        double lower = noise.sample(0, (double) cellX, (double) cellY, (double) cellZ);
        double upper = noise.sample(1, (double) cellX, (double) cellY, (double) cellZ);
        double blend = noise.sample(2, cx, cy, cz) / 20.0 + 0.5;

        double height = lerp(lower, upper, blend) * heightScale;

        // Beta 的 WorldChunkManager 温湿度在 [0, 1]。现代 MC 群系温度可能为负
        // （冻原）或 >1（沙漠），会使 var25 与 tFactor 为负，翻转 bias 方向，
        // 把地形送到天上。
        double climateProduct = Math.max(0.0, Math.min(1.0, humidity * temperature));
        double tFactor = 1.0 - climateProduct;
        tFactor = tFactor * tFactor;
        tFactor = tFactor * tFactor;
        tFactor = 1.0 - tFactor;
        double noiseFactor = noise.sample(3, cx, 0.0, cz) / 512.0 + 0.5;
        noiseFactor = clamp(noiseFactor, 0.0, 1.0);
        tFactor = tFactor * noiseFactor + 0.5;

        // Beta 用噪声湿度（field_4181_h / 8000.0）调海平面，非群系湿度。
        // 调整前范围约 [-8, 8] → 调整后 ±12 格。
        double noiseHumidity = noise.sample(4, cx, 0.0, cz) / 8000.0;
        double hSea = adjustHumiditySeaLevel(noiseHumidity);
        // Beta：湿度噪声 < 0（干燥）时 var27 = 0.0；... var27 += 0.5
        if (noiseHumidity < 0.0)
            tFactor = 0.5;
        double yMidCell = MID_Y_CELL + hSea * 4.0;

        double bias = (cy - yMidCell) * 12.0;
        bias /= tFactor;
        if (cy < yMidCell)
            bias *= 4.0;

        double density = height - bias;

        // Beta：顶部 cell 将 density 渐变到 -10，在世界顶强制为空气。
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
            if (h < -1.0)
                h = -1.0;
            h /= 1.4;
            h /= 2.0;
        } else {
            if (h > 1.0)
                h = 1.0;
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
