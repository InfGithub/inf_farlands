package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 活塞 isPushable 的维度高度边界（L265/L276 getMinBuildHeight、
 * L266/L278 getMaxBuildHeight）——负极端 Y 活塞 isPushable 恒 false。
 * javap 验证：isPushable 内 Level.getMinBuildHeight:()I ×2、
 * Level.getMaxBuildHeight:()I ×2（每个 @Redirect 匹配同 target 全部调用）。
 */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    @Redirect(method = "isPushable",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private static int configWorldGenMinY(Level level) {
        return Config.worldGenMinY;
    }

    @Redirect(method = "isPushable",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxBuildHeight()I"))
    private static int configWorldGenMaxY(Level level) {
        return Config.worldGenMaxY;
    }
}
