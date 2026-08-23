package com.inf.farlands.mixin.axisY;

import com.inf.farlands.light.FarLandsLightEngine;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Replace {@code new ThreadedLevelLightEngine} in ChunkMap constructor
 * with {@link FarLandsLightEngine}.
 */
@Mixin(ChunkMap.class)
public class ChunkMapLightEngineMixin {

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/server/level/ThreadedLevelLightEngine"))
    private ThreadedLevelLightEngine redirectNewLightEngine(
            LightChunkGetter lightChunk, ChunkMap chunkMap, boolean skyLight,
            ProcessorMailbox<Runnable> taskMailbox,
            ProcessorHandle<net.minecraft.server.level.ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox) {
        return new FarLandsLightEngine(lightChunk, chunkMap, skyLight, taskMailbox, sorterMailbox);
    }
}
