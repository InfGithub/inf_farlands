package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.WorldBoxRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.GameTestDebugRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * F3+G 三态循环：0=关、1=只世界盒、2=世界盒+区块线。
 *
 * vanilla 的 renderChunkborder（boolean，描述符 Z）字段不动（@Shadow 无法改
 * 类型），用 @Unique int chunkBorderState 做状态源；switchRenderChunkborder
 * 保持 boolean 签名（KeyboardHandler L179 的 flag1 接收，字节码零改动），
 * 内部三态循环，返回值只用于反馈消息 on/off（state 1/2 都显示 on，可接受）。
 */
@Mixin(DebugRenderer.class)
public abstract class DebugRendererMixin {

    @Unique
    private int chunkBorderState;

    @Shadow
    private DebugRenderer.SimpleDebugRenderer chunkBorderRenderer;

    @Shadow
    private GameTestDebugRenderer gameTestDebugRenderer;

    @Overwrite
    public boolean switchRenderChunkborder() {
        this.chunkBorderState = (this.chunkBorderState + 1) % 3;
        return this.chunkBorderState != 0;
    }

    @SuppressWarnings("null")
    @Overwrite
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY,
            double camZ) {
        boolean show = !Minecraft.getInstance().showOnlyReducedInfo();
        if (this.chunkBorderState >= 1 && show) {
            ((WorldBoxRenderer) this.chunkBorderRenderer).renderWorldBox(poseStack, bufferSource, camX, camY, camZ);
        }
        if (this.chunkBorderState >= 2 && show) {
            this.chunkBorderRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }
        this.gameTestDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
    }
}
