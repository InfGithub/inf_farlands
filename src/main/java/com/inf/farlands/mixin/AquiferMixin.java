package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class AquiferMixin {

    @Shadow protected abstract int gridX(int x);
    @Shadow protected abstract int gridY(int y);
    @Shadow protected abstract int gridZ(int z);
    @Shadow private int minGridX, minGridY, minGridZ;
    @Shadow private int gridSizeX, gridSizeZ;
    @Shadow private Aquifer.FluidStatus[] aquiferCache;
    @Shadow private long[] aquiferLocationCache;
    @Shadow private Aquifer.FluidPicker globalFluidPicker;
    @Shadow private PositionalRandomFactory positionalRandomFactory;
    @Shadow private DensityFunction barrierNoise;
    @Shadow protected abstract Aquifer.FluidStatus computeFluid(int x, int y, int z);
    @Shadow private boolean shouldScheduleFluidUpdate;
    @Shadow private static double FLOWING_UPDATE_SIMULARITY;
    @Shadow protected abstract double calculatePressure(DensityFunction.FunctionContext ctx, MutableDouble s, Aquifer.FluidStatus a, Aquifer.FluidStatus b);
    @Shadow protected static double similarity(int a, int b) { return 0.0; }

    // === method3: block-level key → IntBlockPos ===
    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp
            : new IntBlockPos(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
    }

    @Overwrite
    protected int getIndex(int gridX, int gridY, int gridZ) {
        long i = (long) gridX - this.minGridX;
        long j = (long) gridY - this.minGridY;
        long k = (long) gridZ - this.minGridZ;
        long index = (j * this.gridSizeZ + k) * this.gridSizeX + i;
        if (index < 0 || index >= this.aquiferCache.length) return 0;
        return (int) index;
    }

    // === method1 @Overwrite: getAquiferStatus ===
    @Overwrite
    private Aquifer.FluidStatus getAquiferStatus(long packedPos) {
        IntBlockPos bp = getBlockPos(packedPos);
        int l = gridX(bp.x), i1 = gridY(bp.y), j1 = gridZ(bp.z);
        int k1 = getIndex(l, i1, j1);
        Aquifer.FluidStatus s = aquiferCache[k1];
        if (s != null) return s;
        Aquifer.FluidStatus c = computeFluid(bp.x, bp.y, bp.z);
        aquiferCache[k1] = c;
        return c;
    }

    // === method1 @Overwrite: computeSubstance ===
    // Coordinates flow through the swap chain as (jx,jy,jz) triples alongside longs
    @Overwrite
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
        int i = context.blockX(), j = context.blockY(), k = context.blockZ();
        if (substance > 0.0) { this.shouldScheduleFluidUpdate = false; return null; }
        Aquifer.FluidStatus aq = this.globalFluidPicker.computeFluid(i, j, k);
        if (aq.at(j).is(Blocks.LAVA)) { this.shouldScheduleFluidUpdate = false; return Blocks.LAVA.defaultBlockState(); }
        int l = Math.floorDiv(i - 5, 16), i1 = Math.floorDiv(j + 1, 12), j1d = Math.floorDiv(k - 5, 16);
        int k1 = Integer.MAX_VALUE, l1 = Integer.MAX_VALUE, i2 = Integer.MAX_VALUE;
        long j2 = 0L, k2 = 0L, l2 = 0L;
        int j2x = 0, j2y = 0, j2z = 0, k2x = 0, k2y = 0, k2z = 0, l2x = 0, l2y = 0, l2z = 0;
        for (int i3 = 0; i3 <= 1; i3++) {
            for (int j3 = -1; j3 <= 1; j3++) {
                for (int k3 = 0; k3 <= 1; k3++) {
                    int l3 = l + i3, i4 = i1 + j3, j4 = j1d + k3, k4 = getIndex(l3, i4, j4);
                    long i5 = this.aquiferLocationCache[k4];
                    int lx, ly, lz; long l4;
                    if (i5 != Long.MAX_VALUE) {
                        l4 = i5;
                        IntBlockPos c = getBlockPos(l4);
                        lx = c.x; ly = c.y; lz = c.z;
                    } else {
                        RandomSource r = this.positionalRandomFactory.at(l3, i4, j4);
                        lx = l3 * 16 + r.nextInt(10); ly = i4 * 12 + r.nextInt(9); lz = j4 * 16 + r.nextInt(10);
                        l4 = BlockPos.asLong(lx, ly, lz);
                        this.aquiferLocationCache[k4] = l4;
                    }
                    int i6 = lx - i, j5 = ly - j, k5 = lz - k, l5 = i6 * i6 + j5 * j5 + k5 * k5;
                    if (k1 >= l5) {
                        l2 = k2; l2x = k2x; l2y = k2y; l2z = k2z;
                        k2 = j2; k2x = j2x; k2y = j2y; k2z = j2z;
                        j2 = l4; j2x = lx; j2y = ly; j2z = lz; i2 = l1; l1 = k1; k1 = l5;
                    } else if (l1 >= l5) {
                        l2 = k2; l2x = k2x; l2y = k2y; l2z = k2z;
                        k2 = l4; k2x = lx; k2y = ly; k2z = lz; i2 = l1; l1 = l5;
                    } else if (i2 >= l5) {
                        l2 = l4; l2x = lx; l2y = ly; l2z = lz; i2 = l5;
                    }
                }
            }
        }
        HashUtil.putBlock(j2, new IntBlockPos(j2x, j2y, j2z));
        HashUtil.putBlock(k2, new IntBlockPos(k2x, k2y, k2z));
        HashUtil.putBlock(l2, new IntBlockPos(l2x, l2y, l2z));
        Aquifer.FluidStatus a1 = getAquiferStatus(j2);
        double d1 = similarity(k1, l1);
        BlockState bs = a1.at(j);
        if (d1 <= 0.0) { this.shouldScheduleFluidUpdate = d1 >= FLOWING_UPDATE_SIMULARITY; return bs; }
        else if (bs.is(Blocks.WATER) && this.globalFluidPicker.computeFluid(i, j - 1, k).at(j - 1).is(Blocks.LAVA)) {
            this.shouldScheduleFluidUpdate = true; return bs;
        } else {
            MutableDouble md = new MutableDouble(Double.NaN);
            Aquifer.FluidStatus a2 = getAquiferStatus(k2);
            double d2 = d1 * calculatePressure(context, md, a1, a2);
            if (substance + d2 > 0.0) { this.shouldScheduleFluidUpdate = false; return null; }
            Aquifer.FluidStatus a3 = getAquiferStatus(l2);
            double d0 = similarity(k1, i2);
            if (d0 > 0.0) { double d3 = d1 * d0 * calculatePressure(context, md, a1, a3); if (substance + d3 > 0.0) { this.shouldScheduleFluidUpdate = false; return null; } }
            double d4 = similarity(l1, i2);
            if (d4 > 0.0) { double d5 = d1 * d4 * calculatePressure(context, md, a2, a3); if (substance + d5 > 0.0) { this.shouldScheduleFluidUpdate = false; return null; } }
            this.shouldScheduleFluidUpdate = true; return bs;
        }
    }
}
