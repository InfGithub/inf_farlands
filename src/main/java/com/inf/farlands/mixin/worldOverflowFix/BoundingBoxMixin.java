package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.util.WorldBounds;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BoundingBox.class)
public class BoundingBoxMixin {

    @Shadow
    private int minX, minY, minZ, maxX, maxY, maxZ;

    @Inject(method = "moved", at = @At("HEAD"), cancellable = true)
    private void safeMoved(
            int x,
            int y,
            int z,
            CallbackInfoReturnable<BoundingBox> cir) {
        long newMinX = (long) this.minX + x;
        long newMaxX = (long) this.maxX + x;
        long newMinZ = (long) this.minZ + z;
        long newMaxZ = (long) this.maxZ + z;
        if (!WorldBounds.inBlock(newMinX) ||
                !WorldBounds.inBlock(newMaxX) ||
                !WorldBounds.inBlock(newMinZ) ||
                !WorldBounds.inBlock(newMaxZ)) {
            cir.setReturnValue((BoundingBox) (Object) this);
            return;
        }
        if (newMinX > newMaxX || newMinZ > newMaxZ) {
            cir.setReturnValue((BoundingBox) (Object) this);
            return;
        }
    }

    /**
     * 命令族：/execute if blocks、/clone、/fill 的 box 循环上界 =
     * boundingbox.maxX/maxY/maxZ 直接比较——命令坐标含 2147483647 时上界 = MAX_VALUE
     * → 递增溢出无限 → 服务端线程冻结。fromCorners 是命令 box 的唯一入口，checkRegions
     * /clone /fillBlocks /fillbiome 全走此方法；此处把 max clamp 到 2147483645，
     * 即 MAX_VALUE−2，该值让直接比较上界 ≤ 2147483645 时递增超界退出，未来 ±1 型
     * max+1 = 2147483646 也安全。min 侧保持原值，但防反序：box 完全越界即 min >
     * clampedMax 时 min 坍缩到 clampedMax → 单点。2147483646+ 是边界外 air，无地形
     * 且玩家不可达 → 裁剪无实际语义损失；正常坐标 box 远小于边界 → 零影响。
     * 已知限制：XSpan int 溢出的巨量有限迭代不在此覆盖。
     */
    @Inject(method = "fromCorners", at = @At("RETURN"), cancellable = true)
    private static void clampFromCorners(Vec3i first, Vec3i second, CallbackInfoReturnable<BoundingBox> cir) {
        BoundingBox box = cir.getReturnValue();
        int newMaxX = Math.min(box.maxX(), 2147483645);
        int newMaxY = Math.min(box.maxY(), 2147483645);
        int newMaxZ = Math.min(box.maxZ(), 2147483645);
        if (newMaxX == box.maxX() && newMaxY == box.maxY() && newMaxZ == box.maxZ()) {
            return;
        }
        cir.setReturnValue(new BoundingBox(
                Math.min(box.minX(), newMaxX),
                Math.min(box.minY(), newMaxY),
                Math.min(box.minZ(), newMaxZ),
                newMaxX,
                newMaxY,
                newMaxZ));
    }
}
