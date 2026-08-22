package com.inf.farlands.mixin.render;

import com.inf.farlands.WindowedChunk;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DebugLevelSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 渲染编译取数直查 allSections（数据仓库），绕开窗口数组快照。
 *
 * 原版 RenderChunk 构造时快照 chunk.getSections()（窗口数组）并按窗口索引
 * 取数——新 chunk 的窗口在包处理时未拉正（构造默认 1 section）→ 快照塌缩
 * → 取数越界返回 AIR → 透明。日志实证：isSectionEmpty 正确放行（数据在
 * allSections）但渲染不显示。
 *
 * 取数 = allSections.get(blockY >> 4)（绝对 sectionY），与窗口解耦：
 * 数据在 allSections 即渲染，不受窗口拉正时序影响。debug 分支（j==60/70）
 * 与 CrashReport 包装保留原语义。
 */
@Mixin(targets = "net.minecraft.client.renderer.chunk.RenderChunk")
public abstract class RenderChunkMixin {

    @Shadow
    private boolean debug;

    @Shadow
    LevelChunk wrapped;

    @SuppressWarnings("null")
    @Overwrite
    public BlockState getBlockState(BlockPos pos) {
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        if (this.debug) {
            BlockState blockstate = null;
            if (j == 60) {
                blockstate = Blocks.BARRIER.defaultBlockState();
            }
            if (j == 70) {
                blockstate = DebugLevelSource.getBlockStateFor(i, k);
            }
            return blockstate == null ? Blocks.AIR.defaultBlockState() : blockstate;
        }
        if (this.wrapped instanceof EmptyLevelChunk) {
            return Blocks.AIR.defaultBlockState();
        }
        try {
            LevelChunkSection s = ((WindowedChunk) this.wrapped).windowedAllSections().get(j >> 4);
            if (s == null || s.hasOnlyAir()) {
                return Blocks.AIR.defaultBlockState();
            }
            return s.getBlockState(i & 15, j & 15, k & 15);
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Getting block state");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being got");
            crashreportcategory.setDetail("Location",
                    () -> CrashReportCategory.formatLocation(this.wrapped, i, j, k));
            throw new ReportedException(crashreport);
        }
    }
}
