package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 活塞 isPushable 的维度高度边界——负极端 Y 活塞 isPushable 恒 false。
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
