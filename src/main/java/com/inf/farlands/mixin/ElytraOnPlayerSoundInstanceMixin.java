package com.inf.farlands.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraOnPlayerSoundInstance.class)
public abstract class ElytraOnPlayerSoundInstanceMixin extends AbstractSoundInstance {

    @Shadow
    private LocalPlayer player;

    @SuppressWarnings("DataFlowIssue")
    protected ElytraOnPlayerSoundInstanceMixin() {
        super((ResourceLocation) null, null, RandomSource.create());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void fixPrecisionTick(CallbackInfo ci) {
        if (!this.player.isRemoved()) {
            this.x = this.player.getX();
            this.y = this.player.getY();
            this.z = this.player.getZ();
        }
    }
}
