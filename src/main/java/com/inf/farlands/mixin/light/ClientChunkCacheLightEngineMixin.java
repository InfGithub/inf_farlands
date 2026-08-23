package com.inf.farlands.mixin.light;

import com.inf.farlands.light.FarLandsLightEngine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replace {@code new LevelLightEngine} in ClientChunkCache constructor
 * with {@link FarLandsLightEngine}. ClientChunkCache is client-only
 * so this mixin is silently skipped on dedicated servers.
 *
 * <p>{@code onLightUpdate}（LightChunkGetter default）：客户端本地传播
 * （LevelRenderer 每帧 runLightUpdates）后的 affected-section 通知 →
 * 标记渲染重编译——修复"光照数据已更新但视觉不刷新"（跨 section/chunk
 * 人工光视觉暗）。服务端走 ServerChunkCache.onLightUpdate（vanilla）。
 */
@Mixin(ClientChunkCache.class)
public class ClientChunkCacheLightEngineMixin {

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/level/lighting/LevelLightEngine"))
    private LevelLightEngine redirectNewLightEngine(LightChunkGetter chunkSource, boolean blockLight, boolean skyLight) {
        return new FarLandsLightEngine(chunkSource, skyLight);
    }

    @Overwrite
    public void onLightUpdate(LightLayer layer, SectionPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.levelRenderer != null) {
            mc.levelRenderer.setSectionDirty(pos.x(), pos.y(), pos.z());
        }
    }
}
