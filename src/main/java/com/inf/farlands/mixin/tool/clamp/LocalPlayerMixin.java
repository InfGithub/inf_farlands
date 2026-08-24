package com.inf.farlands.mixin.tool.clamp;

import com.inf.farlands.tool.clamp.ClampMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 钳制模式状态（客户端）。真实碰撞由 EntityCollisionMixin（collideBoundingBox
 * 加虚拟板）处理——客户端预测 move 也撞到虚拟面，与服务端一致。
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin implements ClampMode {

    @Unique
    private boolean farlandsClampEnabled;

    @Override
    public boolean farlandsIsClampEnabled() {
        return farlandsClampEnabled;
    }

    @Override
    public void farlandsSetClampEnabled(boolean enabled) {
        farlandsClampEnabled = enabled;
    }
}