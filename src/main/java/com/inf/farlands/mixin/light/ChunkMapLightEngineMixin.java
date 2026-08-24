package com.inf.farlands.mixin.light;

import com.inf.farlands.light.FarLandsLightEngine;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTaskPriorityQueueSorter;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.thread.ProcessorHandle;
import net.minecraft.util.thread.ProcessorMailbox;
import net.minecraft.world.level.chunk.LightChunkGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 将 ChunkMap 构造中的 {@code new ThreadedLevelLightEngine}
 * 替换为 {@link FarLandsLightEngine}。
 */
@Mixin(ChunkMap.class)
public class ChunkMapLightEngineMixin {

    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/server/level/ThreadedLevelLightEngine"))
    private ThreadedLevelLightEngine redirectNewLightEngine(
            LightChunkGetter lightChunk, ChunkMap chunkMap, boolean skyLight,
            ProcessorMailbox<Runnable> taskMailbox,
            ProcessorHandle<ChunkTaskPriorityQueueSorter.Message<Runnable>> sorterMailbox) {
        return new FarLandsLightEngine(lightChunk, chunkMap, skyLight, taskMailbox, sorterMailbox);
    }
}
