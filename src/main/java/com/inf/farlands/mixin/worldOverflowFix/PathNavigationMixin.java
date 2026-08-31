package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.util.WorldBounds;
import com.inf.farlands.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigation.class)
public class PathNavigationMixin {

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void skipCreatePathAtFarlands(
            BlockPos pos,
            int accuracy,
            CallbackInfoReturnable<Object> cir) {
        int x = pos.getX();
        int z = pos.getZ();
        if (!WorldBounds.inBlockXZ(x, z)) {
            cir.setReturnValue(null);
        }
    }

    /**
     * mob 在维度最低高度 -64 以下任何入口寻路全 null。
     *
     * createPath(Set,IZIF) 的 mob.getY() < level.getMinBuildHeight() 检查——Config
     * 允许的负极端 Y -65 ~ -2.14B 下 mob 无法寻路，实体/位置/随机全 null。
     */
    @Redirect(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getMinBuildHeight()I"))
    private static int configWorldGenMinY(Level level) {
        return Config.worldGenMinY;
    }

    /**
     * 钳制 createPath 中的 BlockPos.offset 结果，防止 AI 目标在坐标边界附近
     * int 溢出时 PathNavigationRegion 抛 NegativeArraySizeException。
     */
    @Redirect(method = "createPath(Ljava/util/Set;IZIF)Lnet/minecraft/world/level/pathfinder/Path;", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;offset(III)Lnet/minecraft/core/BlockPos;"))
    private BlockPos clampOffset(BlockPos self, int dx, int dy, int dz) {
        long newX = (long) self.getX() + dx;
        long newZ = (long) self.getZ() + dz;

        newX = WorldBounds.clampBlockCoord(newX);
        newZ = WorldBounds.clampBlockCoord(newZ);

        long newY = (long) self.getY() + dy;
        return new BlockPos((int) newX, (int) newY, (int) newZ);
    }
}
