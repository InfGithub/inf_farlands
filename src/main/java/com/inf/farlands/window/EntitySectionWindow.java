package com.inf.farlands.window;

import com.inf.farlands.Config;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 实体 section 窗口并集，服务端主线程每 tick 由 InfFarlands.onServerTick 更新。
 *
 * 实体离开窗口冻结：PersistentEntitySectionManager.updateChunkStatus 与
 * EntitySectionStorage.createSection 用本类的 inAnyWindow 把窗口外 section 的
 * visibility 从 TICKING 降为 TRACKED——实体不 tick、保留 accessible。
 * 窗口 = 玩家 sectionY ±17/+16，与随机刻 ServerLevelMixin.tickChunk 一致。
 * 多玩家区间合并；跨维度合并采取保守策略：多维度同时有玩家时仅多 tick，不误杀。
 * 缓存滞后 ≤1 tick；chunk 加载异步调用用上一 tick 窗口，无感。
 */
public final class EntitySectionWindow {

    private EntitySectionWindow() {
    }

    /** 有序不重叠区间 [min0, max0, min1, max1, ...]。 */
    private static volatile int[] ranges = new int[0];

    /** 主线程只读访问窗口并集区间；volatile 引用，读安全。 */
    public static int[] ranges() {
        return ranges;
    }

    public static void update(List<ServerPlayer> players) {
        int n = players.size();
        if (n == 0) {
            ranges = new int[0];
            return;
        }
        int[][] list = new int[n][2];
        for (int i = 0; i < n; i++) {
            ServerPlayer p = players.get(i);
            int c = Mth.floorDiv(p.getBlockY(), 16);
            list[i][0] = c - Config.verticalSimulationDistance;
            list[i][1] = c + Config.verticalSimulationDistance;
        }
        Arrays.sort(list, Comparator.comparingInt(a -> a[0]));
        int[] merged = new int[n * 2];
        int m = 0;
        for (int[] iv : list) {
            if (m == 0) {
                merged[m++] = iv[0];
                merged[m++] = iv[1];
            } else if (iv[0] <= merged[m - 1] + 1) {
                merged[m - 1] = Math.max(merged[m - 1], iv[1]);
            } else {
                merged[m++] = iv[0];
                merged[m++] = iv[1];
            }
        }
        ranges = Arrays.copyOf(merged, m);
    }

    public static boolean inAnyWindow(int sectionY) {
        int[] r = ranges;
        for (int i = 0; i < r.length; i += 2) {
            if (sectionY >= r[i] && sectionY <= r[i + 1]) {
                return true;
            }
        }
        return false;
    }

    /**
     * 是否在所有窗口区间及余量 margin 之外——fsa 清理判定用。
     * 余量防边界抖动，玩家小幅往返不触发写/读，同时保证"滑回读到的必然是已落盘数据"。
     */
    public static boolean isOutsideAllWindows(int sectionY, int margin) {
        int[] r = ranges;
        for (int i = 0; i < r.length; i += 2) {
            if (sectionY >= r[i] - margin && sectionY <= r[i + 1] + margin) {
                return false;
            }
        }
        return true;
    }

    /** 遍历并集区间内全部 sectionY，供地形管线触发收集用。区间合并后数量有限。 */
    public static void forEachSectionInAnyWindow(java.util.function.IntConsumer consumer) {
        int[] r = ranges;
        for (int i = 0; i < r.length; i += 2) {
            for (int sy = r[i]; sy <= r[i + 1]; sy++) {
                consumer.accept(sy);
            }
        }
    }
}
