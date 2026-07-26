package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Constants;
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
        if (x >= Constants.MAX_BLOCK || x <= ~Constants.MAX_BLOCK ||
            z >= Constants.MAX_BLOCK || z <= ~Constants.MAX_BLOCK) {
            ci.cancel();
        }
    }
}
