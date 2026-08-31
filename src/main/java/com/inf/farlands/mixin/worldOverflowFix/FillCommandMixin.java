package com.inf.farlands.mixin.worldOverflowFix;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * /fill 的体积检查同 XSpan int 溢出问题。
 *
 * fillBlocks 的 @Inject HEAD 不可行，参数含 package-private 内部类 Mode——
 * 改 @Redirect GameRules.getInt 调用点；source/area 是 fillBlocks 第 1/2 参数，
 * 连续且跳过 Mode/BlockInput。
 *
 * 注意：不能 return 0 让 "i > 0" 拒绝——getXSpan() 对跨度 ≥ 2^31 溢出成负值，
 * "i > 0" 恒 false 反而放行。超限直接抛 ERROR_AREA_TOO_LARGE，传播到
 * fillBlocks 的 throws CommandSyntaxException，与 ExecuteCommand/
 * CloneCommands 一致。单轴跨度检查：轴跨度 > limit 则 体积 > limit，与原版
 * 体积检查语义等价；正常路径返回真实 limit 走原检查。
 */
@Mixin(FillCommand.class)
public abstract class FillCommandMixin {

    @Shadow
    private static Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE;

    @SuppressWarnings({ "null" })
    @Redirect(method = "fillBlocks", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/GameRules;getInt(Lnet/minecraft/world/level/GameRules$Key;)I"))
    private static int safeGetInt(GameRules rules, GameRules.Key<GameRules.IntegerValue> key,
            CommandSourceStack source, BoundingBox area) throws CommandSyntaxException {
        int limit = rules.getInt(key);
        if (span(area.minX(), area.maxX()) > limit
                || span(area.minY(), area.maxY()) > limit
                || span(area.minZ(), area.maxZ()) > limit) {
            throw ERROR_AREA_TOO_LARGE.create(limit, (int) Math.min(maxSpan(area), Integer.MAX_VALUE));
        }
        return limit;
    }

    private static long span(int a, int b) {
        return Math.abs((long) b - a) + 1;
    }

    private static long maxSpan(BoundingBox box) {
        return Math.max(span(box.minX(), box.maxX()),
                Math.max(span(box.minY(), box.maxY()), span(box.minZ(), box.maxZ())));
    }
}
