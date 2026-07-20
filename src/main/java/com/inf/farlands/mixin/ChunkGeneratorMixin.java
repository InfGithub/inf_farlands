package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public class ChunkGeneratorMixin {

    @Inject(
        method = "applyBiomeDecoration",
        at = @At("HEAD"),
        cancellable = true
    )
    private void skipInFarlands(
        WorldGenLevel level,
        ChunkAccess chunk,
        StructureManager structureManager,
        CallbackInfo ci
    ) {
        int cx = chunk.getPos().x;
        int cz = chunk.getPos().z;
        int max = FarLandsConstants.MAX_CHUNK;
        if (cx > max || cx < -max || cz > max || cz < -max) {
            ci.cancel();
        }
    }
}
