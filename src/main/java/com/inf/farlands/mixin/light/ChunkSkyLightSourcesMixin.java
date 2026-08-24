package com.inf.farlands.mixin.light;

import com.inf.farlands.Config;
import com.inf.farlands.WindowedChunk;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
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
    private final ConcurrentHashMap<Integer, Integer> sourceMap = new ConcurrentHashMap<>();

    @Shadow @Final @Mutable
    private BitStorage heightmap;

    @Shadow
    private int minY;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos1;

    @Shadow
    @Final
    private BlockPos.MutableBlockPos mutablePos2;

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
        Integer v = sourceMap.get(index);
        return v != null ? v : this.minY;
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

    /**
     * 覆盖 fillFrom：直接用 allSections（数据仓库）计算真实最高非空 section 与遮挡，
     * 不依赖 getHighestFilledSectionIndex/getSection（窗口数组语义）。
     *
     * 原实现依赖窗口：客户端 replaceWithPacketData 时窗口为构造默认（1 section -4）或
     * 拉正窗口——getHighestFilledSectionIndex 用窗口索引 → fillFrom 从窗口 section 找遮挡
     * → 最低光源被算到窗口内（如 -4 附近 / 玩家附近）而非真实地表 → 任何破坏格 y >=
     * lowestY → 破坏格被当天空光源 ADD 15 → 自然衰减传播（任何 y 火把样）。
     */
    @Overwrite
    public void fillFrom(ChunkAccess chunk) {
        if (!(chunk instanceof WindowedChunk wc)) {
            this.fill(this.minY);
            return;
        }
        Map<Integer, LevelChunkSection> all = wc.windowedAllSections();
        int highest = Integer.MIN_VALUE;
        int lowestSection = Integer.MAX_VALUE;
        for (Integer sy : all.keySet()) {
            LevelChunkSection s = all.get(sy);
            if (s != null && !s.hasOnlyAir()) {
                if (sy > highest) {
                    highest = sy;
                }
                if (sy < lowestSection) {
                    lowestSection = sy;
                }
            }
        }
        if (highest == Integer.MIN_VALUE) {
            this.fill(this.minY);
            return;
        }
        for (int gx = 0; gx < 16; gx++) {
            for (int gz = 0; gz < 16; gz++) {
                int lowest = this.findLowestSourceYAll(chunk, all, highest, lowestSection, gx, gz);
                this.set(gx + gz * 16, Math.max(lowest, this.minY));
            }
        }
    }

    /** 复刻 vanilla findLowestSourceY，但用 allSections（绝对 section Y）逐格向下找遮挡。 */
    @Unique
    @SuppressWarnings("null")
    private int findLowestSourceYAll(ChunkAccess chunk,
            Map<Integer, LevelChunkSection> all, int highestSection, int lowestSection, int x, int z) {
        this.mutablePos1.set(x, highestSection * 16 + 16, z);
        this.mutablePos2.set(x, highestSection * 16 + 15, z);
        BlockState above = Blocks.AIR.defaultBlockState();
        for (int sy = highestSection; sy >= lowestSection; sy--) {
            LevelChunkSection sec = all.get(sy);
            if (sec == null || sec.hasOnlyAir()) {
                above = Blocks.AIR.defaultBlockState();
                this.mutablePos1.setY(sy * 16 + 15);
                this.mutablePos2.setY(sy * 16 + 14);
                continue;
            }
            for (int k = 15; k >= 0; k--) {
                BlockState state = sec.getBlockState(x, k, z);
                if (isEdgeOccluded(chunk, this.mutablePos1, above, this.mutablePos2, state)) {
                    return this.mutablePos1.getY();
                }
                above = state;
                this.mutablePos1.set(this.mutablePos2);
                this.mutablePos2.move(Direction.DOWN);
            }
        }
        return this.minY;
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
