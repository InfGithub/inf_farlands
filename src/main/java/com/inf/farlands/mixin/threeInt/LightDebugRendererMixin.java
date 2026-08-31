package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.util.IntSectionPos;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.client.renderer.debug.LightDebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LightDebugRenderer.class)
public class LightDebugRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @SuppressWarnings("null")
    @Overwrite
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            double camX,
            double camY,
            double camZ) {

        Level level = this.minecraft.level;
        BlockPos blockpos = BlockPos.containing(camX, camY, camZ);
        LongSet longset = new LongOpenHashSet();

        for (BlockPos pos : BlockPos.betweenClosed(blockpos.offset(-10, -10, -10), blockpos.offset(10, 10, 10))) {
            int light = level.getBrightness(LightLayer.SKY, pos);
            long sec = SectionPos.blockToSection(pos.asLong());

            if (longset.add(sec)) {
                IntSectionPos sp = IntSectionPos.getSectionPos(sec);
                DebugRenderer.renderFloatingText(
                        poseStack,
                        bufferSource,
                        level.getChunkSource().getLightEngine().getDebugData(LightLayer.SKY, SectionPos.of(sec)),
                        (double) SectionPos.sectionToBlockCoord(sp.x, 8),
                        (double) SectionPos.sectionToBlockCoord(sp.y, 8),
                        (double) SectionPos.sectionToBlockCoord(sp.z, 8),
                        16711680,
                        0.3F);
            }

            if (light != 15) {
                DebugRenderer.renderFloatingText(
                        poseStack,
                        bufferSource,
                        String.valueOf(light),
                        (double) pos.getX() + 0.5,
                        (double) pos.getY() + 0.25,
                        (double) pos.getZ() + 0.5,
                        Mth.hsvToRgb((15 - light) / 30.0F + 0.16F, 0.9F, 0.9F));
            }
        }
    }
}
