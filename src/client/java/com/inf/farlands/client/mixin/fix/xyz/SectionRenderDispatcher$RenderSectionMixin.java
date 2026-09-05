package com.inf.farlands.client.mixin.fix.xyz;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Final;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

/**
 * RenderSection.setSectionNode 的 X/Y/Z 极端 section 坐标溢出修复。
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection")
public abstract class SectionRenderDispatcher$RenderSectionMixin {

    @Shadow
    private volatile long sectionNode;

    @Shadow
    private AABB bb;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos renderOrigin;

    @Shadow
    private void reset() {
    }

    /** section→block long 化后饱和到 int 边界。 */
    @Unique
    private static int saturateBlockCoord(int sectionCoord) {
        long block = (long) sectionCoord << 4;
        return block > Integer.MAX_VALUE ? Integer.MAX_VALUE
                : block < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) block;
    }

    @Overwrite
    public void setSectionNode(long node) {
        this.reset();
        this.sectionNode = node;
        int x = saturateBlockCoord(SectionPos.x(node));
        int y = saturateBlockCoord(SectionPos.y(node));
        int z = saturateBlockCoord(SectionPos.z(node));
        this.renderOrigin.set(x, y, z);
        this.bb = new AABB(
                (double) x,
                (double) y,
                (double) z,
                (double) ((long) x + 16L),
                (double) ((long) y + 16L),
                (double) ((long) z + 16L));
    }
}
