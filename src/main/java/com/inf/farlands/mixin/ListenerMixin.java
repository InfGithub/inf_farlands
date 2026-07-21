package com.inf.farlands.mixin;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Moves the OpenAL listener to the origin while keeping the original
 * forward / up orientation.  Combined with {@link SoundEngineMixin}
 * (which shifts every source by the camera position), this keeps all
 * audio coordinates within audible range so that float precision is
 * never a problem.
 */
@Mixin(Listener.class)
public abstract class ListenerMixin {

    @ModifyVariable(
        method = "setTransform",
        at = @At("HEAD"),
        ordinal = 0
    )
    private ListenerTransform zeroListenerPosition(ListenerTransform transform) {
        return new ListenerTransform(
            Vec3.ZERO,
            transform.forward(),
            transform.up()
        );
    }
}
