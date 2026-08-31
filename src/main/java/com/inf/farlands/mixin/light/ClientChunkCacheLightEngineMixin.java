package com.inf.farlands.mixin.light;

import com.inf.farlands.light.FarLandsLightEngine;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 将 ClientChunkCache 构造中的 {@code new LevelLightEngine}
 * 替换为 {@link FarLandsLightEngine}。ClientChunkCache 仅客户端使用，
 * 本 mixin 在专用服务器上静默跳过。
 *
 * <p>onLightUpdate 不再 @Overwrite：vanilla ClientChunkCache.onLightUpdate
 * 已是 setSectionDirty（反编译 L178-180），我们的引擎经 chunkSource.onLightUpdate
 * 虚分派即到达，无需替换；保留 vanilla 方法使 flywheel 的
 * ClientChunkCacheMixin @Inject HEAD 能注入（@Overwrite 会打 MixinMerged
 * 标记 → flywheel 注入 0 目标 → 启动 CTD，Create 兼容 A2）。
 */
@Mixin(ClientChunkCache.class)
public class ClientChunkCacheLightEngineMixin {

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/world/level/lighting/LevelLightEngine"))
    private LevelLightEngine redirectNewLightEngine(LightChunkGetter chunkSource, boolean blockLight, boolean skyLight) {
        return new FarLandsLightEngine(chunkSource, skyLight);
    }
}
