package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * RenderSection.setOrigin 的 AABB 构造 (double)(x+16) 在 x > 2147483631
 * （缓冲带 origin，如 chunk 134217727 的 2147483632~2147483647）时 int
 * 溢出为负 → AABB 塌缩。long 化：(double)((long)x + 16L)。
 *
 * 配合 ViewArea origin 饱和上限放宽到 int max——缓冲带 chunk 保留独立
 * origin → 独立渲染（空气），不再折叠显示 134217726 的内容。
 * relativeOrigins 的 ±16 溢出保留：超界方向的相对 origin 为负数 →
 * SectionOcclusionGraph.getRelativeFrom 的 isInViewDistance 判定超界 →
 * 不扩散不射线（安全，该方向本就无数据）。
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.SectionRenderDispatcher$RenderSection")
public class RenderSectionSetOriginMixin {

    @Shadow
    private AABB bb;

    @Shadow
    private BlockPos.MutableBlockPos origin;

    @Shadow
    private BlockPos.MutableBlockPos[] relativeOrigins;

    @Shadow
    private void reset() {
    }

    @SuppressWarnings("null")
    @Overwrite
    public void setOrigin(int x, int y, int z) {
        this.reset();
        this.origin.set(x, y, z);
        this.bb = new AABB(
                (double) x,
                (double) y,
                (double) z,
                (double) ((long) x + 16L),
                (double) ((long) y + 16L),
                (double) ((long) z + 16L));
        for (Direction direction : Direction.values()) {
            this.relativeOrigins[direction.ordinal()].set(this.origin).move(direction, 16);
        }
    }
}
