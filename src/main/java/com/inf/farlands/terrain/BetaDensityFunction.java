package com.inf.farlands.terrain;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DensityFunction;

public class BetaDensityFunction implements DensityFunction.SimpleFunction {

    private static final double MIN = -2000.0;
    private static final double MAX = 2000.0;

    private int cachedCx = Integer.MIN_VALUE;
    private int cachedCy;
    private int cachedCz;
    private final double[] cachedCorners = new double[8];
    private double cachedTemp, cachedHum;

    @Override
    public double compute(FunctionContext context) {
        BetaTerrainNoise noise = BetaTerrain.get();
        if (noise == null) return 0.0;

        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();

        int cx0 = Math.floorDiv(x, 4);
        int cy0 = Math.floorDiv(y, 8);
        int cz0 = Math.floorDiv(z, 4);

        if (cx0 != cachedCx || cy0 != cachedCy || cz0 != cachedCz) {
            cachedCx = cx0;
            cachedCy = cy0;
            cachedCz = cz0;

            ChunkAccess chunk = BetaTerrain.getCurrentChunk();
            if (chunk != null) {
                Holder<Biome> holder = chunk.getNoiseBiome(
                    QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
                if (holder != null) {
                    Biome biome = holder.value();
                    Biome.ClimateSettings cs = biome.getModifiedClimateSettings();
                    cachedTemp = cs.temperature();
                    cachedHum = cs.downfall();
                }
            }

            int x0 = cx0 * 4;
            int y0 = cy0 * 8;
            int z0 = cz0 * 4;

            cachedCorners[0] = BetaTerrainFormula.density(x0,      y0,      z0,      noise, cachedTemp, cachedHum);
            cachedCorners[1] = BetaTerrainFormula.density(x0 + 4,  y0,      z0,      noise, cachedTemp, cachedHum);
            cachedCorners[2] = BetaTerrainFormula.density(x0,      y0 + 8,  z0,      noise, cachedTemp, cachedHum);
            cachedCorners[3] = BetaTerrainFormula.density(x0 + 4,  y0 + 8,  z0,      noise, cachedTemp, cachedHum);
            cachedCorners[4] = BetaTerrainFormula.density(x0,      y0,      z0 + 4,  noise, cachedTemp, cachedHum);
            cachedCorners[5] = BetaTerrainFormula.density(x0 + 4,  y0,      z0 + 4,  noise, cachedTemp, cachedHum);
            cachedCorners[6] = BetaTerrainFormula.density(x0,      y0 + 8,  z0 + 4,  noise, cachedTemp, cachedHum);
            cachedCorners[7] = BetaTerrainFormula.density(x0 + 4,  y0 + 8,  z0 + 4,  noise, cachedTemp, cachedHum);
        }

        double fx = (double)(x - cx0 * 4) / 4.0;
        double fy = (double)(y - cy0 * 8) / 8.0;
        double fz = (double)(z - cz0 * 4) / 4.0;

        return lerp3(cachedCorners[0], cachedCorners[1], cachedCorners[2], cachedCorners[3],
                     cachedCorners[4], cachedCorners[5], cachedCorners[6], cachedCorners[7],
                     fx, fy, fz);
    }

    private static double lerp3(double c000, double c100, double c010, double c110,
                                 double c001, double c101, double c011, double c111,
                                 double fx, double fy, double fz) {
        double c00 = c000 + fx * (c100 - c000);
        double c10 = c010 + fx * (c110 - c010);
        double c01 = c001 + fx * (c101 - c001);
        double c11 = c011 + fx * (c111 - c011);
        double c0 = c00 + fy * (c10 - c00);
        double c1 = c01 + fy * (c11 - c01);
        return c0 + fz * (c1 - c0);
    }

    @Override
    public double minValue() { return MIN; }

    @Override
    public double maxValue() { return MAX; }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return null;
    }
}
