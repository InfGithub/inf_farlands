package com.inf.farlands.mixin.axisY;

import com.inf.farlands.WindowedChunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 丢弃（§7.3 discardOutsideHoldBoundary）只删 allSections 数据、不清 BE/ticker →
 * BE tick 时 getBlockState 读空 → "invalid for ticking" WARN（每 BE 一次批量噪音）。
 * 修复：tick HEAD 检查该 section 是否在数据仓库中——section 不存在或空（窗口滑出
 * 的预期状态）→ 跳过 tick（不 WARN）；section 有数据但 isValid false（真数据不
 * 一致）→ 保留 vanilla WARN。
 * 判断用 windowedAllSections().get()（不懒创建），且 HEAD 在任何 getBlockState
 * 之前——避免懒创建空 section 污染判断。
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class LevelChunk$BoundTickingBlockEntityMixin<T extends BlockEntity> {

    @Shadow
    @Final
    private LevelChunk this$0;

    @Shadow
    public abstract BlockPos getPos();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void skipTickWhenSectionDiscarded(CallbackInfo ci) {
        LevelChunkSection s = ((WindowedChunk) this.this$0).windowedAllSections().get(getPos().getY() >> 4);
        if (s == null || s.hasOnlyAir()) {
            ci.cancel();
        }
    }
}
