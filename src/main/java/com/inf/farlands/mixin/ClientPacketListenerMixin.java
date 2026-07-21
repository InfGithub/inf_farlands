package com.inf.farlands.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void filterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        BlockPos pos = packet.getPos();
        int x = pos.getX();
        int z = pos.getZ();
        if (x >= Integer.MAX_VALUE - 1 || x <= Integer.MIN_VALUE + 1 ||
            z >= Integer.MAX_VALUE - 1 || z <= Integer.MIN_VALUE + 1) {
            ci.cancel();
        }
    }
}
