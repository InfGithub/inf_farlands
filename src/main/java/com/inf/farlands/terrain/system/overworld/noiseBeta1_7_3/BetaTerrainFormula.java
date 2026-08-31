package com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3;

import com.inf.farlands.Config;

public final class BetaTerrainFormula {

    private static final double CELL_H = 4.0;
    private static final double CELL_V = 8.0;
    /** beta func_4061_a 的 Y cell 网格数，var6 = 17，覆盖 128 格世界。 */
    private static final double Y_CELLS = 17.0;

    // C9：恢复 beta 原样 1/512；此前 1/256 使 height 幅度 2 倍，地表范围从 ±128 扩到
    // ±256 block，山可顶到 384 格世界顶。
    private static final double heightScale = 1.0 / 512.0;

    // C11（恢复，note #81 曾删除）：beta 原版顶部渐变（func_4061_a L292-295）——
    // var33 > var6-4 → t=(var33-(var6-4))/3 → density·(1-t) + (-10)·t。beta 原版
    // var33 ∈ [0,17)（128 高世界）→ t 天然 ≤1；无限 Y 下 cy 无上限 → t>1 时 (1-t)
    // 为负、density 大负值翻正 → 实心方块（#81 光照黑根因）。恢复时 clamp t ≤1：
    // 渐消带 = 世界顶 3 cell（blockY 104~128），之上纯公式自然空气（与 1.6.4 移植一致）。
    private static final double TOP_START_CELL = Y_CELLS - 4.0; // 13

    private BetaTerrainFormula() {
    }

    public static double density(int blockX, int blockY, int blockZ,
            BetaTerrainNoise noise,
            double temperature, double humidity,
            double sample3, double sample4) {
        double cx = blockX / CELL_H;
        double cy = blockY / CELL_V;
        double cz = blockZ / CELL_H;

        // 通道 0/1 用整数 cell 坐标，匹配 beta 的 cell 网格采样。
        // 通道 2 用浮点坐标做低频采样，保证 lerp 过渡平滑。
        int cellX = Math.floorDiv(blockX, (int) CELL_H);
        int cellY = Math.floorDiv(blockY, (int) CELL_V);
        int cellZ = Math.floorDiv(blockZ, (int) CELL_H);

        double lower = noise.sample(0, (double) cellX, (double) cellY, (double) cellZ);
        double upper = noise.sample(1, (double) cellX, (double) cellY, (double) cellZ);
        // C10：blend 对齐 beta 的 clamp：var42<0 → 选 e；>1 → 选 f——clamp 后 lerp 等价
        double blend = noise.sample(2, cx, cy, cz) / 20.0 + 0.5;
        blend = blend < 0.0 ? 0.0 : (blend > 1.0 ? 1.0 : blend);

        double height = lerp(lower, upper, blend) * heightScale;

        // 湿润调制 beta var25：1-(1-hum·temp)⁴。hum·temp 用 biome climate 值，
        // clamp [0,1] 防御，因为 modern 温度可负。
        double climateProduct = Math.max(0.0, Math.min(1.0, humidity * temperature));
        double var25 = 1.0 - climateProduct;
        var25 = var25 * var25;
        var25 = var25 * var25;
        var25 = 1.0 - var25;

        // C3：陡峭度分母 var27，按 beta 顺序：先乘 var25 再 clamp 上界，+0.5 在湿度分支后，
        // 最终 [0.5, 1.5]；原实现先 clamp g 再乘，上界 1.0，湿润山地比 beta 陡。
        double var27 = (sample3 / 512.0 + 0.5) * var25;
        if (var27 > 1.0) {
            var27 = 1.0;
        }

        // C4/C5/C6：湿度→海平面偏移，beta var29 完整变换，逐行对应 func_4061_a L241-262，
        // 含 ×3-2；干燥分支返回负值，中心下移即变矮；判定范围 = 变换后 <0，即 v<2/3。
        double v = sample4 / 8000.0;
        if (v < 0.0) {
            v = -v * 0.3;
        }
        v = v * 3.0 - 2.0;
        double hSea;
        if (v < 0.0) {
            v /= 2.0;
            if (v < -1.0) {
                v = -1.0;
            }
            v /= 1.4;
            v /= 2.0;
            hSea = v;      // 负值：中心下移，beta 原样；原实现此处返回正值，方向反
            var27 = 0.0;   // 干燥分支：var27 置 0 → +0.5 后 = 0.5，即最陡
        } else {
            if (v > 1.0) {
                v = 1.0;
            }
            hSea = v / 8.0;
        }
        if (var27 < 0.0) {
            var27 = 0.0;
        }
        var27 += 0.5;

        // beta L269-270：var29 *= var6/16，即 17/16；var31 = var6/2 + var29*4
        double yMidCell = Y_CELLS / 2.0 + hSea * (Y_CELLS / 16.0) * 4.0;

        double bias = (cy - yMidCell) * 12.0;
        bias /= var27;
        if (cy < yMidCell) {
            bias *= 4.0;
        }

        double density = height - bias;

        // C11：顶部渐变（beta 原版 L292-295 移植，t clamp ≤1 防无限 Y 翻转）。
        // Config.topFadeEnabled 开关：默认关（note #81 曾删除本项，恢复后与无限 Y
        // 翻转风险隔离）；开启时渐消带 blockY ∈ (104, 128] → t ∈ (0, 1]。
        if (Config.topFadeEnabled && cy > TOP_START_CELL) {
            double t = (cy - TOP_START_CELL) / 3.0;
            if (t > 1.0) {
                t = 1.0;
            }
            density = density * (1.0 - t) + (-10.0) * t;
        }

        return density;
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
}
