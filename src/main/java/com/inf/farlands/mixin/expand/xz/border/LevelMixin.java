package com.inf.farlands.mixin.expand.xz.border;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.inf.farlands.FarlandsConfig;

import net.minecraft.world.level.Level;

@Mixin(Level.class)
public class LevelMixin {
    // 旧版模组用 setter 修改 MAX_LEVEL_SIZE 的值。
    // ! 但如今已移除，因为意识到引用处在编译时内联。

    @ModifyConstant(method = "isInWorldBoundsHorizontal", constant = @Constant(intValue = 30000000))
    private static int maxBlockA(int max) {
        return FarlandsConfig.borderAbsoluteMax;
    }

    @ModifyConstant(method = "isInWorldBoundsHorizontal", constant = @Constant(intValue = -30000000))
    private static int minBlockA(int min) {
        return ~FarlandsConfig.borderAbsoluteMax;
    }

    @ModifyConstant(method = "getHeight", constant = @Constant(intValue = 30000000))
    private static int maxBlockB(int max) {
        return FarlandsConfig.borderAbsoluteMax;
    }

    @ModifyConstant(method = "getHeight", constant = @Constant(intValue = -30000000))
    private static int minBlockB(int min) {
        return ~FarlandsConfig.borderAbsoluteMax;
    }
}
