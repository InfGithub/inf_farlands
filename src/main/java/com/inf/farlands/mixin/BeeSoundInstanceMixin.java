package com.inf.farlands.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.BeeSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Bee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeeSoundInstance.class)
public abstract class BeeSoundInstanceMixin extends AbstractSoundInstance {

    @Shadow
    protected Bee bee;

    @SuppressWarnings("DataFlowIssue")
    protected BeeSoundInstanceMixin() {
        super((ResourceLocation) null, null, RandomSource.create());
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fixPrecision(CallbackInfo ci) {
        this.x = this.bee.getX();
        this.y = this.bee.getY();
        this.z = this.bee.getZ();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void fixPrecisionTick(CallbackInfo ci) {
        if (!this.bee.isRemoved()) {
            this.x = this.bee.getX();
            this.y = this.bee.getY();
            this.z = this.bee.getZ();
        }
    }
}
