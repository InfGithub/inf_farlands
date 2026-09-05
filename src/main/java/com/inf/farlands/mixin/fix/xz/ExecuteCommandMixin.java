package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;

import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.server.commands.ExecuteCommand;
import net.minecraft.server.level.ServerLevel;

/**
 * /execute if blocks 的体积检查 checkRegions 的跨度溢出防护。
 */
@Mixin(ExecuteCommand.class)
public abstract class ExecuteCommandMixin {

    @Shadow
    private static Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE;

    @Inject(method = "checkRegions(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Z)Ljava/util/OptionalInt;", at = @At("HEAD"), cancellable = true)
    private static void rejectHugeSpan(ServerLevel level, BlockPos begin, BlockPos end, BlockPos destination,
            boolean isMasked, CallbackInfoReturnable<OptionalInt> cir) throws CommandSyntaxException {
        if (span(begin.getX(), end.getX()) > 32768L
                || span(begin.getY(), end.getY()) > 32768L
                || span(begin.getZ(), end.getZ()) > 32768L) {
            throw ERROR_AREA_TOO_LARGE.create(32768, maxSpan(begin, end));
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
