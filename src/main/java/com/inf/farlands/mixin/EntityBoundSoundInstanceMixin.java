package com.inf.farlands.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityBoundSoundInstance.class)
public abstract class EntityBoundSoundInstanceMixin extends AbstractSoundInstance {

    @Shadow
    private Entity entity;

    @SuppressWarnings("DataFlowIssue")
    protected EntityBoundSoundInstanceMixin() {
        super((ResourceLocation) null, null, RandomSource.create());
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fixPrecision(CallbackInfo ci) {
        this.x = this.entity.getX();
        this.y = this.entity.getY();
        this.z = this.entity.getZ();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void fixPrecisionTick(CallbackInfo ci) {
        if (!this.entity.isRemoved()) {
            this.x = this.entity.getX();
            this.y = this.entity.getY();
            this.z = this.entity.getZ();
        }
    }
}
