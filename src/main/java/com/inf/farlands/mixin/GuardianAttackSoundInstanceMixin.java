package com.inf.farlands.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.GuardianAttackSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Guardian;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuardianAttackSoundInstance.class)
public abstract class GuardianAttackSoundInstanceMixin extends AbstractSoundInstance {

    @Shadow
    private Guardian guardian;

    @SuppressWarnings("DataFlowIssue")
    protected GuardianAttackSoundInstanceMixin() {
        super((ResourceLocation) null, null, RandomSource.create());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void fixPrecisionTick(CallbackInfo ci) {
        if (!this.guardian.isRemoved() && this.guardian.getTarget() == null) {
            this.x = this.guardian.getX();
            this.y = this.guardian.getY();
            this.z = this.guardian.getZ();
        }
    }
}
