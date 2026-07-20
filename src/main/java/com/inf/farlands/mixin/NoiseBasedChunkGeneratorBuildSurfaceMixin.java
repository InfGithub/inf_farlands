package com.inf.farlands.mixin;

import com.inf.farlands.FarLandsConstants;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorBuildSurfaceMixin {

    @Inject(method = "buildSurface", at = @At("HEAD"), cancellable = true)
    private void skipIfChunkOverflows(
        WorldGenRegion level,
        StructureManager structureManager,
        RandomState randomState,
        ChunkAccess chunk,
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
