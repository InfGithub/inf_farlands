package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Level.class)
public class LevelBoundsMixin {

    @Overwrite
    private static boolean isInWorldBoundsHorizontal(
        net.minecraft.core.BlockPos pos
    ) {
        int max = Config.borderAbsoluteMax;
        return (
            pos.getX() >= -max &&
            pos.getZ() >= -max &&
            pos.getX() < max &&
            pos.getZ() < max
        );
    }
}
