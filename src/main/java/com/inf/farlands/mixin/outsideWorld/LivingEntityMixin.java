package com.inf.farlands.mixin.outsideWorld;

import com.inf.farlands.Config;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 六面体边界：baseTick 越界掉血判定三维化。
 *
 * vanilla: isWithinBounds(boundingBox) XZ 判定——Y 越界不掉血。
 * 三维版：XZ 判定 && 脚部不高于地板 box.minY <= limit && 头部不低于天花板
 * box.maxY >= -limit——玩家站立在地板/天花板下不误判，box 身高超出边界是正常状态，
 * 判定用脚/头而非 box 整体。
 *
 * outside=true → 界内，不掉血。只改 baseTick 调用点，isWithinBounds 本体不动。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @SuppressWarnings("null")
    @Redirect(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;isWithinBounds(Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean checkBoundsIncludingY(WorldBorder worldBorder, AABB box) {
        if (Config.outside) {
            return true;
        }
        double limit = Config.borderAbsoluteMax - 16.0;
        return worldBorder.isWithinBounds(box) && box.minY <= limit && box.maxY >= -limit;
    }
}
