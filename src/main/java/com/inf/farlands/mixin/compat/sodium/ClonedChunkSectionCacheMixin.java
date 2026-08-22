package com.inf.farlands.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium 0.8.13-beta.1 版本绑定（升级即碎）。
 *
 * 冲突 A1+B（ClonedChunkSectionCache.clone）：
 * - B：level.isOutsideBuildHeight 被本 mod 覆盖为 Config 语义（±2.14B）
 * → 维度外 section（y=-5）不再拦截 → 取 windowSections[-1] → CTD。
 * 改为窗口范围检查：窗口外 = 空气（镜像 vanilla 维度外语义，
 * ClonedChunkSection 的 null section 走 unpackBlockData 的 EMPTY 路径）。
 * - A1：level.getSectionIndexFromSectionY（vanilla 索引 y+4）取窗口数组
 * （34 section）→ 系统性错位。改用 chunk.getSectionIndexFromSectionY
 * （本 mod LevelChunk 分支 = 窗口索引）。
 *
 * clone(int x, int y, int z) 参数槽：this=0, x=1, y=2, z=3。
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.cloned.ClonedChunkSectionCache")
public abstract class ClonedChunkSectionCacheMixin {

    @WrapOperation(method = "clone", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isOutsideBuildHeight(I)Z"))
    private boolean wrapIsOutsideBuildHeight(Level instance, int y, Operation<Boolean> original,
            @Local(argsOnly = true, index = 1) int x,
            @Local(argsOnly = true, index = 3) int z) {
        // 调用点是 isOutsideBuildHeight(sectionToBlockCoord(y)) = y<<4——handler 的 y 是
        // blockY，
        // 必须 >>4 转回 sectionY（16 的倍数，算术右移无精度损失），否则窗口检查用错单位：
        // 窗口内 section 被误拦截（变空气，只渲染 y≈0）+ 窗口外 section 漏拦截（A1 越界 CTD）。
        int sy = y >> 4;
        LevelChunk chunk = instance.getChunk(x, z);
        if (chunk == null) {
            return original.call(instance, y);
        }
        int idx = chunk.getSectionIndexFromSectionY(sy);
        boolean out = idx < 0 || idx >= chunk.getSections().length;
        return out;
    }

    @WrapOperation(method = "clone", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSectionIndexFromSectionY(I)I"))
    private int wrapGetSectionIndex(Level instance, int y, Operation<Integer> original,
            @Local(argsOnly = true, index = 1) int x,
            @Local(argsOnly = true, index = 3) int z) {
        LevelChunk chunk = instance.getChunk(x, z);
        if (chunk == null) {
            return original.call(instance, y);
        }
        int idx = chunk.getSectionIndexFromSectionY(y);
        int len = chunk.getSections().length;
        if (idx < 0 || idx >= len) {
            // 兜底：B 放行后窗口变化（理论竞态）——clamp 防越界，错位一帧可接受
            return Math.max(0, Math.min(idx, len - 1));
        }
        return idx;
    }
}
