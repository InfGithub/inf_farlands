package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GroundPathNavigation.class)
public class PathNavigationMixin {

    @Inject(
        method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void skipCreatePathAtFarlands(
        BlockPos pos,
        int accuracy,
        CallbackInfoReturnable<Object> cir
    ) {
        int x = pos.getX();
        int z = pos.getZ();
        int maxBlock = FarLandsConstants.MAX_BLOCK;
        if (x < -maxBlock || x > maxBlock || z < -maxBlock || z > maxBlock) {
            cir.setReturnValue(null);
        }
    }
}
