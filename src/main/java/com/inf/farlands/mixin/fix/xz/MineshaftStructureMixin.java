package com.inf.farlands.mixin.fix.xz;

import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.inf.farlands.FarlandsConstant;

import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;

/**
 * 极端 X/Z 的 Mineshaft 结构起点拦截——findGenerationPoint 源头拒绝，防走廊 box int 溢出。
 */
@Mixin(MineshaftStructure.class)
public class MineshaftStructureMixin {

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void skipOutOfBounds(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        int cx = context.chunkPos().x();
        int cz = context.chunkPos().z();
        if (cx > FarlandsConstant.MINESHAFT_LIMIT_CHUNK || cx < ~FarlandsConstant.MINESHAFT_LIMIT_CHUNK
                || cz > FarlandsConstant.MINESHAFT_LIMIT_CHUNK || cz < ~FarlandsConstant.MINESHAFT_LIMIT_CHUNK) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
