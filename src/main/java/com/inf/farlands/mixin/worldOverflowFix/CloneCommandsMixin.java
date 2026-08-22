package com.inf.farlands.mixin.worldOverflowFix;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Vec3i;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 留档项 #1 修复：/clone 的体积检查（L238）同 XSpan int 溢出问题。
 *
 * clone 的 @Inject HEAD 不可行（参数含 package-private 内部类
 * DimensionAndPosition/Predicate/Mode）——改 @Redirect fromCorners 调用点
 * （L229，handler 只需 Vec3i×2 + 外层方法参数 CommandSourceStack，绕开内部类）。
 * 超限抛 ERROR_AREA_TOO_LARGE（传播到 clone 的 throws CommandSyntaxException）。
 * 单轴跨度检查（轴跨度 > limit ⇒ 体积 > limit）与原版体积检查语义等价。
 */
@Mixin(CloneCommands.class)
public abstract class CloneCommandsMixin {

    @Shadow
    private static Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE;

    @SuppressWarnings({ "null" })
    @Redirect(method = "clone", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;fromCorners(Lnet/minecraft/core/Vec3i;Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/level/levelgen/structure/BoundingBox;"))
    private static BoundingBox safeFromCorners(Vec3i first, Vec3i second, CommandSourceStack source)
            throws CommandSyntaxException {
        int limit = source.getLevel().getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        if (span(first.getX(), second.getX()) > limit
                || span(first.getY(), second.getY()) > limit
                || span(first.getZ(), second.getZ()) > limit) {
            throw ERROR_AREA_TOO_LARGE.create(limit, (int) Math.min(maxSpan(first, second), Integer.MAX_VALUE));
        }
        return BoundingBox.fromCorners(first, second);
    }

    private static long span(int a, int b) {
        return Math.abs((long) b - a) + 1;
    }

    private static long maxSpan(Vec3i a, Vec3i b) {
        return Math.max(span(a.getX(), b.getX()),
                Math.max(span(a.getY(), b.getY()), span(a.getZ(), b.getZ())));
    }
}
