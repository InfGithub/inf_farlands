package com.inf.farlands.mixin;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoundingBox.class)
public class BoundingBoxMixin {

    @Shadow
    private int minX, minY, minZ, maxX, maxY, maxZ;

    @Inject(method = "moved", at = @At("HEAD"), cancellable = true)
    private void safeMoved(
        int x,
        int y,
        int z,
        CallbackInfoReturnable<BoundingBox> cir
    ) {
        long newMinX = (long) this.minX + x;
        long newMaxX = (long) this.maxX + x;
        long newMinZ = (long) this.minZ + z;
        long newMaxZ = (long) this.maxZ + z;
        if (
            newMinX > Integer.MAX_VALUE ||
            newMinX < Integer.MIN_VALUE ||
            newMaxX > Integer.MAX_VALUE ||
            newMaxX < Integer.MIN_VALUE ||
            newMinZ > Integer.MAX_VALUE ||
            newMinZ < Integer.MIN_VALUE ||
            newMaxZ > Integer.MAX_VALUE ||
            newMaxZ < Integer.MIN_VALUE
        ) {
            cir.setReturnValue((BoundingBox) (Object) this);
            return;
        }
        if (newMinX > newMaxX || newMinZ > newMaxZ) {
            cir.setReturnValue((BoundingBox) (Object) this);
            return;
        }
    }
}
