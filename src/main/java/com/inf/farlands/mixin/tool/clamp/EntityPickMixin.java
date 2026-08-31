package com.inf.farlands.mixin.tool.clamp;

import com.inf.farlands.tool.clamp.ClampMode;
import com.inf.farlands.tool.clamp.FarLandsVirtualHit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 钳制模式虚拟面射线命中：开启且未按 shift 时，射线向下穿过当前 section 底处的虚拟面，
 * 返回指向面下格子的虚拟 BlockHitResult，direction 为 UP，使放置落到面上方。
 * 同时记录虚拟命中位置供选择框识别。
 */
@Mixin(Entity.class)
public class EntityPickMixin {

    @Inject(method = "pick", at = @At("RETURN"), cancellable = true)
    private void farlandsVirtualPick(double hitDistance, float partialTicks, boolean hitFluids,
            CallbackInfoReturnable<HitResult> cir) {
        Entity self = (Entity) (Object) this;
        FarLandsVirtualHit.clear();
        // 真实方块优先：原命中 BLOCK 即保留，不虚拟命中，因为 clip 只命中非空气。
        // 注意不能用 instanceof BlockHitResult——miss 也是 BlockHitResult，type 为 MISS，
        // 会把空气 miss 误判为真实方块而跳过虚拟命中。
        if (cir.getReturnValue() instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            return;
        }
        if (self instanceof ClampMode clamp && clamp.farlandsIsClampEnabled() && !self.isShiftKeyDown()) {
            Vec3 eye = self.getEyePosition(partialTicks);
            Vec3 dir = self.getViewVector(partialTicks);
            double floorY = Math.floor(self.getY() / 16.0) * 16.0;
            if (dir.y < 0 && eye.y > floorY) {
                double t = (floorY - eye.y) / dir.y;
                if (t >= 0 && t <= hitDistance) {
                    double ix = eye.x + dir.x * t;
                    double iz = eye.z + dir.z * t;
                    int bx = Mth.floor(ix);
                    int bz = Mth.floor(iz);
                    BlockPos virtual = new BlockPos(bx, (int) floorY - 1, bz);
                    FarLandsVirtualHit.set(virtual);
                    cir.setReturnValue(new BlockHitResult(new Vec3(ix, floorY, iz), Direction.UP, virtual, false));
                }
            }
        }
    }
}