package com.inf.farlands.mixin.outsideWorld;

import com.inf.farlands.Config;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 六面体边界（outsideWorld）：isInsideCloseToBorder 三维化（墙碰撞参与条件）。
 *
 * vanilla: getDistanceToBorder(entity)（XZ）< d0*2 && isWithinBounds(x, z,
 * d0)——实体靠近
 * Y 地板/天花板时碰撞不参与（Y 无边界）。三维版：min(XZ 距离, Y 距离) < d0*2 且
 * y 在 [minY-d0, maxY+d0]。
 *
 * outside=true → false：XZ+Y 墙碰撞全关（所有实体可穿出）。shape 保持六面体不动。
 */
@Mixin(WorldBorder.class)
public abstract class WorldBorderMixin {

    @Shadow
    public abstract double getDistanceToBorder(double x, double z);

    @Overwrite
    public boolean isInsideCloseToBorder(Entity entity, AABB bounds) {
        if (Config.outside) {
            return false;
        }
        double limit = Config.borderAbsoluteMax - 16.0;
        double d0 = Math.max(Mth.absMax(bounds.getXsize(), bounds.getZsize()), 1.0);
        double dXZ = this.getDistanceToBorder(entity.getX(), entity.getZ());
        double y = entity.getY();
        double dY = Math.min(y + limit, limit - y);
        return Math.min(dXZ, dY) < d0 * 2.0 && y > -limit - d0 && y < limit + d0;
    }
}
