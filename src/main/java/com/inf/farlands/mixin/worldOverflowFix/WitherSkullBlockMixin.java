package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherSkullBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 凋灵召唤的 -64 高度拦截——负极端 Y 无法召唤。
 */
@Mixin(WitherSkullBlock.class)
public abstract class WitherSkullBlockMixin {

    @Redirect(method = {
            "checkSpawn(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/SkullBlockEntity;)V",
            "canSpawnMob(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/ItemStack;)Z" },
              at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private static int configWorldGenMinY(Level level) {
        return Config.worldGenMinY;
    }
}
