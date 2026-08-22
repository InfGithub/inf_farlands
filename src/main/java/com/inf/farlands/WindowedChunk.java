package com.inf.farlands;

import java.util.Map;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 窗口系统一等公民接口（由 ChunkAccessMixin 注入到 ChunkAccess）。
 *
 * chunk 持有无限 Y 的 section 仓库（allSections），但对外只展示一个
 * 以"中心"为基准的固定 34 section 窗口视图（windowSections + windowMinY）。
 * buildWindow 是低层原语；moveWindowTo/expandWindowTo 是 S2 滑动窗口语义。
 */
public interface WindowedChunk {

    /** 中心下方半径（下界 = center - 17） */
    int WINDOW_HALF_BELOW = 17;

    /** 中心上方半径（上界 = center + 16） */
    int WINDOW_HALF_ABOVE = 16;

    /** 重建窗口视图为精确的 [sectionYMin, sectionYMax]。 */
    void buildWindow(int sectionYMin, int sectionYMax);

    /** 窗口滑到以 centerSectionY 为中心（34 section）。 */
    default void moveWindowTo(int centerSectionY) {
        buildWindow(centerSectionY - WINDOW_HALF_BELOW, centerSectionY + WINDOW_HALF_ABOVE);
    }

    /** 确保 sectionY 可见：窗口内不动，窗口外将窗口滑到该点。 */
    default void expandWindowTo(int sectionY) {
        if (sectionY < getWindowMinY() || sectionY > getWindowMaxY()) {
            moveWindowTo(sectionY);
        }
    }

    int getWindowMinY();

    int getWindowMaxY();

    int windowSectionYFromIndex(int index);

    int windowSectionIndexFromY(int sectionY);

    Map<Integer, LevelChunkSection> windowedAllSections();

    /** chunk 的真实 LevelHeightAccessor（维度范围，非窗口感知） */
    LevelHeightAccessor levelHeightAccessor();

    // ---- 客户端持有边界（window.md §7.2）----
    // 服务端承诺发送的 Y 范围，由 §5 section 包 handler 更新；
    // MIN_VALUE = 未收到包。独立于视图窗口（windowMinY 被相机占用）。
    // 丢弃检查（§7.3）必须带 minY != MIN_VALUE 守卫：未收包时跳过，
    // 否则 MIN_VALUE 下丢弃条件恒真，会丢光未收包 chunk 的数据。

    default int lastPacketMinY() {
        return Integer.MIN_VALUE;
    }

    default int lastPacketMaxY() {
        return Integer.MIN_VALUE;
    }

    default void setLastPacketWindow(int minY, int maxY) {
    }
}
