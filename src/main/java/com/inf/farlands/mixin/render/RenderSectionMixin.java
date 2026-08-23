package com.inf.farlands.mixin.render;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 修复 hasAllNeighbors() 在视距边缘取消编译的问题。
 * <p>
 * vanilla 中，玩家 24 格内的 RenderSection 总是通过。超过 24 格后，
 * 它检查 4 个正交相邻 chunk 是否已在客户端加载。在视距边缘，中心 chunk
 * 的包可能先于相邻 chunk 到达——hasAllNeighbors() 失败，
 * RebuildTask.cancel() 置 dirty=false，section 不再重试。
 * <p>
 * 本 mod 立即以 FULL 状态发送所有 chunk，所以相邻 chunk 的包到达后总有方块
 * 数据。编译前等待相邻没有意义。
 */
@Mixin(RenderSection.class)
public abstract class RenderSectionMixin {

    @Overwrite
    public boolean hasAllNeighbors() {
        return true;
    }
}
