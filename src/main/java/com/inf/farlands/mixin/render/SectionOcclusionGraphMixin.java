package com.inf.farlands.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.Future;
import net.minecraft.client.renderer.SectionOcclusionGraph;

@Mixin(SectionOcclusionGraph.class)
public class SectionOcclusionGraphMixin {

    @Shadow
    private Future<?> fullUpdateTask;

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
