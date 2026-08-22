package com.inf.farlands.mixin.audioOverflowFix;

import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fix sound coordinates overflowing the vanilla int*8 encoding.
 * <p>
 * Vanilla stores sound positions as {@code (int)(coord * 8.0)} — at
 * |Y| &gt; 268M this saturates at Integer.MAX_VALUE, so the client decodes a
 * garbage position (~268M blocks away) and OpenAL attenuation silences the
 * sound (chests, barrels, any server-side {@code playSound(double...)}).
 * <p>
 * This mixin keeps the exact same wire layout (sound, source, x, y, z,
 * volume, pitch, seed) but transports coordinates as 8-byte longs
 * ({@code (long)(coord * 8.0)}). Decoding uses {@code readLong} via
 * {@link Redirect} of the three {@code readInt} calls; the original int
 * fields keep their saturated values but are never read (getters are
 * overwritten to decode from the long fields). The packet grows 12 bytes.
 */
@Mixin(ClientboundSoundPacket.class)
public abstract class ClientboundSoundPacketMixin {

    @Shadow
    @Final
    private Holder<SoundEvent> sound;

    @Shadow
    @Final
    private SoundSource source;

    @Shadow
    @Final
    private float volume;

    @Shadow
    @Final
    private float pitch;

    @Shadow
    @Final
    private long seed;

    @Unique
    private long x8, y8, z8;

    @Unique
    private int coordIndex;

    @Inject(method = "<init>(Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;DDDFFJ)V", at = @At("RETURN"))
    private void storeLongCoords(Holder<SoundEvent> sound, SoundSource source, double x, double y, double z,
            float volume, float pitch, long seed, CallbackInfo ci) {
        this.x8 = (long) (x * 8.0);
        this.y8 = (long) (y * 8.0);
        this.z8 = (long) (z * 8.0);
    }

    @Redirect(method = "<init>(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;readInt()I"))
    private int redirectReadInt(RegistryFriendlyByteBuf buffer) {
        long v = buffer.readLong();
        if (this.coordIndex == 0) {
            this.x8 = v;
        } else if (this.coordIndex == 1) {
            this.y8 = v;
        } else {
            this.z8 = v;
        }
        this.coordIndex++;
        return (int) v;
    }

    @SuppressWarnings("null")
    @Overwrite
    private void write(RegistryFriendlyByteBuf buffer) {
        SoundEvent.STREAM_CODEC.encode(buffer, this.sound);
        buffer.writeEnum(this.source);
        buffer.writeLong(this.x8);
        buffer.writeLong(this.y8);
        buffer.writeLong(this.z8);
        buffer.writeFloat(this.volume);
        buffer.writeFloat(this.pitch);
        buffer.writeLong(this.seed);
    }

    @Overwrite
    public double getX() {
        return (double) this.x8 / 8.0;
    }

    @Overwrite
    public double getY() {
        return (double) this.y8 / 8.0;
    }

    @Overwrite
    public double getZ() {
        return (double) this.z8 / 8.0;
    }
}
