package com.inf.farlands.mixin.debug.tool.clamp;

import com.inf.farlands.debug.tool.clamp.ClampMode;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 服务端钳制模式 per-player 状态。真实碰撞由
 * EntityCollisionMixin 处理，它在 collideBoundingBox 加虚拟板——玩家 move 时撞到
 * 当前 section 底平面，onGround/deltaMovement 由物理引擎自然处理。
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin implements ClampMode {

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