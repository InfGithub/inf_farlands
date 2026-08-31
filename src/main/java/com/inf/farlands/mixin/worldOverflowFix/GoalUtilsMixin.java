package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.util.WorldBounds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.GoalUtils;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 随机走动/逃跑目标生成的 -64/320 拦截。
 *
 * RandomStrollGoal/AvoidEntityGoal → DefaultRandomPos/LandRandomPos/
 * AirAndWaterRandomPos 的 generateRandomPosTowardDirection 用
 * GoalUtils.isOutsideLimits 过滤目标位置——边界 = LevelReader
 * getMinBuildHeight/getMaxBuildHeight，取值 dimensionType().minY()/height()，
 * overworld 为 -64/320——极端 Y 平台上的实体随机目标 Y ≈ mob Y，被拒绝
 * → goal 无目标 → 不移动。实体目标如攻击/跟随不经过此检查 → 任意高度
 * 正常。
 *
 * 修复：边界 Config 化。
 * mob 参数保留，签名不变，3 个调用者无感。
 */
@Mixin(GoalUtils.class)
public abstract class GoalUtilsMixin {

    @Overwrite
    public static boolean isOutsideLimits(BlockPos pos, PathfinderMob mob) {
        return !WorldBounds.inBuildHeight(pos.getY());
    }
}
