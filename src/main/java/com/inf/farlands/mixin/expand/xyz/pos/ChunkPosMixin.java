package com.inf.farlands.mixin.expand.xyz.pos;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkPos.class)
public abstract class ChunkPosMixin {

    @Shadow
    public int x;
    @Shadow
    public int z;

    @Unique
    private int shiftToBlockCoord(int coord) {
        long val = (long) coord << 4;
        if (val > Integer.MAX_VALUE - 15)
            return Integer.MAX_VALUE - 15;
        if (val < Integer.MIN_VALUE + 15)
            return Integer.MIN_VALUE + 15;
        return (int) val;
    }

    @Overwrite
    public int getMinBlockX() {
        return shiftToBlockCoord(this.x);
    }

    @Overwrite
    public int getMinBlockZ() {
        return shiftToBlockCoord(this.z);
    }

    @Overwrite
    public static boolean isValid(int x, int z) {
        return true;
    }
}
