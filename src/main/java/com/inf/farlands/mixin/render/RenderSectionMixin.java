package com.inf.farlands.mixin.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * Fix hasAllNeighbors() cancelling compilation at view-distance edge.
 * <p>
 * In vanilla, a RenderSection within 24 blocks of the player always passes.
 * Beyond 24 blocks, it checks whether the 4 cardinal neighbor chunks have
 * been loaded on the client. At the view-distance edge the center chunk
 * packet may arrive before neighbor packets — hasAllNeighbors() fails,
 * RebuildTask.cancel() sets dirty=false, and the section is never retried.
 * <p>
 * This mod sends all chunks at FULL status immediately, so neighbor chunks
 * always have block data once their packets arrive. There is no benefit to
 * waiting for neighbors before compiling.
 */
@Mixin(RenderSection.class)
public abstract class RenderSectionMixin {

    @Overwrite
    public boolean hasAllNeighbors() {
        return true;
    }
}
