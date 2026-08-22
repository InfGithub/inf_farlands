package com.inf.farlands.mixin.axisYOverflowFix;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Fix BlockEntityInfo Y coordinate overflowing the vanilla short encoding.
 * <p>
 * Vanilla encodes the BE world Y as {@code writeShort} — |Y| &gt; 32767 is
 * truncated (e.g. 2,147,483,631 → -17), so the client fails to create the
 * block entity at the garbage position and it never renders. The wire format
 * is changed to 4-byte int (both ends run this mod). The original {@code y}
 * field keeps the truncated value (unused); the full value is stored in
 * {@code yCorrect} and consumed by the chunk packet mixin that rebuilds the
 * position.
 */
@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo")
public abstract class ClientboundLevelChunkPacketData$BlockEntityInfoMixin {

    @Unique
    private int yCorrect;

    @Redirect(method = "<init>(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;readShort()S"))
    private short redirectReadShort(RegistryFriendlyByteBuf buffer) {
        this.yCorrect = buffer.readInt();
        return (short) this.yCorrect;
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;writeShort(I)Lnet/minecraft/network/FriendlyByteBuf;"))
    private FriendlyByteBuf redirectWriteShort(RegistryFriendlyByteBuf buffer, int value) {
        return buffer.writeInt(value);
    }
}
