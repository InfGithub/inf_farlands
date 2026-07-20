package com.inf.farlands.mixin;

import net.minecraft.world.level.levelgen.Aquifer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class AquiferMixin {

    @Shadow
    protected abstract int gridX(int x);

    @Shadow
    protected abstract int gridY(int y);

    @Shadow
    protected abstract int gridZ(int z);

    @Shadow
    private int minGridX;

    @Shadow
    private int minGridY;

    @Shadow
    private int minGridZ;

    @Shadow
    private int gridSizeX;

    @Shadow
    private int gridSizeZ;

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
}
