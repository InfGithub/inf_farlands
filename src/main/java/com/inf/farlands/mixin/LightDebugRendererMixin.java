package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntSectionPos;
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

    @Shadow @Final private Minecraft minecraft;

    // === method3: section-level key → IntSectionPos ===
    private static IntSectionPos getSectionPos(long key) {
        IntSectionPos sp = HashUtil.getSection(key);
        return sp != null ? sp
            : new IntSectionPos(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
    }

    @Overwrite
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        Level level = this.minecraft.level;
        BlockPos blockpos = BlockPos.containing(camX, camY, camZ);
        LongSet longset = new LongOpenHashSet();
        for (BlockPos blockpos1 : BlockPos.betweenClosed(blockpos.offset(-10, -10, -10), blockpos.offset(10, 10, 10))) {
            int i = level.getBrightness(LightLayer.SKY, blockpos1);
            float f = (float)(15 - i) / 15.0F * 0.5F + 0.16F;
            int j = Mth.hsvToRgb(f, 0.9F, 0.9F);
            long k = SectionPos.blockToSection(blockpos1.asLong());
            if (longset.add(k)) {
                IntSectionPos sp = getSectionPos(k);
                DebugRenderer.renderFloatingText(poseStack, bufferSource,
                    level.getChunkSource().getLightEngine().getDebugData(LightLayer.SKY, SectionPos.of(k)),
                    (double)SectionPos.sectionToBlockCoord(sp.x, 8),
                    (double)SectionPos.sectionToBlockCoord(sp.y, 8),
                    (double)SectionPos.sectionToBlockCoord(sp.z, 8),
                    16711680, 0.3F);
            }
            if (i != 15) {
                DebugRenderer.renderFloatingText(poseStack, bufferSource, String.valueOf(i),
                    (double)blockpos1.getX() + 0.5, (double)blockpos1.getY() + 0.25, (double)blockpos1.getZ() + 0.5, j);
            }
        }
    }
}
