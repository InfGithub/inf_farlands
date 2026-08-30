package com.inf.farlands.client.mixin.debug.tool.clamp;

import com.inf.farlands.debug.tool.clamp.ClampMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 客户端钳制模式状态。真实碰撞由 EntityCollisionMixin 处理
 * 加虚拟板——客户端预测 move 也撞到虚拟面，与服务端一致。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin implements ClampMode {

    @Unique
    private boolean clampEnabled;

    @Override
    public boolean isClampEnabled() {
        return clampEnabled;
    }

    @Override
    public void setClampEnabled(boolean enabled) {
        clampEnabled = enabled;
    }
}