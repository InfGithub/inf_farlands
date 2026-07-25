package com.inf.farlands.mixin.threeInt;

import org.apache.commons.lang3.mutable.MutableDouble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.block.Blocks;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class Aquifer$NoiseBasedAquiferMixin {
    // protected int getIndex(int gridX, int gridY, int gridZ) {
    // int i = gridX - this.minGridX;
    // int j = gridY - this.minGridY;
    // int k = gridZ - this.minGridZ;
    // return (j * this.gridSizeZ + k) * this.gridSizeX + i;
    // }
    @Shadow
    private int minGridX, minGridY, minGridZ;
    @Shadow
    private int gridSizeX, gridSizeZ;
    @Shadow
    private Aquifer.FluidStatus[] aquiferCache;

    @Overwrite
    protected int getIndex(int gridX, int gridY, int gridZ) {
        long i = (long) gridX - this.minGridX;
        long j = (long) gridY - this.minGridY;
        long k = (long) gridZ - this.minGridZ;
        long index = (j * this.gridSizeZ + k) * this.gridSizeX + i;
        if (index < 0 || index >= this.aquiferCache.length)
            return 0;
        return (int) index;
    }

    // private Aquifer.FluidStatus getAquiferStatus(long packedPos) {
    // int i = BlockPos.getX(packedPos);
    // int j = BlockPos.getY(packedPos);
    // int k = BlockPos.getZ(packedPos);
    // int l = this.gridX(i);
    // int i1 = this.gridY(j);
    // int j1 = this.gridZ(k);
    // int k1 = this.getIndex(l, i1, j1);
    // Aquifer.FluidStatus aquifer$fluidstatus = this.aquiferCache[k1];
    // if (aquifer$fluidstatus != null) {
    // return aquifer$fluidstatus;
    // } else {
    // Aquifer.FluidStatus aquifer$fluidstatus1 = this.computeFluid(i, j, k);
    // this.aquiferCache[k1] = aquifer$fluidstatus1;
    // return aquifer$fluidstatus1;
    // }
    // }
    @Shadow
    protected abstract int gridX(int x);

    @Shadow
    protected abstract int gridY(int y);

    @Shadow
    protected abstract int gridZ(int z);

    @Shadow
    protected abstract Aquifer.FluidStatus computeFluid(int x, int y, int z);

    @Overwrite
    private Aquifer.FluidStatus getAquiferStatus(long packedPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        int i = gridX(bp.x);
        int j = gridY(bp.y);
        int k = gridZ(bp.z);
        int k1 = getIndex(i, j, k);
        Aquifer.FluidStatus s = aquiferCache[k1];
        if (s == null) {
            Aquifer.FluidStatus c = computeFluid(bp.x, bp.y, bp.z);
            aquiferCache[k1] = c;
            return c;
        }
        return s;
    }

    @Shadow
    private boolean shouldScheduleFluidUpdate;
    @Shadow
    private Aquifer.FluidPicker globalFluidPicker;
    @Shadow
    private long[] aquiferLocationCache;
    @Shadow
    private PositionalRandomFactory positionalRandomFactory;
    @Shadow
    private static double FLOWING_UPDATE_SIMULARITY;

    @Shadow
    protected static double similarity(int a, int b) {
        return 0.0;
    }

    @Shadow
    protected abstract double calculatePressure(DensityFunction.FunctionContext ctx, MutableDouble s,
            Aquifer.FluidStatus a, Aquifer.FluidStatus b);

    @SuppressWarnings("null")
    @Overwrite
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
        int i = context.blockX();
        int j = context.blockY();
        int k = context.blockZ();

        if (substance > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus fluid = this.globalFluidPicker.computeFluid(i, j, k);
        if (fluid.at(j).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = false;
            return Blocks.LAVA.defaultBlockState();
        }

        int fd1 = Math.floorDiv(i - 5, 16);
        int fd2 = Math.floorDiv(j + 1, 12);
        int fd3 = Math.floorDiv(k - 5, 16);

        int[] nearestDist = new int[] {
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE
        };
        long[] nearestPos = new long[3];
        int[][] nearestCoord = new int[3][3];

        for (int i3 = 0; i3 <= 1; i3++) {
            for (int j3 = -1; j3 <= 1; j3++) {
                for (int k3 = 0; k3 <= 1; k3++) {
                    int gridX = fd1 + i3;
                    int gridY = fd2 + j3;
                    int gridZ = fd3 + k3;
                    int index = getIndex(gridX, gridY, gridZ);
                    long cache = this.aquiferLocationCache[index];
                    int lx, ly, lz;
                    long longPos;

                    if (cache != Long.MAX_VALUE) {
                        longPos = cache;
                        IntBlockPos c = IntBlockPos.getBlockPos(longPos);
                        lx = c.x;
                        ly = c.y;
                        lz = c.z;
                    } else {
                        RandomSource r = this.positionalRandomFactory.at(gridX, gridY, gridZ);
                        lx = gridX * 16 + r.nextInt(10);
                        ly = gridY * 12 + r.nextInt(9);
                        lz = gridZ * 16 + r.nextInt(10);
                        longPos = BlockPos.asLong(lx, ly, lz);
                        this.aquiferLocationCache[index] = longPos;
                    }

                    int n1 = lx - i;
                    int n2 = ly - j;
                    int n3 = lz - k;
                    int value = n1 * n1 + n2 * n2 + n3 * n3;

                    for (int idx = 0; idx < 3; idx++) {
                        if (value < nearestDist[idx]) {
                            for (int shift = 2; shift > idx; shift--) {
                                nearestDist[shift] = nearestDist[shift - 1];
                                nearestPos[shift] = nearestPos[shift - 1];
                                nearestCoord[shift][0] = nearestCoord[shift - 1][0];
                                nearestCoord[shift][1] = nearestCoord[shift - 1][1];
                                nearestCoord[shift][2] = nearestCoord[shift - 1][2];
                            }
                            nearestDist[idx] = value;
                            nearestPos[idx] = longPos;
                            nearestCoord[idx][0] = lx;
                            nearestCoord[idx][1] = ly;
                            nearestCoord[idx][2] = lz;
                            break;
                        }
                    }
                }
            }
        }

        long i2 = nearestPos[0];
        long j2 = nearestPos[1];
        long k2 = nearestPos[2];

        int j2x = nearestCoord[0][0], j2y = nearestCoord[0][1], j2z = nearestCoord[0][2];
        int k2x = nearestCoord[1][0], k2y = nearestCoord[1][1], k2z = nearestCoord[1][2];
        int l2x = nearestCoord[2][0], l2y = nearestCoord[2][1], l2z = nearestCoord[2][2];

        int max1 = nearestDist[0];
        int max2 = nearestDist[1];
        int max3 = nearestDist[2];

        HashUtil.putBlock(i2, new IntBlockPos(j2x, j2y, j2z));
        HashUtil.putBlock(j2, new IntBlockPos(k2x, k2y, k2z));
        HashUtil.putBlock(k2, new IntBlockPos(l2x, l2y, l2z));

        Aquifer.FluidStatus fluidStatus1 = getAquiferStatus(i2);
        BlockState blockState = fluidStatus1.at(j);

        double s1 = similarity(max1, max2);
        if (s1 <= 0.0) {
            this.shouldScheduleFluidUpdate = s1 >= FLOWING_UPDATE_SIMULARITY;
            return blockState;
        } else if (blockState.is(Blocks.WATER)
                && this.globalFluidPicker.computeFluid(i, j - 1, k).at(j - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return blockState;
        } else {

            MutableDouble mutableDouble = new MutableDouble(Double.NaN);
            Aquifer.FluidStatus fluidStatus2 = getAquiferStatus(j2);

            double d2 = s1 * calculatePressure(context, mutableDouble, fluidStatus1, fluidStatus2);
            if (substance + d2 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }

            Aquifer.FluidStatus fluidStatus3 = getAquiferStatus(k2);
            double s0 = similarity(max1, max3);
            if (s0 > 0.0) {
                double d3 = s1 * s0 * calculatePressure(context, mutableDouble, fluidStatus1, fluidStatus3);
                if (substance + d3 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }

            double d4 = similarity(max2, max3);
            if (d4 > 0.0) {
                double d5 = s1 * d4 * calculatePressure(context, mutableDouble, fluidStatus2, fluidStatus3);
                if (substance + d5 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }

            this.shouldScheduleFluidUpdate = true;
            return blockState;
        }
    }
}
