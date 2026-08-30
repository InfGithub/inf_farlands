package com.inf.farlands.client.debug.tool.clamp;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * 钳制模式虚拟面命中的当前标记。
 * EntityPickMixin 在射线穿过虚拟面时设置
 * LevelRenderer 选择框据此放行空气渲染
 */
public final class VirtualHit {
    private static BlockPos current;

    private VirtualHit() {
    }

    public static void set(BlockPos pos) {
        current = pos;
    }

    public static void clear() {
        current = null;
    }

    /** 当前命中 HitResult 是否为虚拟面命中。 */
    public static boolean isCurrent(HitResult hit) {
        return current != null && hit instanceof BlockHitResult bhr && bhr.getBlockPos().equals(current);
    }

    public static boolean isPos(BlockPos pos) {
        return current != null && current.equals(pos);
    }
}