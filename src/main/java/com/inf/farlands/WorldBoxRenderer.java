package com.inf.farlands;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 世界盒渲染入口（DebugRendererMixin 三态：state 1/2 时调用）。
 * 由 ChunkBorderRendererMixin 注入到 ChunkBorderRenderer（接口 mixin 模式，
 * 同 WindowedChunk——跨类免反射调用）。
 */
public interface WorldBoxRenderer {
    void renderWorldBox(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ);
}
