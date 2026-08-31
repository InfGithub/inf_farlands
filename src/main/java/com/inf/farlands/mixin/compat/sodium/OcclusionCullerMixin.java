package com.inf.farlands.mixin.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium 0.8.13-beta.1 版本绑定，升级即碎。
 *
 * init 用 level.getMinSection()/getMaxSection()，vanilla -4/20，判断玩家
 * sectionY 是否在世界高度内。本 mod 允许玩家在维度外，Config 语义 ±2.14B——
 * 玩家在 y=2.14B 时 sectionY=134M >= 20 → outOfWorld 路径，只渲染 y=19
 * 水平层 → 2.14B 的 RenderSection 永不收集/编译 → setblock 不渲染。
 *
 * init 的高度判断改窗口范围，玩家所在 chunk 的窗口。玩家 = 相机，单机，
 * 与 viewport.getChunkCoord 一致；第三人称/spectator 的相机偏差在窗口 ±17
 * 容差内，可接受。
 *
 * init(RenderSectionVisitor, WriteQueue, Viewport, boolean, int) 的
 * getMinSection/getMaxSection 各 2 处调用 L278/281/283/286，全部包装。
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.occlusion.OcclusionCuller")
public abstract class OcclusionCullerMixin {

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinSection()I"))
    private int wrapGetMinSection(Level instance, Operation<Integer> original) {
        LevelChunk chunk = playerChunk(instance);
        return chunk != null ? chunk.getMinSection() : original.call(instance);
    }

    @WrapOperation(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMaxSection()I"))
    private int wrapGetMaxSection(Level instance, Operation<Integer> original) {
        LevelChunk chunk = playerChunk(instance);
        return chunk != null ? chunk.getMaxSection() : original.call(instance);
    }

    private static LevelChunk playerChunk(Level instance) {
        if (!(instance instanceof ClientLevel level)) {
            return null;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return level.getChunk(
                SectionPos.blockToSectionCoord(player.getBlockX()),
                SectionPos.blockToSectionCoord(player.getBlockZ()));
    }
}
