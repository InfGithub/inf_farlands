package com.inf.farlands.mixin.worldBorder;

import com.inf.farlands.Config;

import net.minecraft.server.dedicated.DedicatedServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * DedicatedServer override 了 MinecraftServer.getAbsoluteMaxWorldSize，读
 * server.properties 的 max-world-size，DedicatedServerProperties 还 clamp 到
 * 29999984——MinecraftServerMixin 对基类的 @Overwrite 被子类 override 遮蔽
 * → runServer 世界边界实际 ±29999984 → 2.14B 右键放置被 mayInteract 静默拒绝。
 * 单机集成服务器走基类，无此 override → 正常——正负/环境不对称来源。
 */
@Mixin(DedicatedServer.class)
public abstract class DedicatedServerMixin {

    @Overwrite
    public int getAbsoluteMaxWorldSize() {
        return Config.borderAbsoluteMax - 16;
    }
}
