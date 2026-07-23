package com.inf.farlands.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.entity.Entity;

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

    // 方法：
    // public void absMoveTo(double x, double y, double z) {
    //     double d0 = Mth.clamp(x, -3.0E7, 3.0E7);
    //     double d1 = Mth.clamp(z, -3.0E7, 3.0E7);
    //     this.xo = d0;
    //     this.yo = y;
    //     this.zo = d1;
    //     this.setPos(d0, y, d1);
    // }

    @Overwrite
    public void absMoveTo(double x, double y, double z) {
        this.xo = x;
        this.yo = y;
        this.zo = z;
        this.setPos(x, y, z);
    }

    // 方法：
    // this.setPosRaw(
    //   Mth.clamp(listtag.getDouble(0),-3.0000512E7,3.0000512E7),
    //   Mth.clamp(listtag.getDouble(1),-2.0E7,2.0E7),
    //   Mth.clamp(listtag.getDouble(2),-3.0000512E7,3.0000512E7));


    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 0))
    private double skipXClampOnLoad(double value, double min, double max) {
        return value;
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 1))
    private double skipYClampOnLoad(double value, double min, double max) {
        return value;
    }

    @Redirect(method = "load", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(DDD)D", ordinal = 2))
    private double skipZClampOnLoad(double value, double min, double max) {
        return value;
    }
}
