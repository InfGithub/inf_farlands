package com.inf.farlands.mixin.tool.clamp;

import com.inf.farlands.tool.clamp.ClampMode;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 钳制模式 per-player 状态（服务端）。真实碰撞由
 * EntityCollisionMixin（collideBoundingBox 加虚拟板）处理——玩家 move 时撞到
 * 当前 section 底平面，onGround/deltaMovement 由物理引擎自然处理。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements ClampMode {

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