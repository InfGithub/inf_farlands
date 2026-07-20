package com.inf.farlands.mixin;

import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ChunkPos.class)
public abstract class ChunkPosMixin {

    @Shadow public int x;
    @Shadow public int z;

    @Overwrite
    public int getMinBlockX() {
        long val = (long)this.x << 4;
        if (val > Integer.MAX_VALUE - 15) return Integer.MAX_VALUE - 15;
        if (val < Integer.MIN_VALUE + 15) return Integer.MIN_VALUE + 15;
        return (int)val;
    }

    @Overwrite
    public int getMinBlockZ() {
        long val = (long)this.z << 4;
        if (val > Integer.MAX_VALUE - 15) return Integer.MAX_VALUE - 15;
        if (val < Integer.MIN_VALUE + 15) return Integer.MIN_VALUE + 15;
        return (int)val;
    }
}
