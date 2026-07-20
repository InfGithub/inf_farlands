package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MineshaftStructure.class)
public class MineshaftStructureMixin {

    @Inject(
        method = "findGenerationPoint",
        at = @At("HEAD"),
        cancellable = true
    )
    private void skipIfOutOfBounds(
        Structure.GenerationContext context,
        CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir
    ) {
        int cx = context.chunkPos().x;
        int cz = context.chunkPos().z;
        int max = FarLandsConstants.MAX_CHUNK;
        if (cx > max || cx < -max || cz > max || cz < -max) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
