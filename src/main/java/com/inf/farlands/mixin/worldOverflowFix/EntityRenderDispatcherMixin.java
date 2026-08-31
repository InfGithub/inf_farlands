package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 实体阴影渲染 renderShadow 的 X/Z 遍历范围在实体坐标接近世界边界时溢出：
 * Mth.floor(d0 + size)，其中 size ≤ 32，在 d0 > 2147483615 时 double→int 饱和为
 * 2147483647 → for (l1 = i; l1 <= j; l1++) 的上界 j = int max →
 * l1++ 溢出为负 → <= j 恒 true → 无限循环，玩家走进 x>2147483632 后冻结。
 * 修复：6 处 Mth.floor 全部 clamp 到 [int min+1, int max-1]——上界 ≤ 2147483646
 * 保证循环 ++ 正常退出；下界防 d0-size 饱和为 int max 时起点即上界的死循环。
 * 正常坐标 clamp 恒等零变化；边界影响仅阴影少遍历 2147483647 一格，可忽略。
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Redirect(method = "renderShadow",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;floor(D)I"))
    private static int floorSafe(double v) {
        int r = (int) Math.floor(v);
        if (r > 2147483646) {
            return 2147483646;
        }
        if (r < -2147483647) {
            return -2147483647;
        }
        return r;
    }
}
