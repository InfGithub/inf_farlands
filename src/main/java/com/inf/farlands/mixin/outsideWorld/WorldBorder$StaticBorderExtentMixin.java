package com.inf.farlands.mixin.outsideWorld;

import com.inf.farlands.Config;

import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 六面体边界：StaticBorderExtent 碰撞 shape 的 Y 有限化。
 *
 * vanilla updateBox: Shapes.box(minX, -Infinity, minZ, maxX, +Infinity,
 * maxZ)——Y 无边界，
 * 玩家可穿过地板/天花板。改为 Y = ±(borderAbsoluteMax - 16)，与 XZ 墙
 * absoluteMaxSize clamp 值 2147483631 对齐 → 六面体闭合，地板/天花板有物理碰撞。
 *
 * outside=true 时碰撞关闭不走本类，shape 恒为六面体，由
 * WorldBorderMixin.isInsideCloseToBorder 返回 false 关闭，避免 ±∞ AABB
 * 碰撞计算风险。
 */
@Mixin(targets = "net.minecraft.world.level.border.WorldBorder$StaticBorderExtent")
public abstract class WorldBorder$StaticBorderExtentMixin {

    @Redirect(method = "updateBox", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/shapes/Shapes;box(DDDDDD)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private static VoxelShape boxWithYBounds(double minX, double minY, double minZ, double maxX, double maxY,
            double maxZ) {
        double limit = Config.borderAbsoluteMax - 16.0;
        return Shapes.box(minX, -limit, minZ, maxX, limit, maxZ);
    }
}
