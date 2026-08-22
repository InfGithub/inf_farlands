package com.inf.farlands.mixin.worldOverflowFix;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.datafixers.util.Either;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 留档项 #1 修复：/fillbiome 的体积检查（fill L124）同 XSpan int 溢出问题。
 *
 * 6 参重载（L113）精确描述符（#19 教训：method 名字匹配全部重载会错注入）——
 * 5 参重载（L108）内部委托 6 参，注入 6 参版即覆盖全部。
 * from/to 是 quantize 前原始坐标——阈值（单轴跨度 > GameRules limit）不受
 * quantize ±15 偏差影响（超 int 场景）。非 void 方法 → CallbackInfoReturnable
 * （#20/#24 教训）。
 */
@Mixin(FillBiomeCommand.class)
public abstract class FillBiomeCommandMixin {

    @Shadow
    private static Dynamic2CommandExceptionType ERROR_VOLUME_TOO_LARGE;

    @SuppressWarnings({ "null" })
    @Inject(method = "fill(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Holder;Ljava/util/function/Predicate;Ljava/util/function/Consumer;)Lcom/mojang/datafixers/util/Either;", at = @At("HEAD"), cancellable = true)
    private static void rejectHugeSpan(ServerLevel level, BlockPos from, BlockPos to,
            Holder<Biome> biome, Predicate<Holder<Biome>> filter,
            Consumer<Supplier<Component>> messageOutput,
            CallbackInfoReturnable<Either<Integer, CommandSyntaxException>> cir) {
        long limit = level.getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        if (span(from.getX(), to.getX()) > limit
                || span(from.getY(), to.getY()) > limit
                || span(from.getZ(), to.getZ()) > limit) {
            cir.setReturnValue(Either.right(ERROR_VOLUME_TOO_LARGE.create(limit,
                    (int) Math.min(maxSpan(from, to), Integer.MAX_VALUE))));
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
