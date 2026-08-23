package com.inf.farlands.mixin.light;

import com.inf.farlands.Config;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.ChunkSkyLightSources;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkSkyLightSources.class)
public class ChunkSkyLightSourcesMixin {

    /**
     * 侧信道存储（index → 绝对 sourceY）：替代 vanilla BitStorage（其 int value 上限
     * 2^31-1 无法覆盖 ±2.14B——stored = sourceY - minY 在负 Y 为负、极端正 Y 超界）。
     * 未设置 = minY（无光源），与 BitStorage 默认语义等价。heightmap 字段保留但不再
     * 使用（fixBits/@ModifyArg 改的是构造创建的 BitStorage 位数，无害）。
     */
    @Unique
    private final Int2IntOpenHashMap sourceMap = new Int2IntOpenHashMap();

    @Shadow @Final @Mutable
    private BitStorage heightmap;

    @Shadow
    private int minY;

    private static LevelHeightAccessor capturedLevel;

    @Redirect(method = "findLowestSourceY", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;hasOnlyAir()Z"))
    private boolean redirectHasOnlyAir(LevelChunkSection section) {
        return section == null || section.hasOnlyAir();
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/LevelHeightAccessor;)V", at = @At("HEAD"))
    private static void captureLevel(LevelHeightAccessor level, CallbackInfo ci) {
        capturedLevel = level;
    }

    @ModifyArg(method = "<init>(Lnet/minecraft/world/level/LevelHeightAccessor;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/SimpleBitStorage;<init>(II)V"), index = 0)
    private int fixBits(int overflowedBits) {
        long span = (long)capturedLevel.getMaxBuildHeight() - (long)capturedLevel.getMinBuildHeight() + 2L;
        return 64 - Long.numberOfLeadingZeros(span - 1L);
    }

    @Overwrite
    private void set(int index, int value) {
        sourceMap.put(index, value);
    }

    @Overwrite
    private int get(int index) {
        return sourceMap.containsKey(index) ? sourceMap.get(index) : this.minY;
    }

    @Overwrite
    public int getHighestLowestSourceY() {
        int i = this.minY;
        for (int v : sourceMap.values()) {
            if (v > i) {
                i = v;
            }
        }
        return i == this.minY ? Integer.MIN_VALUE : i;
    }

    @Overwrite
    private void fill(int value) {
        if (value == this.minY) {
            sourceMap.clear(); // fillFrom 只调 fill(minY)（L36）——未设置 = minY，清空等价
        } else {
            sourceMap.replaceAll((k, v) -> value);
        }
    }

    @Shadow
    private static boolean isEdgeOccluded(BlockGetter level, BlockPos pos1, BlockState state1,
            BlockPos pos2, BlockState state2) {
        return false;
    }

    /**
     * cap 512 格（Config.maxCapIter）：vanilla while 循环从 pos 向下到 minY(-65)——
     * 侧信道开启后破坏 2.14B 方块（update 的 i == k 触发）且下方无遮挡时，从 2.14B
     * 扫到 -65 = 21 亿次（每格读方块）→ OOM。正常高度 limitY = minY（行为不变）；
     * 负 Y pos < minY → 循环天然不执行。cap 后 512 格内无遮挡 → 返回 minY（保守，
     * 该列近似无光源——比 OOM 好；后续再放置恢复正常）。
     */
    @SuppressWarnings({ "null" })
    @Overwrite
    private int findLowestSourceBelow(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos.MutableBlockPos mp1 = new BlockPos.MutableBlockPos().set(pos);
        BlockPos.MutableBlockPos mp2 = new BlockPos.MutableBlockPos().setWithOffset(pos, Direction.DOWN);
        BlockState blockstate = state;
        int limitY = (int) Math.max((long) pos.getY() - Config.maxCapIter, (long) this.minY);
        while (mp2.getY() >= limitY) {
            BlockState blockstate1 = level.getBlockState(mp2);
            if (isEdgeOccluded(level, mp1, blockstate, mp2, blockstate1)) {
                return mp1.getY();
            }
            blockstate = blockstate1;
            mp1.set(mp2);
            mp2.move(Direction.DOWN);
        }
        return this.minY;
    }
}
