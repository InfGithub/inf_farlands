package com.inf.farlands.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium 0.8.13-beta.1 版本绑定（升级即碎）。
 *
 * 冲突 A3（LevelSlice.prepare）：level.getSectionIndexFromSectionY
 * （vanilla 索引 y+4）取 chunk 的窗口数组（34 section）→ 系统性错位。
 * 改用 chunk.getSectionIndexFromSectionY（窗口索引）。
 *
 * prepare 只被编译任务调用（createRebuildTask），RenderSection 的 y 在
 * 窗口内（C 修复后）→ 窗口索引安全，无需越界防御。
 *
 * prepare(Level, SectionPos, ClonedChunkSectionCache) 参数槽（static）：
 * level=0, pos=1, cache=2。
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
public abstract class LevelSliceMixin {

    @WrapOperation(method = "prepare", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getSectionIndexFromSectionY(I)I"))
    private static int wrapGetSectionIndex(Level instance, int y, Operation<Integer> original,
            @Local(argsOnly = true, index = 1) SectionPos pos) {
        LevelChunk chunk = instance.getChunk(pos.getX(), pos.getZ());
        if (chunk == null) {
            return original.call(instance, y);
        }
        int idx = chunk.getSectionIndexFromSectionY(y);
        int len = chunk.getSections().length;
        if (idx < 0 || idx >= len) {
            // 兜底：过时 RenderSection（窗口滑动后）编译——clamp 防越界，错位一次可接受
            return Math.max(0, Math.min(idx, len - 1));
        }
        return idx;
    }
}
