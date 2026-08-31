package com.inf.farlands.mixin.light;

import com.inf.farlands.Config;
import com.inf.farlands.window.WindowedChunk;
import com.inf.farlands.light.ISkyLightSourcesAccessor;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.BitStorage;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
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
public class ChunkSkyLightSourcesMixin implements ISkyLightSourcesAccessor {

    /** fillFrom 完成标志，用于区分 fill 中与空气：内容判断无法区分 fillFrom 后无源的真空气。 */
    @Unique
    private volatile boolean farlandsFillFromDone;

    /**
     * 侧信道存储，index → 绝对 sourceY：替代 vanilla BitStorage，其 int value 上限
     * 2^31-1 无法覆盖 ±2.14B——stored = sourceY - minY 在负 Y 为负、极端正 Y 超界。
     * 未设置 = minY，表示无光源，与 BitStorage 默认语义等价。heightmap 字段保留但不再
     * 使用；fixBits/@ModifyArg 改的是构造创建的 BitStorage 位数，无害。
     *
     * volatile int[256]：fillFrom 单写，gen 线程构建新数组 volatile 发布 → light
     * 线程播种读，happens-before 由 lightChunk 队列链保证。构造 RETURN 填 minY，
     * 使 fillFrom 前 getHighestLowestSourceY 的 MIN_VALUE 检查正确。
     */
    @Unique
    private volatile int[] source = new int[256];

    @Inject(method = "<init>(Lnet/minecraft/world/level/LevelHeightAccessor;)V", at = @At("RETURN"))
    private void initSourceArray(LevelHeightAccessor level, CallbackInfo ci) {
        java.util.Arrays.fill(this.source, this.minY);
    }

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
        this.source[index] = value;
    }

    @Overwrite
    private int get(int index) {
        return this.source[index];
    }

    @Overwrite
    public int getHighestLowestSourceY() {
        int i = this.minY;
        for (int v : this.source) {
            if (v > i) {
                i = v;
            }
        }
        return i == this.minY ? Integer.MIN_VALUE : i;
    }

    @Overwrite
    private void fill(int value) {
        // fillFrom 前 getHighestLowestSourceY 读旧数组——旧数组内容 = 上次 fill
        // 结果，fill(minY) 语义 = 全部无源 = minY，兼容。
        int[] arr = new int[256];
        java.util.Arrays.fill(arr, value);
        this.source = arr;
    }

    /**
     * 覆盖 fillFrom：直接用 allSections 计算真实最高非空 section 与遮挡，
     * 不依赖 getHighestFilledSectionIndex/getSection，遵循窗口数组语义。
     */
    @Overwrite
    public void fillFrom(ChunkAccess chunk) {
        if (!(chunk instanceof WindowedChunk wc)) {
            this.fill(this.minY);
            this.farlandsFillFromDone = true;
            return;
        }
        Map<Integer, LevelChunkSection> all = wc.windowedAllSections();
        // 预计算非空 section 升序数组，一次遍历；findLowestSourceYAll 倒序访问。
        // 降序访问实际非空 section：循环次数 = section 数，与跨度无关。
        int[] buf = new int[all.size()];
        int n = 0;
        for (Integer sy : all.keySet()) {
            LevelChunkSection s = all.get(sy);
            if (s != null && !s.hasOnlyAir()) {
                buf[n++] = sy;
            }
        }
        if (n == 0) {
            this.fill(this.minY);
            this.farlandsFillFromDone = true;
            return;
        }
        int[] nonEmpty = n == buf.length ? buf : java.util.Arrays.copyOf(buf, n);
        java.util.Arrays.sort(nonEmpty);
        // gen 线程写 → light 线程播种读，happens-before 由 lightChunk 队列链保证。
        int[] arr = new int[256];
        for (int gx = 0; gx < 16; gx++) {
            for (int gz = 0; gz < 16; gz++) {
                int lowest = this.findLowestSourceYAll(chunk, all, nonEmpty, gx, gz);
                arr[gx + gz * 16] = Math.max(lowest, this.minY);
            }
        }
        this.source = arr;
        this.farlandsFillFromDone = true;
    }

    @Override
    public boolean farlands$isFillFromDone() {
        return this.farlandsFillFromDone;
    }

    /** AIR 常量缓存。 */
    @Unique
    private static final net.minecraft.world.level.block.state.BlockState FARLANDS_AIR =
            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();

    /** 复刻 vanilla findLowestSourceY，但用 allSections 的绝对 section Y 找遮挡；非空 section 倒序 + 掩码位跳。 */
    @Unique
    private int findLowestSourceYAll(ChunkAccess chunk,
            Map<Integer, LevelChunkSection> all, int[] nonEmpty, int x, int z) {
        this.mutablePos1.set(x, nonEmpty[nonEmpty.length - 1] * 16 + 16, z);
        this.mutablePos2.set(x, nonEmpty[nonEmpty.length - 1] * 16 + 15, z);
        // 降序遍历实际非空 section，即升序数组倒序——循环次数 = section 数，
        // 与数字跨度无关。
        // 空 section 不访问，above 语义由 prevY 绝对 Y 判定保持，方向 X：
        // 跳过的空气行/空洞 section 使 prevY 与当前行不相邻 → above=AIR。
        // 掩码位跳：numberOfLeadingZeros 直接定位最高 1 位行，跳过中间空气行
        // ——塔顶露天世界每 section 只有几行方块，循环从 16 次/列降到方块行数/列。
        int prevY = Integer.MIN_VALUE; // 哨兵：首访问行上方无访问行 → above=AIR
        BlockState prevState = null;
        for (int idx = nonEmpty.length - 1; idx >= 0; idx--) {
            int sy = nonEmpty[idx];
            LevelChunkSection sec = all.get(sy);
            // 列级非空掩码，LevelChunkSectionMixin 维护：bit k = 该行有方块——免逐格
            // getBlockState 的 palette 查找。
            short[] masks = ((com.inf.farlands.light.IColumnMasks) sec).farlands$ensureColumnMasks();
            int m = masks[x + z * 16] & 0xFFFF; // short 符号扩展抹高位，bit 仅 0-15
            while (m != 0) {
                // 自顶向下取最高 1 位——LZCNT 单指令
                int k = 31 - Integer.numberOfLeadingZeros(m);
                m ^= 1 << k; // 清当前位
                int y = sy * 16 + k;
                BlockState above = prevY == y + 1 ? prevState : FARLANDS_AIR;
                this.mutablePos1.setY(y + 1);
                this.mutablePos2.setY(y);
                BlockState state = sec.getBlockState(x, k, z);
                if (isEdgeOccluded(chunk, this.mutablePos1, above, this.mutablePos2, state)) {
                    return this.mutablePos1.getY();
                }
                prevY = y;
                prevState = state;
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
     * cap 512 格，Config.maxCapIter：vanilla while 循环从 pos 向下到 minY(-65)——
     * 侧信道开启后破坏 2.14B 方块，update 的 i == k 触发，且下方无遮挡时，从 2.14B
     * 扫到 -65 = 21 亿次，每格读方块 → OOM。正常高度 limitY = minY，行为不变；
     * 负 Y pos < minY → 循环天然不执行。cap 后 512 格内无遮挡 → 返回 minY，保守：
     * 该列近似无光源，比 OOM 好；后续再放置恢复正常。
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
