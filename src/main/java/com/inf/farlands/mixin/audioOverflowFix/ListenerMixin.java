package com.inf.farlands.mixin.audioOverflowFix;

import com.mojang.blaze3d.audio.Listener;
import com.mojang.blaze3d.audio.ListenerTransform;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Listener.class)
public abstract class ListenerMixin {

    @SuppressWarnings("null")
    @ModifyVariable(method = "setTransform", at = @At("HEAD"), ordinal = 0)
    private ListenerTransform zeroListenerPosition(ListenerTransform transform) {
        return new ListenerTransform(
                Vec3.ZERO,
                transform.forward(),
                transform.up());
    }
}
