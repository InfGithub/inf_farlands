package com.inf.farlands.mixin.expand.xyz.pos;

import org.apache.commons.lang3.mutable.MutableDouble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.block.Blocks;

import com.inf.farlands.util.pos.AquiferPos;
import com.inf.farlands.util.pos.IntBlockPos;
import com.inf.farlands.util.maps.aquifer.AquiferUtil;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class Aquifer$NoiseBasedAquiferMixin {
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
        if (index < 0 || index >= this.aquiferCache.length) {
            return 0;
        }
        return (int) index;
    }

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
        AquiferPos ap = AquiferUtil.get(packedPos);
        int bx, by, bz;
        if (ap != null) {
            bx = ap.x;
            by = ap.y;
            bz = ap.z;
        } else {
            IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
            bx = bp.x;
            by = bp.y;
            bz = bp.z;
        }
        int i = gridX(bx);
        int j = gridY(by);
        int k = gridZ(bz);
        int k1 = getIndex(i, j, k);
        Aquifer.FluidStatus s = aquiferCache[k1];
        if (s == null) {
            Aquifer.FluidStatus c = computeFluid(bx, by, bz);
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

    @Overwrite
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        if (substance > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus aquifer$fluidstatus = this.globalFluidPicker.computeFluid(x, y, z);
        if (aquifer$fluidstatus.at(y).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = false;
            return Blocks.LAVA.defaultBlockState();
        }

        int fd1 = Math.floorDiv(x - 5, 16);
        int fd2 = Math.floorDiv(y + 1, 12);
        int fd3 = Math.floorDiv(z - 5, 16);
        int i = Integer.MAX_VALUE;
        int j = Integer.MAX_VALUE;
        int k = Integer.MAX_VALUE;
        long j2 = 0L;
        long k2 = 0L;
        long l2 = 0L;

        for (int i3 = 0; i3 <= 1; i3++) {
            for (int j3 = -1; j3 <= 1; j3++) {
                for (int k3 = 0; k3 <= 1; k3++) {
                    int gridX = fd1 + i3;
                    int gridY = fd2 + j3;
                    int gridZ = fd3 + k3;
                    int index = this.getIndex(gridX, gridY, gridZ);
                    long cache = this.aquiferLocationCache[index];
                    long pos;
                    if (cache != Long.MAX_VALUE) {
                        pos = cache;
                        AquiferPos c = AquiferUtil.get(pos);
                        if (c == null) {
                            IntBlockPos bp = IntBlockPos.getBlockPos(pos);
                            c = new AquiferPos(bp.x, bp.y, bp.z);
                        }
                    } else {
                        RandomSource randomsource = this.positionalRandomFactory.at(gridX, gridY, gridZ);
                        int lx = gridX * 16 + randomsource.nextInt(10);
                        int ly = gridY * 12 + randomsource.nextInt(9);
                        int lz = gridZ * 16 + randomsource.nextInt(10);
                        pos = BlockPos.asLong(lx, ly, lz);
                        AquiferUtil.put(pos, lx, ly, lz);
                        this.aquiferLocationCache[index] = pos;
                    }

                    int n1 = BlockPos.getX(pos) - x;
                    int n2 = BlockPos.getY(pos) - y;
                    int n3 = BlockPos.getZ(pos) - z;
                    int value = n1 * n1 + n2 * n2 + n3 * n3;
                    if (i >= value) {
                        l2 = k2;
                        k2 = j2;
                        j2 = pos;
                        k = j;
                        j = i;
                        i = value;
                    } else if (j >= value) {
                        l2 = k2;
                        k2 = pos;
                        k = j;
                        j = value;
                    } else if (k >= value) {
                        l2 = pos;
                        k = value;
                    }
                }
            }
        }

        Aquifer.FluidStatus aquifer$fluidstatus1 = this.getAquiferStatus(j2);
        double s1 = similarity(i, j);
        BlockState blockstate = aquifer$fluidstatus1.at(y);
        if (s1 <= 0.0) {
            this.shouldScheduleFluidUpdate = s1 >= FLOWING_UPDATE_SIMULARITY;
            return blockstate;
        }
        if (blockstate.is(Blocks.WATER)
                && this.globalFluidPicker.computeFluid(x, y - 1, z).at(y - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true;
            return blockstate;
        }

        MutableDouble mutabledouble = new MutableDouble(Double.NaN);
        Aquifer.FluidStatus aquifer$fluidstatus2 = this.getAquiferStatus(k2);
        double d2 = s1 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus1,
                aquifer$fluidstatus2);
        if (substance + d2 > 0.0) {
            this.shouldScheduleFluidUpdate = false;
            return null;
        }

        Aquifer.FluidStatus aquifer$fluidstatus3 = this.getAquiferStatus(l2);
        double s2 = similarity(i, k);
        if (s2 > 0.0) {
            double d3 = s1 * s2 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus1,
                    aquifer$fluidstatus3);
            if (substance + d3 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
        }

        double s3 = similarity(j, k);
        if (s3 > 0.0) {
            double d5 = s1 * s3 * this.calculatePressure(context, mutabledouble, aquifer$fluidstatus2,
                    aquifer$fluidstatus3);
            if (substance + d5 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
        }

        this.shouldScheduleFluidUpdate = true;
        return blockstate;
    }
}
