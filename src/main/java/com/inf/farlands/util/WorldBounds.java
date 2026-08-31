package com.inf.farlands.util;

import com.inf.farlands.Config;
import com.inf.farlands.Constants;

/**
 * 边界统一语义，是唯一边界入口。
 *
 * 可玩范围供正常逻辑使用，涵盖生成/装饰/寻路/结构/方块操作/光照：
 *   block XZ: [MIN_PLAYABLE_BLOCK, MAX_PLAYABLE_BLOCK] = [-MAX_BLOCK, MAX_BLOCK-1]
 *   chunk:    [MIN_PLAYABLE_CHUNK, MAX_PLAYABLE_CHUNK] = [-MAX_CHUNK, MAX_CHUNK-1]
 *   Y:        [worldGenMinY, worldGenMaxY)，半开区间——worldGenMaxY 是缓冲带起始
 *
 * 缓冲带不可用于正常逻辑，各 16 格 = 恰好一个整 chunk：
 *   正侧 [MAX_BLOCK, MAX_BLOCK+15] = chunk MAX_CHUNK
 *   负侧 [Integer.MIN_VALUE, ~MAX_BLOCK] = chunk ~MAX_CHUNK
 *
 * 整型本身正负不对称 → 位对称配对 +X ↔ ~X=-X-1；可玩范围数值对称 [-MAX, MAX-1]
 * 等价位对称定义为 [~P, P]，其中 P = Integer.MAX_VALUE - 16 = MAX_BLOCK - 1。
 * 判断一律"包含式"命名，拦截点用 !in... 取反——避免散落的 >=/<= 各自偏差。
 */
public final class WorldBounds {
    private WorldBounds() {
    }

    /** 正方向最后一个可玩方块 = 2,147,483,631 = 2^31-17。 */
    public static final int MAX_PLAYABLE_BLOCK = Constants.MAX_BLOCK - 1;

    /** 负方向最后一个可玩方块 = -2,147,483,632 = -2^31+16 = ~MAX_PLAYABLE_BLOCK。 */
    public static final int MIN_PLAYABLE_BLOCK = -Constants.MAX_BLOCK;

    /** 正方向最后一个可玩 chunk = 134,217,726 = 2^27-2。 */
    public static final int MAX_PLAYABLE_CHUNK = Constants.MAX_CHUNK - 1;

    /** 负方向最后一个可玩 chunk = -134,217,727 = -(2^27-1)。 */
    public static final int MIN_PLAYABLE_CHUNK = -Constants.MAX_CHUNK;

    // ---- block 单轴 ----

    public static boolean inBlock(int v) {
        return v >= MIN_PLAYABLE_BLOCK && v <= MAX_PLAYABLE_BLOCK;
    }

    /** 中间值可能已溢出 int，如结构 boundingBox 等，long 版承接。 */
    public static boolean inBlock(long v) {
        return v >= MIN_PLAYABLE_BLOCK && v <= MAX_PLAYABLE_BLOCK;
    }

    public static boolean inBlockXZ(int x, int z) {
        return inBlock(x) && inBlock(z);
    }

    public static boolean inBlockXZ(long x, long z) {
        return inBlock(x) && inBlock(z);
    }

    // ---- chunk ----

    public static boolean inChunk(int v) {
        return v >= MIN_PLAYABLE_CHUNK && v <= MAX_PLAYABLE_CHUNK;
    }

    public static boolean inChunkRange(int cx, int cz) {
        return inChunk(cx) && inChunk(cz);
    }

    // ---- Y：Config 化；worldGenMaxY 是缓冲带起始 → 半开 [min, max) ----

    public static boolean inBuildHeight(int y) {
        return y >= Config.worldGenMinY && y < Config.worldGenMaxY;
    }

    /** 范围 [minY, maxY) 是否完全在可玩高度内，沿用 hasChunksAt 语义。 */
    public static boolean inBuildHeightRange(int minY, int maxY) {
        return minY >= Config.worldGenMinY && maxY < Config.worldGenMaxY;
    }

    // ---- clamp 含边界，永不落缓冲带 ----

    public static int clampBlockCoord(int v) {
        return v > MAX_PLAYABLE_BLOCK ? MAX_PLAYABLE_BLOCK
                : v < MIN_PLAYABLE_BLOCK ? MIN_PLAYABLE_BLOCK : v;
    }

    public static int clampBlockCoord(long v) {
        return v > MAX_PLAYABLE_BLOCK ? MAX_PLAYABLE_BLOCK
                : v < MIN_PLAYABLE_BLOCK ? MIN_PLAYABLE_BLOCK : (int) v;
    }

    public static int clampChunk(int v) {
        return v > MAX_PLAYABLE_CHUNK ? MAX_PLAYABLE_CHUNK
                : v < MIN_PLAYABLE_CHUNK ? MIN_PLAYABLE_CHUNK : v;
    }
}
