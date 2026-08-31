package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 结构方块 detectSize 的扫描范围是 Y 全高 -64..319——极端 Y
 * 结构方块超出维度范围时 corner 检测不到 → detectSize 失败。且 Config
 * 化全高 ±2.14B 会让 getRelatedCorners 扫 161×161×4.28B → 爆炸。
 *
 * 修复：扫描范围 = 结构位置 Y ±80，与 XZ ±80 对称：
 *   [max(Config.worldGenMinY, 结构Y-80), min(Config.worldGenMaxY, 结构Y+80)]
 * ±MAX_BLOCK ± 80 超 int。
 * 标注：corner 超出结构 Y±80 的正常 Y 超高结构漏检——罕见，可接受。
 */
@Mixin(StructureBlockEntity.class)
public abstract class StructureBlockEntityMixin {

    @Redirect(method = "detectSize",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private int minScanY(Level level) {
        return (int) Math.max((long) Config.worldGenMinY,
                (long) ((BlockEntity) (Object) this).getBlockPos().getY() - 80L);
    }

    @Redirect(method = "detectSize",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxBuildHeight()I"))
    private int maxScanY(Level level) {
        return (int) Math.min((long) Config.worldGenMaxY,
                (long) ((BlockEntity) (Object) this).getBlockPos().getY() + 80L);
    }
}
