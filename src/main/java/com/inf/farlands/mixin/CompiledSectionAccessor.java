package com.inf.farlands.mixin;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(SectionRenderDispatcher.CompiledSection.class)
public interface CompiledSectionAccessor {

    @Accessor
    Set<RenderType> getHasBlocks();
}
