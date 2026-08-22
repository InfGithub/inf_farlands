package com.inf.farlands.mixin.worldOverflowFix;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 留档项 #1（XSpan 溢出）修复：/execute if blocks 的体积检查
 * （checkRegions L965 `getXSpan()*getYSpan()*getZSpan()`）在跨度 ≥ 2^31 时
 * int 溢出成负 → "i > 32768" 失效 → 40 亿次有限迭代卡顿。
 *
 * 单轴跨度检查（数学：任一轴跨度 > limit ⇒ 体积 > limit，其他轴 ≥ 1）——
 * 与原版体积检查语义等价。long 算术（|(long)b − a| + 1）不溢出。
 * 原版检查保留（跨度 ≤ limit 时正常体积拒绝走原逻辑）。
 */
@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandMixin {

    @Shadow
    private static Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE;

    @Inject(method = "checkRegions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Z)Ljava/util/OptionalInt;", at = @At("HEAD"), cancellable = true)
    private static void rejectHugeSpan(ServerLevel level, BlockPos begin, BlockPos end, BlockPos destination,
            boolean isMasked, CallbackInfoReturnable<OptionalInt> cir) throws CommandSyntaxException {
        if (span(begin.getX(), end.getX()) > 32768
                || span(begin.getY(), end.getY()) > 32768
                || span(begin.getZ(), end.getZ()) > 32768) {
            throw ERROR_AREA_TOO_LARGE.create(32768, (int) Math.min(maxSpan(begin, end), Integer.MAX_VALUE));
        }
    }

    private static long span(int a, int b) {
        return Math.abs((long) b - a) + 1;
    }

    private static long maxSpan(BlockPos a, BlockPos b) {
        return Math.max(span(a.getX(), b.getX()),
                Math.max(span(a.getY(), b.getY()), span(a.getZ(), b.getZ())));
    }
}
