package com.inf.farlands.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes sound source positions listener-relative before they reach OpenAL.
 * <p>
 * Combines with {@link ListenerMixin} (which moves the OpenAL listener
 * to the origin).  Sources are shifted by the camera position, keeping
 * all coordinates within audible range where float precision is adequate.
 * The original {@code isRelative()} flag is left untouched so OpenAL
 * spatialisation still applies listener orientation correctly.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {

    @Unique
    private Vec3 cachedListenerPos = Vec3.ZERO;

    @Unique
    private boolean playSoundWasRelative;

    @Unique
    private boolean tickSoundWasRelative;

    @Inject(method = "updateSource", at = @At("HEAD"))
    private void captureListenerPosition(Camera renderInfo, CallbackInfo ci) {
        this.cachedListenerPos = renderInfo.getPosition();
    }

    @Unique
    private Vec3 makeRelative(Vec3 absolute) {
        return absolute.subtract(this.cachedListenerPos);
    }

    // ---- play() ----

    @ModifyVariable(
        method = "play",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Listener;getGain()F"
        ),
        ordinal = 0
    )
    private boolean capturePlayFlag(boolean flag) {
        this.playSoundWasRelative = flag;
        return flag;
    }

    @ModifyVariable(
        method = "play",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Listener;getGain()F"
        )
    )
    private Vec3 makePlayRelative(Vec3 original) {
        if (this.playSoundWasRelative) {
            return original;
        }
        return makeRelative(original);
    }

    // ---- tickNonPaused() ----

    @ModifyVariable(
        method = "tickNonPaused",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
            ordinal = 0
        ),
        ordinal = 0
    )
    private TickableSoundInstance captureTickFlag(TickableSoundInstance tsi) {
        this.tickSoundWasRelative = tsi.isRelative();
        return tsi;
    }

    @ModifyVariable(
        method = "tickNonPaused",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;",
            ordinal = 0
        )
    )
    private Vec3 makeTickRelative(Vec3 original) {
        if (this.tickSoundWasRelative) {
            return original;
        }
        return makeRelative(original);
    }
}
