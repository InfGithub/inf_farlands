package com.inf.farlands.client.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/**
 * setBlocksDirty / setBlockDirty 极端坐标死循环修复。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    public abstract void setSectionDirty(int sectionX, int sectionY, int sectionZ);

    @Shadow
    protected abstract void setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean playerChanged);

    /** 坐标 ±1 运算是否触及 int 边界。 */
    @Unique
    private static boolean isDangerous(int v) {
        return v >= FarlandsConstant.MAX_BLOCK - 1 || v <= -FarlandsConstant.MAX_BLOCK;
    }

    @Inject(method = "setBlocksDirty(IIIIII)V", at = @At("HEAD"), cancellable = true)
    private void safeSetBlocksDirty(int x0, int y0, int z0, int x1, int y1, int z1, CallbackInfo ci) {
        if (!isDangerous(x0) && !isDangerous(y0) && !isDangerous(z0)
                && !isDangerous(x1) && !isDangerous(y1) && !isDangerous(z1)) {
            return; // 正常坐标：放行 vanilla 原方法体
        }
        for (long i = (long) z0 - 1; i <= (long) z1 + 1; i++) {
            for (long j = (long) x0 - 1; j <= (long) x1 + 1; j++) {
                for (long k = (long) y0 - 1; k <= (long) y1 + 1; k++) {
                    this.setSectionDirty(
                            SectionPos.blockToSectionCoord((int) j),
                            SectionPos.blockToSectionCoord((int) k),
                            SectionPos.blockToSectionCoord((int) i));
                }
            }
        }
        ci.cancel();
    }

    @Inject(method = "setBlockDirty(Lnet/minecraft/core/BlockPos;Z)V", at = @At("HEAD"), cancellable = true)
    private void safeSetBlockDirty(BlockPos pos, boolean playerChanged, CallbackInfo ci) {
        if (!isDangerous(pos.getX()) && !isDangerous(pos.getY()) && !isDangerous(pos.getZ())) {
            return; // 正常坐标：放行 vanilla 原方法体
        }
        for (long i = (long) pos.getZ() - 1; i <= (long) pos.getZ() + 1; i++) {
            for (long j = (long) pos.getX() - 1; j <= (long) pos.getX() + 1; j++) {
                for (long k = (long) pos.getY() - 1; k <= (long) pos.getY() + 1; k++) {
                    this.setSectionDirty(
                            SectionPos.blockToSectionCoord((int) j),
                            SectionPos.blockToSectionCoord((int) k),
                            SectionPos.blockToSectionCoord((int) i),
                            playerChanged);
                }
            }
        }
        ci.cancel();
    }
}
