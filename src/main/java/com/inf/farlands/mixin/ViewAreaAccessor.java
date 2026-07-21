package com.inf.farlands.mixin;

import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ViewArea.class)
public interface ViewAreaAccessor {

    @Accessor
    Level getLevel();

    @Accessor
    int getSectionGridSizeY();

    @Accessor
    SectionRenderDispatcher.RenderSection[] getSections();

    @Invoker("getRenderSectionAt")
    SectionRenderDispatcher.RenderSection callGetRenderSectionAt(BlockPos pos);
}
