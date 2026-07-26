package com.inf.farlands.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {

    @Shadow
    private java.util.concurrent.Future<?> fullUpdateTask;

    @Inject(method = "update", at = @At("TAIL"))
    private void waitForFullUpdate(CallbackInfo ci) {
        if (this.fullUpdateTask != null) {
            try {
                this.fullUpdateTask.get();
            } catch (Exception ignored) {
            }
        }
    }
}
