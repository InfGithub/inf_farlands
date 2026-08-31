package com.inf.farlands.mixin.worldOverflowFix;

import com.inf.farlands.util.WorldBounds;
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
        if (!WorldBounds.inChunkRange(cx, cz)) {
            ci.cancel();
        }
    }
}
