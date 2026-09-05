package com.inf.farlands.mixin.fix.xz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Vec3i;
import net.minecraft.server.commands.CloneCommands;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * /clone 的 XSpan int 溢出防护。
 */
@Mixin(CloneCommands.class)
public abstract class CloneCommandsMixin {

    @Shadow
    private static Dynamic2CommandExceptionType ERROR_AREA_TOO_LARGE;

    @Redirect(method = "clone", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/structure/BoundingBox;fromCorners(Lnet/minecraft/core/Vec3i;Lnet/minecraft/core/Vec3i;)Lnet/minecraft/world/level/levelgen/structure/BoundingBox;", ordinal = 0))
    private static BoundingBox safeFromCorners(Vec3i first, Vec3i second, CommandSourceStack source)
            throws CommandSyntaxException {
        int limit = source.getLevel().getGameRules().get(GameRules.MAX_BLOCK_MODIFICATIONS);
        if (span(first.getX(), second.getX()) > limit
                || span(first.getY(), second.getY()) > limit
                || span(first.getZ(), second.getZ()) > limit) {
            throw ERROR_AREA_TOO_LARGE.create(limit, maxSpan(first, second));
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
