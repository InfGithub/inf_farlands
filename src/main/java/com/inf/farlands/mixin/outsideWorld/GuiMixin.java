package com.inf.farlands.mixin.outsideWorld;

import com.inf.farlands.Config;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 六面体边界：renderVignette 的边界距离三维化。
 *
 * vanilla: getDistanceToBorder(entity) XZ 距离——靠近 Y 地板/天花板不泛红。
 * 三维版：min(XZ 距离, Y 距离)——六个面统一。
 *
 * outside=true → 远距离，不泛红。只改 renderVignette 调用点，getDistanceToBorder 本体不动。
 */
@Mixin(Gui.class)
public abstract class GuiMixin {

    @Redirect(method = "renderVignette", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;getDistanceToBorder(Lnet/minecraft/world/entity/Entity;)D"))
    private double borderDistanceIncludingY(WorldBorder worldBorder, Entity entity) {
        if (Config.outside) {
            return Double.MAX_VALUE;
        }
        double limit = Config.borderAbsoluteMax - 16.0;
        double dXZ = worldBorder.getDistanceToBorder(entity.getX(), entity.getZ());
        double y = entity.getY();
        double dY = Math.min(y + limit, limit - y);
        return Math.min(dXZ, dY);
    }
}
