package com.inf.farlands.client.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.client.renderer.WeatherEffectRenderer;

/**
 * 天气渲染提取 extractRenderState 的 X/Z 遍历范围在相机坐标接近世界边界时溢出。
 */
@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I"))
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
