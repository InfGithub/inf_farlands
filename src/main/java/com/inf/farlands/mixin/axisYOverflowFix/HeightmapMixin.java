package com.inf.farlands.mixin.axisYOverflowFix;

import net.minecraft.util.BitStorage;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Heightmap.class)
public class HeightmapMixin {

    @Shadow
    @Final
    @Mutable
    private BitStorage data;

    @Shadow
    private ChunkAccess chunk;

    @Inject(method = "setHeight", at = @At("HEAD"))
    private void ensureCapacity(int x, int z, int value, CallbackInfo ci) {
        int minY = this.chunk.getMinBuildHeight();
        long storedNew = (long) value - (long) minY;
        long maxStoredOld = (1L << this.data.getBits()) - 1L;
        if (storedNew <= maxStoredOld)
            return;

        long maxStored = Math.max(storedNew, maxStoredOld);
        if (maxStored < 0L)
            maxStored = 0L;
        int bits = 64 - Long.numberOfLeadingZeros(maxStored);
        if (bits < 1)
            bits = 1;
        SimpleBitStorage news = new SimpleBitStorage(bits, 256);
        for (int i = 0; i < 256; i++)
            news.set(i, this.data.get(i));
        this.data = news;
    }

    @ModifyArg(method = "setHeight", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/BitStorage;set(II)V"), index = 1)
    private int clampNegativeValue(int value) {
        return Math.max(value, 0);
    }

    private static final ThreadLocal<Integer> currentUpdateY = new ThreadLocal<>();

    @Inject(method = "update", at = @At("HEAD"))
    private void captureUpdateY(int x, int y, int z, BlockState state, CallbackInfoReturnable<?> ci) {
        currentUpdateY.set(y);
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getMinBuildHeight()I"))
    private int capUpdateLoop(ChunkAccess chunk) {
        Integer yObj = currentUpdateY.get();
        int vanillaMin = chunk.getMinBuildHeight();
        if (yObj == null)
            return vanillaMin;
        int cap = yObj - 2048;
        return Math.max(vanillaMin, cap);
    }

    /**
     * 修复：服务端 ensureCapacity 为极端 Y 高度图值扩容 BitStorage 后，序列化的
     * long[] 大于客户端默认 9 位分配。不落入 primeHeightmaps，它因
     * getHighestSectionPosition 返回极端值而冻结；而是重建 BitStorage 匹配
     * 服务端数据大小并直接复制。
     */
    @Overwrite
    public void setRawData(ChunkAccess chunk, Heightmap.Types type, long[] data) {
        long[] along = this.data.getRaw();
        if (along.length == data.length) {
            System.arraycopy(data, 0, along, 0, data.length);
        } else {
            int bits = Math.max(1, data.length / 4);
            SimpleBitStorage news = new SimpleBitStorage(bits, 256);
            System.arraycopy(data, 0, news.getRaw(), 0, data.length);
            this.data = news;
        }
    }

    /**
     * 钳制 primeHeightmaps 的循环：窗口覆盖极端 Y 的 chunk 到达它时，
     * 每列不会迭代 21 亿次。
     */
    @SuppressWarnings("removal")
    @Redirect(method = "primeHeightmaps", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getHighestSectionPosition()I"))
    private static int capPrimeHeightmapsLoop(ChunkAccess chunk) {
        int raw = chunk.getHighestSectionPosition();
        int cap = chunk.getMinBuildHeight() + 2048;
        return Math.min(raw, cap);
    }
}
