package com.inf.farlands.tool.clamp;

/**
 * 钳制模式 per-player 状态接口，由 ServerPlayerMixin 注入到 ServerPlayer。
 * 钳制面 = 玩家当前 section 的下边界平面，像真实方块顶面承接玩家；
 * 按住 shift 暂时忽略。服务端 payload 处理器经本接口 toggle。
 */
public interface ClampMode {
    boolean farlandsIsClampEnabled();

    void farlandsSetClampEnabled(boolean enabled);
}