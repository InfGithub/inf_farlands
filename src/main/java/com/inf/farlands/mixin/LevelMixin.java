package com.inf.farlands.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Level.class)
public abstract class LevelMixin {

    @ModifyVariable(
        method = "getChunk",
        at = @At("HEAD"),
        argsOnly = true,
        index = 1
    )
    private int remapChunkX(int cx) {
        if (cx < -33_554_432) {
            return -cx - 1;
        }
        return cx;
    }

    @ModifyVariable(
        method = "getChunk",
        at = @At("HEAD"),
        argsOnly = true,
        index = 2
    )
    private int remapChunkZ(int cz) {
        if (cz < -33_554_432) {
            return -cz - 1;
        }
        return cz;
    }
}
