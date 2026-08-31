package com.inf.farlands.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium 0.8.13-beta.1 版本绑定，升级即碎。
 *
 * onSectionAdded 用 level.getSectionIndexFromSectionY，vanilla 索引 y+4，取
 * chunk 的窗口数组 34 section → 系统性错位；改用窗口索引。
 * onChunkAdded/onChunkRemoved 遍历 level.getMinSection()..getMaxSection()
 * 固定 24 个——与窗口 34 个，跟随玩家，不匹配；改用窗口索引后玩家 Y 远离
 * [64,208] 时遍历 level 范围会索引越界。改遍历窗口范围。
 *
 * onSectionAdded(int x, int y, int z) 参数槽：this=0, x=1, y=2, z=3。
 * onChunkAdded/onChunkRemoved(int x, int z) 参数槽：this=0, x=1, z=2。
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager")
public abstract class RenderSectionManagerMixin {

    @WrapOperation(method = "onSectionAdded", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSectionIndexFromSectionY(I)I"))
    private int wrapGetSectionIndex(ClientLevel instance, int y, Operation<Integer> original,
            @Local(argsOnly = true, index = 1) int x,
            @Local(argsOnly = true, index = 3) int z) {
        LevelChunk chunk = instance.getChunk(x, z);
        if (chunk == null) {
            return original.call(instance, y);
        }
        int idx = chunk.getSectionIndexFromSectionY(y);
        int len = chunk.getSections().length;
        if (idx < 0 || idx >= len) {
            // 兜底：onChunkAdded 遍历窗口与 chunk 窗口变化——clamp
            return Math.max(0, Math.min(idx, len - 1));
        }
        return idx;
    }

    @WrapOperation(method = "onChunkAdded", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinSection()I"))
    private int wrapGetMinSection(ClientLevel instance, Operation<Integer> original,
            @Local(argsOnly = true, index = 1) int x,
            @Local(argsOnly = true, index = 2) int z) {
        LevelChunk chunk = instance.getChunk(x, z);
        return chunk != null ? chunk.getMinSection() : original.call(instance);
    }

    @WrapOperation(method = "onChunkAdded", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSection()I"))
    private int wrapGetMaxSection(ClientLevel instance, Operation<Integer> original,
            @Local(argsOnly = true, index = 1) int x,
            @Local(argsOnly = true, index = 2) int z) {
        LevelChunk chunk = instance.getChunk(x, z);
        return chunk != null ? chunk.getMaxSection() : original.call(instance);
    }

    @WrapOperation(method = "onChunkRemoved", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMinSection()I"))
    private int wrapRemovedGetMinSection(ClientLevel instance, Operation<Integer> original,
            @Local(argsOnly = true, index = 1) int x,
            @Local(argsOnly = true, index = 2) int z) {
        LevelChunk chunk = instance.getChunk(x, z);
        return chunk != null ? chunk.getMinSection() : original.call(instance);
    }

    @WrapOperation(method = "onChunkRemoved", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getMaxSection()I"))
    private int wrapRemovedGetMaxSection(ClientLevel instance, Operation<Integer> original,
            @Local(argsOnly = true, index = 1) int x,
            @Local(argsOnly = true, index = 2) int z) {
        LevelChunk chunk = instance.getChunk(x, z);
        return chunk != null ? chunk.getMaxSection() : original.call(instance);
    }
}
