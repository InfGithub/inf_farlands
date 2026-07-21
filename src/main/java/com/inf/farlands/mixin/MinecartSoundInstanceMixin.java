package com.inf.farlands.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.MinecartSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecartSoundInstance.class)
public abstract class MinecartSoundInstanceMixin extends AbstractSoundInstance {

    @Shadow
    private AbstractMinecart minecart;

    @SuppressWarnings("DataFlowIssue")
    protected MinecartSoundInstanceMixin() {
        super((ResourceLocation) null, null, RandomSource.create());
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fixPrecision(CallbackInfo ci) {
        this.x = this.minecart.getX();
        this.y = this.minecart.getY();
        this.z = this.minecart.getZ();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void fixPrecisionTick(CallbackInfo ci) {
        if (!this.minecart.isRemoved()) {
            this.x = this.minecart.getX();
            this.y = this.minecart.getY();
            this.z = this.minecart.getZ();
        }
    }
}
