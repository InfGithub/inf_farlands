package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PathNavigation.class)
public class PathNavigationRegionMixin {

    private static final int MAX_BLOCK = FarLandsConstants.MAX_BLOCK;

    /**
     * Clamp BlockPos.offset results in createPath to prevent
     * PathNavigationRegion NegativeArraySizeException when
     * AI goal targets overflow int near the coordinate boundary.
     */
    @Redirect(
        method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/BlockPos;offset(III)Lnet/minecraft/core/BlockPos;"
        )
    )
    private BlockPos clampOffset(BlockPos self, int dx, int dy, int dz) {
        long newX = (long) self.getX() + dx;
        long newZ = (long) self.getZ() + dz;
        if (newX > MAX_BLOCK) newX = MAX_BLOCK;
        else if (newX < -MAX_BLOCK) newX = -MAX_BLOCK;
        if (newZ > MAX_BLOCK) newZ = MAX_BLOCK;
        else if (newZ < -MAX_BLOCK) newZ = -MAX_BLOCK;
        long newY = (long) self.getY() + dy;
        return new BlockPos((int) newX, (int) newY, (int) newZ);
    }
}
