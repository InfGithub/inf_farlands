package com.inf.farlands.terrain.system.overworld.noiseBeta1_7_3;

import net.minecraft.util.RandomSource;

/**
 * 复刻 beta 1.7.3 用于 {@code ChunkProviderGenerate.func_4061_a} 的五通道八度噪声。
 *
 * <pre>{@code
 *   0 — field_4184_e   lower terrain limit     (16 octaves, 3D)
 *   1 — field_4183_f   upper terrain limit     (16 octaves, 3D)
 *   2 — field_4185_d   blend weight            ( 8 octaves, 3D)
 *   3 — field_922_a    temperature proxy       (10 octaves, 2D xz)
 *   4 — field_921_b    humidity proxy          (16 octaves, 2D xz)
 * }</pre>
 */
public final class BetaTerrainNoise {

    private static final double FREQ_XZ = 684.412;
    private static final int O_LIMIT = 16;
    private static final int O_BLEND = 8;
    private static final int O_TEMP = 10;

    private final LegacyPerlinNoise[][] octaves;

    public BetaTerrainNoise(long seed) {
        RandomSource random = RandomSource.create(seed);
        // A2：对齐 beta 构造顺序，见 ChunkProviderGenerate L33-39——16,16,8,4(sand),4(stone),10(g),16(h)。
        // sand/stone 两组未实现，无表面层，但必须占位消费随机流，否则通道 3/4 的
        // Perlin 实例错源：beta 第 6/7 组 vs 原实现第 4/5 组。
        LegacyPerlinNoise[][] all = new LegacyPerlinNoise[7][];
        all[0] = createOctaves(random, O_LIMIT); // beta field_912_k → 通道 0：下界限制
        all[1] = createOctaves(random, O_LIMIT); // beta field_911_l → 通道 1：上界限制
        all[2] = createOctaves(random, O_BLEND); // beta field_910_m → 通道 2：混合权重
        all[3] = createOctaves(random, 4);       // beta field_909_n，sand 占位
        all[4] = createOctaves(random, 4);       // beta field_908_o，stone 占位
        all[5] = createOctaves(random, O_TEMP);  // beta field_922_a → 通道 3：温度代理
        all[6] = createOctaves(random, O_LIMIT); // beta field_921_b → 通道 4：湿度代理
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
     * @param x       block X
     * @param y       block Y；通道 3/4 为 beta 固定切片 y=10.0，即 func_4109_a 的 y 起点，
     *                fy=1.0 使各 octave 的 y 切片 = 10·2⁻ᵒ，与 beta 一致
     * @param z       block Z
     */
    public double sample(int channel, double x, double y, double z) {
        double fx, fy, fz;
        switch (channel) {
            case 2:
                fy = FREQ_XZ / 160.0;
                fx = FREQ_XZ / 80.0;
                fz = FREQ_XZ / 80.0;
                break;
            case 3:
                // B1：beta generateNoiseOctaves(..., 10.0, 5,1,5, 1.121, 1.0, 1.121)
                fy = 1.0;
                fx = 1.121;
                fz = 1.121;
                break;
            case 4:
                fy = 1.0;
                fx = 200.0;
                fz = 200.0;
                break;
            default:
                fy = FREQ_XZ;
                fx = FREQ_XZ;
                fz = FREQ_XZ;
                break;
        }

        double sum = 0.0;
        double freq = 1.0;
        for (LegacyPerlinNoise noise : this.octaves[channel]) {
            sum += noise.generateNoise(x * fx * freq, y * fy * freq, z * fz * freq) / freq;
            freq /= 2.0;
        }
        return sum;
    }
}
