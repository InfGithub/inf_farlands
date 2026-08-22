package com.inf.farlands;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 实体 section 窗口并集（服务端主线程，每 tick 由 InfFarlands.onServerTick 更新）。
 *
 * 方案 D（实体离开窗口冻结）：PersistentEntitySectionManager.updateChunkStatus 与
 * EntitySectionStorage.createSection 用本类的 inAnyWindow 把窗口外 section 的
 * visibility 从 TICKING 降为 TRACKED——实体不 tick、保留 accessible。
 * 窗口 = 玩家 sectionY ±17/+16（与随机刻 ServerLevelMixin.tickChunk 一致）。
 * 多玩家区间合并；跨维度合并（保守：多维度同时有玩家时仅多 tick，不误杀）。
 * 缓存滞后 ≤1 tick（chunk 加载异步调用用上一 tick 窗口，无感）。
 */
public final class EntitySectionWindow {

    private EntitySectionWindow() {
    }

    /** 有序不重叠区间 [min0, max0, min1, max1, ...]。 */
    private static volatile int[] ranges = new int[0];

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
            list[i][0] = c - WindowedChunk.WINDOW_HALF_BELOW;
            list[i][1] = c + WindowedChunk.WINDOW_HALF_ABOVE;
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
}
