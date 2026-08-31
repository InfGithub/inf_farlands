package com.inf.farlands.client.mixin.expand.xyz.pos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.inf.farlands.util.pos.IntSectionPos;

import net.minecraft.client.renderer.debug.LightDebugRenderer;

@Mixin(LightDebugRenderer.class)
public class LightDebugRendererMixin {

    // 替换 SectionPos.x(long) 调用
    @Redirect(method = "emitGizmos", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;x(J)I"))
    private int redirectSectionX(long sectionNode) {
        return IntSectionPos.getSectionPos(sectionNode).x;
    }

    @Redirect(method = "emitGizmos", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;y(J)I"))
    private int redirectSectionY(long sectionNode) {
        return IntSectionPos.getSectionPos(sectionNode).y;
    }

    @Redirect(method = "emitGizmos", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;z(J)I"))
    private int redirectSectionZ(long sectionNode) {
        return IntSectionPos.getSectionPos(sectionNode).z;
    }
}