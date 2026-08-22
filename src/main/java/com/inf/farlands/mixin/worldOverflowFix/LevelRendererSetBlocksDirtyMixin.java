package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * #38 setBlocksDirty 极端坐标冻结修复（与 #36 checkInsideBlocks 同类）。
 *
 * 三层 for 循环边界 = 传入坐标 ± 1（int 运算）。任一轴坐标 = 2147483646
 * （MAX_VALUE−1）时 max+1 = 2147483647 = MAX_VALUE（不溢出）→ 循环正常跑完
 * 区间后 i++ 溢出成 MIN_VALUE ≤ 上界 → 无限循环（同一溢出点：#36 是上界直接
 * = MAX_VALUE，这里是 max+1 = MAX_VALUE）。setBlocksDirty 无 hasChunksAt 门，
 * X/Y/Z 任一轴均触发。触发源：§5 批量包发送缓冲带 section（sy = MAX_CHUNK，
 * relY = 14 → 坐标 2147483646）——源头未知，修在循环本身（对任何触发源鲁棒）。
 *
 * 修复：循环变量 long 化——(long) 起止 ±1 不溢出，long 递增永不溢出，循环按
 * 区间差值正常终止（正常单点 27 次不变；死亡坐标 2147483646 也 3 次终止）。
 * 体内 (int) 转换：超出 int 的部分（(long)MIN_VALUE−1 等）低 32 位与原版 int
 * 溢出逐位一致，只有 int 范围内正常遍历的行为修正。
 * 逐行对照 vanilla（LevelRenderer.java:2517-2525），仅循环变量类型改变。
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
     * #38 同款漏洞的第二入口（blockChanged → setBlockDirty）——markAndNotifyBlock L283
     * 的 sendBlockUpdated 路径（与 L277 setBlocksDirty 路径并存，flags=19 时两条都会走）。
     * 循环结构与 setBlocksDirty 逐字同构（pos ± 1，三层）——long 化后死亡坐标
     * 2147483646（max+1 = MAX_VALUE）正常终止。
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
