package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * setBlocksDirty 极端坐标冻结修复。
 *
 * 三层 for 循环边界 = 传入坐标 ± 1，int 运算。任一轴坐标 = 2147483646
 * = MAX_VALUE−1 时 max+1 = 2147483647 = MAX_VALUE 不溢出 → 循环正常跑完区间
 * 后 i++ 溢出成 MIN_VALUE ≤ 上界 → 无限循环。setBlocksDirty 无 hasChunksAt 门，
 * X/Y/Z 任一轴均触发。
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererSetBlocksDirtyMixin {

    @Shadow
    public abstract void setSectionDirty(int sectionX, int sectionY, int sectionZ);

    @Shadow
    protected abstract void setSectionDirty(int sectionX, int sectionY, int sectionZ, boolean reRenderOnMainThread);

    @Overwrite
    public void setBlocksDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (long i = (long) minZ - 1; i <= (long) maxZ + 1; i++) {
            for (long j = (long) minX - 1; j <= (long) maxX + 1; j++) {
                for (long k = (long) minY - 1; k <= (long) maxY + 1; k++) {
                    this.setSectionDirty(
                            SectionPos.blockToSectionCoord((int) j),
                            SectionPos.blockToSectionCoord((int) k),
                            SectionPos.blockToSectionCoord((int) i));
                }
            }
        }
    }

    /**
     * 同款漏洞的第二入口：blockChanged → setBlockDirty，与 setBlocksDirty 路径
     * 并存，flags=19 时两条都会走。循环结构同 setBlocksDirty，pos ± 1，三层。
     */
    @Overwrite
    private void setBlockDirty(BlockPos pos, boolean reRenderOnMainThread) {
        for (long i = (long) pos.getZ() - 1; i <= (long) pos.getZ() + 1; i++) {
            for (long j = (long) pos.getX() - 1; j <= (long) pos.getX() + 1; j++) {
                for (long k = (long) pos.getY() - 1; k <= (long) pos.getY() + 1; k++) {
                    this.setSectionDirty(
                            SectionPos.blockToSectionCoord((int) j),
                            SectionPos.blockToSectionCoord((int) k),
                            SectionPos.blockToSectionCoord((int) i),
                            reRenderOnMainThread);
                }
            }
        }
    }
}
