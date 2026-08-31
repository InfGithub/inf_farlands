package com.inf.farlands;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * 世界盒渲染入口：DebugRendererMixin 三态中 state 1/2 时调用。
 * 由 ChunkBorderRendererMixin 注入到 ChunkBorderRenderer，采用接口 mixin 模式，
 * 与 WindowedChunk 相同——跨类免反射调用。
 */
public interface WorldBoxRenderer {
    void renderWorldBox(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ);
}
