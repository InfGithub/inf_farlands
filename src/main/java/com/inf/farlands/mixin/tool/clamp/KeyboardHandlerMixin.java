package com.inf.farlands.mixin.tool.clamp;

import com.inf.farlands.tool.clamp.FarLandsClampTogglePacket;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** F3+K 触发钳制模式 toggle。客户端发请求，服务端校验 OP。 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {

    @SuppressWarnings("null")
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void farlandsClampToggle(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key == 75) { // F3+K
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.connection.send(new ServerboundCustomPayloadPacket(new FarLandsClampTogglePacket()));
                cir.setReturnValue(true);
            }
        }
    }
}