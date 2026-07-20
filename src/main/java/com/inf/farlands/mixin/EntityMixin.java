package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public double xo;

    @Shadow
    public double yo;

    @Shadow
    public double zo;

    @Shadow
    public abstract void setPos(double x, double y, double z);

    @Inject(method = "checkInsideBlocks", at = @At("HEAD"), cancellable = true)
    private void skipCheckInsideBlocksInFarlands(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        double max = FarLandsConstants.MAX_BLOCK;
        if (Math.abs(self.getX()) > max || Math.abs(self.getZ()) > max) {
            ci.cancel();
        }
    }

    @Overwrite
    public void absMoveTo(double x, double y, double z) {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setPos(x, y, z);
    }

    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(DDD)D",
            ordinal = 0
        )
    )
    private double skipXClampOnLoad(double value, double min, double max) {
        return value;
    }

    @Redirect(
        method = "load",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(DDD)D",
            ordinal = 2
        )
    )
    private double skipZClampOnLoad(double value, double min, double max) {
        return value;
    }
}
