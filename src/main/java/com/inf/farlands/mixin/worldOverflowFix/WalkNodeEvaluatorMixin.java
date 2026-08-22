package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import java.lang.reflect.Field;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 极端 Y 空中/水下 mob 的寻路扫描 -64 下限（note.md #16 残留）。
 *
 * getStart（L72）：空中 mob 向下扫到 getMinBuildHeight()（-64）找支撑方块——
 * 极端 Y 空中 mob（掉落/击飞/tp 未落地）扫 2.14B 次 getBlockState（每 16 格
 * 懒创建 section → OOM/冻结）→ 撞 -64 基岩 → 起点错位到 -63。
 * tryFindFirstNonWaterBelow（L330）：水下 mob 向下扫同样问题（极端 Y 放置水）。
 * getPathTypeStatic（L452）：`j >= getMinBuildHeight() + 1`——-64 以下节点直接
 * OPEN 跳过下方检查——负极端 Y 节点本应检查下方（放置方块），正确性错误。
 *
 * 修复：
 * 1. @Overwrite getStart（vanilla L53-101 逐行，javap 150 指令对照）——空中
 * 扫描下限 → max(Config.worldGenMinY, mobY - maxCapIter)（long 防溢出）；找不到
 * 支撑 → 起点 = mob 实际位置（A* 自然处理，不冻结；平台旁掉落起点正确）
 * 2. @Redirect tryFindFirstNonWaterBelow → 同上限（水柱 maxCapIter 格内必有底）
 * 3. @Redirect getPathTypeStatic(PathfindingContext,…) → Config.worldGenMinY
 * （j >= Config+1，正常 Y 零回归）
 *
 * 继承字段 mob/currentContext 用反射（@Shadow 不解析继承成员——bugs.md 教训）；
 * canFloat() 是 NodeEvaluator 的 public getter（javap 确认）→ cast 直接调用；
 * getStartNode/canStartAt 是 WalkNodeEvaluator 自身 protected 方法 → @Shadow。
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin {

    private static final int SCAN_LIMIT = Config.maxCapIter;

    private static final Field F_MOB;
    private static final Field F_CURRENT_CONTEXT;

    static {
        try {
            F_MOB = NodeEvaluator.class.getDeclaredField("mob");
            F_MOB.setAccessible(true);
            F_CURRENT_CONTEXT = NodeEvaluator.class.getDeclaredField("currentContext");
            F_CURRENT_CONTEXT.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private Mob refMob() {
        try {
            return (Mob) F_MOB.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private PathfindingContext refContext() {
        try {
            return (PathfindingContext) F_CURRENT_CONTEXT.get(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Shadow
    protected abstract Node getStartNode(BlockPos pos);

    @Shadow
    protected abstract boolean canStartAt(BlockPos pos);

    @SuppressWarnings("null")
    @Overwrite
    public Node getStart() {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();
        Mob mob = refMob();
        PathfindingContext ctx = refContext();
        int i = mob.getBlockY();
        BlockState blockstate = ctx.getBlockState(blockpos$mutableblockpos.set(mob.getX(), (double) i, mob.getZ()));
        if (!mob.canStandOnFluid(blockstate.getFluidState())) {
            if (((NodeEvaluator) (Object) this).canFloat() && mob.isInWater()) {
                while (true) {
                    if (!blockstate.is(Blocks.WATER) && blockstate.getFluidState() != Fluids.WATER.getSource(false)) {
                        i--;
                        break;
                    }

                    blockstate = ctx
                            .getBlockState(blockpos$mutableblockpos.set(mob.getX(), (double) (++i), mob.getZ()));
                }
            } else if (mob.onGround()) {
                i = Mth.floor(mob.getY() + 0.5);
            } else {
                blockpos$mutableblockpos.set(mob.getX(), mob.getY() + 1.0, mob.getZ());
                int floorLimit = (int) Math.max((long) Config.worldGenMinY, (long) mob.getBlockY() - SCAN_LIMIT);
                boolean foundGround = false;

                while (blockpos$mutableblockpos.getY() > floorLimit) {
                    i = blockpos$mutableblockpos.getY();
                    blockpos$mutableblockpos.setY(blockpos$mutableblockpos.getY() - 1);
                    BlockState blockstate1 = ctx.getBlockState(blockpos$mutableblockpos);
                    if (!blockstate1.isAir() && !blockstate1.isPathfindable(PathComputationType.LAND)) {
                        foundGround = true;
                        break;
                    }
                }

                // 扫描区间内无支撑（极端 Y 纯空气）→ 起点 = mob 实际位置（A* 自然处理）
                if (!foundGround) {
                    i = mob.getBlockY();
                }
            }
        } else {
            while (mob.canStandOnFluid(blockstate.getFluidState())) {
                blockstate = ctx.getBlockState(blockpos$mutableblockpos.set(mob.getX(), (double) (++i), mob.getZ()));
            }

            i--;
        }

        BlockPos blockpos = mob.blockPosition();
        if (!this.canStartAt(blockpos$mutableblockpos.set(blockpos.getX(), i, blockpos.getZ()))) {
            AABB aabb = mob.getBoundingBox();
            if (this.canStartAt(blockpos$mutableblockpos.set(aabb.minX, (double) i, aabb.minZ))
                    || this.canStartAt(blockpos$mutableblockpos.set(aabb.minX, (double) i, aabb.maxZ))
                    || this.canStartAt(blockpos$mutableblockpos.set(aabb.maxX, (double) i, aabb.minZ))
                    || this.canStartAt(blockpos$mutableblockpos.set(aabb.maxX, (double) i, aabb.maxZ))) {
                return this.getStartNode(blockpos$mutableblockpos);
            }
        }

        return this.getStartNode(new BlockPos(blockpos.getX(), i, blockpos.getZ()));
    }

    @Redirect(method = "tryFindFirstNonWaterBelow", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private int nonWaterFloorLimit(Level level) {
        return (int) Math.max((long) Config.worldGenMinY, (long) refMob().getY() - SCAN_LIMIT);
    }

    @Redirect(method = "getPathTypeStatic(Lnet/minecraft/world/level/pathfinder/PathfindingContext;Lnet/minecraft/core/BlockPos$MutableBlockPos;)Lnet/minecraft/world/level/pathfinder/PathType;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/CollisionGetter;getMinBuildHeight()I"))
    private static int configWorldGenMinY(CollisionGetter level) {
        return Config.worldGenMinY;
    }
}
