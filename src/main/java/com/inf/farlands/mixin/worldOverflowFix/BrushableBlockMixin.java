package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BrushableBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 考古方块（沙砾）tick 的 -64 高度检查（L94）——负极端 Y 无法下落。
 * javap 验证：tick 内 ServerLevel.getMinBuildHeight:()I 1 处。
 */
@Mixin(BrushableBlock.class)
public abstract class BrushableBlockMixin {

    @Redirect(method = "tick",
              at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getMinBuildHeight()I"))
    private static int configWorldGenMinY(ServerLevel level) {
        return Config.worldGenMinY;
    }
}
