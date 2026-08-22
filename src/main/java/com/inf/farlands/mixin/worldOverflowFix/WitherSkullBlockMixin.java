package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherSkullBlock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 凋灵召唤的 -64 高度拦截（checkSpawn / canSpawnMob）——负极端 Y 无法召唤
 * （"维度高度边界 Config 化"系列）。
 *
 * method 必须写精确描述符：名字 "checkSpawn" 会同时匹配 2 参转发重载（无
 * getMinBuildHeight 调用 = 0 target）→ 注入器整体未生效（2026-08-05 日志实锤：
 * WS-O 日志显示 O4 缺失、y 检查用原始 -64 拦截；@Inject 精确描述符生效作对照）。
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
