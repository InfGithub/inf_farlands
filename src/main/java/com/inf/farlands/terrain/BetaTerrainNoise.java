package com.inf.farlands.terrain;

import net.minecraft.util.RandomSource;

/**
 * Replicates beta 1.7.3's five-channel octave noise used in
 * {@code ChunkProviderGenerate.func_4061_a}.
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
        this.octaves = new LegacyPerlinNoise[][] {
            createOctaves(random, O_LIMIT),   // 0: lower limit
            createOctaves(random, O_LIMIT),   // 1: upper limit
            createOctaves(random, O_BLEND),   // 2: blend weight
            createOctaves(random, O_TEMP),    // 3: temperature proxy
            createOctaves(random, O_LIMIT),   // 4: humidity proxy
        };
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
     * @param y       block Y (ignored for channels 3/4)
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
                fy = 0.0;
                fx = 1.121;
                fz = 1.121;
                break;
            case 4:
                fy = 0.0;
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
