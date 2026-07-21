package com.inf.farlands.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SnifferSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SnifferSoundInstance.class)
public abstract class SnifferSoundInstanceMixin extends AbstractSoundInstance {

    @Shadow
    private Sniffer sniffer;

    @SuppressWarnings("DataFlowIssue")
    protected SnifferSoundInstanceMixin() {
        super((ResourceLocation) null, null, RandomSource.create());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void fixPrecisionTick(CallbackInfo ci) {
        if (!this.sniffer.isRemoved() && this.sniffer.getTarget() == null && this.sniffer.canPlayDiggingSound()) {
            this.x = this.sniffer.getX();
            this.y = this.sniffer.getY();
            this.z = this.sniffer.getZ();
        }
    }
}
