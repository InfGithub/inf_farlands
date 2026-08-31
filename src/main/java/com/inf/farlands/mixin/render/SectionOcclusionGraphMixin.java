package com.inf.farlands.mixin.render;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.concurrent.Future;
import net.minecraft.client.renderer.SectionOcclusionGraph;
import net.minecraft.core.BlockPos;

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

    /**
     * 射线步进起点 offset +16 的 int 溢出防护：ViewArea origin 因极端坐标与
     * floorMod 环绕可贴到 int 边界，origin.x 进入 [2147483632, int max] 时 +16
     * 溢出回绕成负 → vec31 跨符号 → 射线从 -2.14B 步进到相机 2.14B，约 1.5 亿
     * 步 → 每步 new Vec3 → GC 风暴。long 计算 + 饱和到 int 边界，vec31 不跨符号，
     * 射线步进距离有限。正常坐标原样返回，无行为变化。
     */
    @Redirect(method = "runUpdates", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos safeOffset(BlockPos self, int dx, int dy, int dz) {
        long x = (long) self.getX() + dx;
        long y = (long) self.getY() + dy;
        long z = (long) self.getZ() + dz;
        return new BlockPos(
                x > Integer.MAX_VALUE ? Integer.MAX_VALUE : x < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) x,
                y > Integer.MAX_VALUE ? Integer.MAX_VALUE : y < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) y,
                z > Integer.MAX_VALUE ? Integer.MAX_VALUE : z < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) z);
    }
}
