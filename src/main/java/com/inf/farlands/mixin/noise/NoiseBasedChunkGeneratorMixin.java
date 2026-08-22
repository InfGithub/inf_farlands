package com.inf.farlands.mixin.noise;

import com.inf.farlands.terrain.BetaTerrain;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.core.Holder;

@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {

    @Shadow
    private Holder<NoiseGeneratorSettings> settings;

    @Inject(method = "doFill", at = @At("HEAD"))
    private void captureChunk(Blender blender, StructureManager sm, RandomState rs, ChunkAccess chunk, int minCellY,
            int cellCountY, CallbackInfoReturnable<ChunkAccess> cir) {
        BetaTerrain.setCurrentChunk(chunk);
    }
}
