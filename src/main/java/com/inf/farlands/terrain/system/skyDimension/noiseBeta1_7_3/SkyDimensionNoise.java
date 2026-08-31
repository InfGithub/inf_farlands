package com.inf.farlands.terrain.system.skyDimension.noiseBeta1_7_3;

import com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3.LegacyPerlinNoise;

import net.minecraft.util.RandomSource;

/**
 * 天域五通道八度噪声：b1.7.3 {@code ChunkProviderSky.func_28073_a} 的噪声通道移植。
 *
 * 通道（与 b1.7.3 L172-181 逐行对照）：
 *   0 — lower 地形下限（16 octaves，3D）——XZ 频率 = 684.412×2（var8 *= 2.0 后），Y = 684.412（var10 未 ×2）
 *   1 — upper 地形上限（16 octaves，3D）——同上
 *   2 — blend 权重（8 octaves，3D）——XZ = 684.412×2/80、Y = 684.412/160
 *   3 — 温度代理（10 octaves，2D xz）——1.121，y=10 固定切片（func_4109_a）
 *   4 — 湿度代理（16 octaves，2D xz）——200.0，y=10 固定切片
 *
 * 构造消费 7 组（16,16,8,4,4,10,16）：sand/stone 占位保随机流（仿 BetaTerrainNoise A2 教训），
 * 有效通道 = [0,1,2,5,6]。频率 ×2 只作用于 XZ（var8），Y 不变（var10）——与主世界 beta
 * 的 684.412 全同频不同，不能复用 BetaTerrainNoise。
 * 通道 3（temp）仅构造占位：b1.7.3 的湿润调制 var25/tempFactor 是死代码（var27 只喂 var36
 * 死链），密度计算不采样 temp——SkyDimensionDensityFunction 不再调用 sample(3,...)。
 */
public final class SkyDimensionNoise {

    private static final double FREQ_XZ = 684.412D;
    private static final int O_LIMIT = 16;
    private static final int O_BLEND = 8;
    private static final int O_TEMP = 10;

    private final LegacyPerlinNoise[][] octaves;

    public SkyDimensionNoise(long seed) {
        RandomSource random = RandomSource.create(seed);
        // 7 组消费：lower/upper/blend/sand占位/stone占位/temp/humidity（b1.7.3 构造顺序）
        LegacyPerlinNoise[][] all = new LegacyPerlinNoise[7][];
        all[0] = createOctaves(random, O_LIMIT); // 通道 0 lower
        all[1] = createOctaves(random, O_LIMIT); // 通道 1 upper
        all[2] = createOctaves(random, O_BLEND); // 通道 2 blend
        all[3] = createOctaves(random, 4);       // sand 占位
        all[4] = createOctaves(random, 4);       // stone 占位
        all[5] = createOctaves(random, O_TEMP);  // 通道 3 temp
        all[6] = createOctaves(random, O_LIMIT); // 通道 4 humidity
        this.octaves = new LegacyPerlinNoise[][] { all[0], all[1], all[2], all[5], all[6] };
    }

    private static LegacyPerlinNoise[] createOctaves(RandomSource random, int count) {
        LegacyPerlinNoise[] arr = new LegacyPerlinNoise[count];
        for (int i = 0; i < count; i++) {
            arr[i] = new LegacyPerlinNoise(random);
        }
        return arr;
    }

    /**
     * @param channel 0=lower, 1=upper, 2=blend, 3=temp, 4=humidity
     * @param x/y/z   cell 坐标（天域 cell：XZ 8 格、Y 4 格）；通道 3/4 的 y 恒传 10.0
     */
    public double sample(int channel, double x, double y, double z) {
        double fx, fy, fz;
        switch (channel) {
            case 2: // blend：b1.7.3 var8*2/80、var10/160、var8*2/80
                fy = FREQ_XZ / 160.0;
                fx = FREQ_XZ * 2.0 / 80.0;
                fz = FREQ_XZ * 2.0 / 80.0;
                break;
            case 3: // temp：func_4109_a(1.121, 1.0, 1.121)，y 切片 10
                fy = 1.0;
                fx = 1.121;
                fz = 1.121;
                break;
            case 4: // humidity：func_4109_a(200.0, 1.0, 200.0)
                fy = 1.0;
                fx = 200.0;
                fz = 200.0;
                break;
            default: // lower/upper：var8 *= 2.0 后 (var8, var10, var8)——XZ ×2、Y 不变
                fy = FREQ_XZ;
                fx = FREQ_XZ * 2.0;
                fz = FREQ_XZ * 2.0;
                break;
        }
        double sum = 0.0;
        double freq = 1.0;
        for (LegacyPerlinNoise n : this.octaves[channel]) {
            sum += n.generateNoise(x * fx * freq, y * fy * freq, z * fz * freq) / freq;
            freq /= 2.0;
        }
        return sum;
    }
}
