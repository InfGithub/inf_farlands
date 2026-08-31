package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.VineBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 藤蔓随机刻生长的维度高度边界：向上 getMaxBuildHeight / 向下
 * getMinBuildHeight——负极端 Y 藤蔓不生长。
 */
@Mixin(VineBlock.class)
public abstract class VineBlockMixin {

    @Redirect(method = "randomTick",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMaxBuildHeight()I"))
    private static int configWorldGenMaxY(ServerLevel level) {
        return Config.worldGenMaxY;
    }

    @Redirect(method = "randomTick",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMinBuildHeight()I"))
    private static int configWorldGenMinY(ServerLevel level) {
        return Config.worldGenMinY;
    }
}
