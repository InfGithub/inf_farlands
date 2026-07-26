package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.Constants;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationStub;
import net.minecraft.world.level.levelgen.structure.Structure.GenerationContext;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MineshaftStructure.class)
public class MineshaftStructureMixin {

    @Inject(method = "findGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void skipIfOutOfBounds(
            GenerationContext context,
            CallbackInfoReturnable<Optional<GenerationStub>> cir) {
        int cx = context.chunkPos().x;
        int cz = context.chunkPos().z;
        if (cx > Constants.MAX_CHUNK ||
                cx < ~Constants.MAX_CHUNK ||
                cz > Constants.MAX_CHUNK ||
                cz < ~Constants.MAX_CHUNK) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
