package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Config;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 信标的维度高度边界。
 *
 * 1. tick 光束上限 `l = getHeight(WORLD_SURFACE, x, z)`——极端 Y 信标
 * 循环条件 `blockpos.Y <= l` 恒 false → 每 tick 空转重置 → 无光束无药水
 * 效果。修复：l = max(地表, min(Config.max, 信标Y+maxCapIter))——极端 Y
 * 光束 maxCapIter 格；地下信标不变；空中信标连带修复。
 * 2. tick / setLevel `lastCheckY = getMinBuildHeight() - 1`、updateBase
 * 基座下限——Config 化。
 *
 * tick 是 static，@Redirect handler 必须 static 且拿不到信标 pos，所以
 * @Inject HEAD 捕获 pos 到 ThreadLocal；下 tick HEAD 覆盖，异常路径残留无害。
 */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {

    @Unique
    private static final ThreadLocal<BlockPos> TICK_POS = new ThreadLocal<>();

    @Inject(method = "tick", at = @At("HEAD"))
    private static void captureTickPos(Level level, BlockPos pos, BlockState state,
            BeaconBlockEntity blockEntity, CallbackInfo ci) {
        TICK_POS.set(pos);
    }

    @SuppressWarnings("null")
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private static int beamCeiling(Level level, Heightmap.Types type, int x, int z) {
        BlockPos pos = TICK_POS.get();
        int y = pos != null ? pos.getY() : Config.worldGenMinY;
        int ceil = (int) Math.min((long) Config.worldGenMaxY, (long) y + (long) Config.maxCapIter);
        return Math.max(level.getHeight(type, x, z), ceil);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private static int configMinYTick(Level level) {
        return Config.worldGenMinY;
    }

    @Redirect(method = "updateBase", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private static int configMinYUpdateBase(Level level) {
        return Config.worldGenMinY;
    }

    @Redirect(method = "setLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private static int configMinYSetLevel(Level level) {
        return Config.worldGenMinY;
    }
}
