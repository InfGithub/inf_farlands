package com.inf.farlands.mixin.expand.xyz.pos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.world.level.levelgen.Aquifer;

import com.inf.farlands.util.pos.AquiferPos;
import com.inf.farlands.util.pos.IntBlockPos;
import com.inf.farlands.util.maps.aquifer.AquiferUtil;

/**
 * Aquifer.NoiseBasedAquifer 3int 适配。
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class Aquifer$NoiseBasedAquiferMixin {

    @Shadow
    private Aquifer.FluidStatus[] aquiferCache;

    @Shadow
    private long[] aquiferLocationCache;

    @Shadow
    protected abstract Aquifer.FluidStatus computeFluid(int x, int y, int z);

    // computeSubstance 循环的 3 处坐标解包 -> 侧信道反查
    // 跨 tick 保活

    @Redirect(method = "computeSubstance", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getX(J)I"))
    private static int farlands$getX(long packedPos) {
        return resolveX(packedPos);
    }

    @Redirect(method = "computeSubstance", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getY(J)I"))
    private static int farlands$getY(long packedPos) {
        return resolveY(packedPos);
    }

    @Redirect(method = "computeSubstance", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/BlockPos;getZ(J)I"))
    private static int farlands$getZ(long packedPos) {
        return resolveZ(packedPos);
    }

    private static int resolveX(long packedPos) {
        AquiferPos ap = AquiferUtil.get(packedPos);
        if (ap != null) {
            return ap.x;
        }
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        AquiferUtil.put(packedPos, bp.x, bp.y, bp.z); // 补注册：此后跨 tick 保活
        return bp.x;
    }

    private static int resolveY(long packedPos) {
        AquiferPos ap = AquiferUtil.get(packedPos);
        if (ap != null) {
            return ap.y;
        }
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        AquiferUtil.put(packedPos, bp.x, bp.y, bp.z);
        return bp.y;
    }

    private static int resolveZ(long packedPos) {
        AquiferPos ap = AquiferUtil.get(packedPos);
        if (ap != null) {
            return ap.z;
        }
        IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
        AquiferUtil.put(packedPos, bp.x, bp.y, bp.z);
        return bp.z;
    }

    @Overwrite
    private Aquifer.FluidStatus getAquiferStatus(int index) {
        Aquifer.FluidStatus s = aquiferCache[index];
        if (s != null) {
            return s;
        }
        long packedPos = aquiferLocationCache[index];
        AquiferPos ap = AquiferUtil.get(packedPos);
        int bx, by, bz;
        if (ap != null) {
            bx = ap.x;
            by = ap.y;
            bz = ap.z;
        } else {
            IntBlockPos bp = IntBlockPos.getBlockPos(packedPos);
            bx = bp.x;
            by = bp.y;
            bz = bp.z;
            AquiferUtil.put(packedPos, bx, by, bz); // 补注册：此后跨 tick 保活
        }
        Aquifer.FluidStatus c = computeFluid(bx, by, bz);
        aquiferCache[index] = c;
        return c;
    }
}
