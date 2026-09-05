package com.inf.farlands.client.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.client.renderer.entity.EntityRenderer;

/**
 * 实体阴影提取 extractShadow 的 X/Z/Y 遍历范围在实体坐标接近世界边界时溢出。
 */
@Mixin(EntityRenderer.class)
public class EntityRendererMixin {

    @Redirect(method = "extractShadow", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I"))
    private static int floorSafe(double v) {
        int r = (int) Math.floor(v);
        if (r > FarlandsConstant.MAX_BLOCK - 1) {
            return FarlandsConstant.MAX_BLOCK - 1;
        }
        if (r < -FarlandsConstant.MAX_BLOCK) {
            return -FarlandsConstant.MAX_BLOCK;
        }
        return r;
    }
}
