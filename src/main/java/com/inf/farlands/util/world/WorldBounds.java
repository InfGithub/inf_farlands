package com.inf.farlands.util.world;

import com.inf.farlands.FarlandsConfig;
import com.inf.farlands.FarlandsConstant;

/**
 * 边界统一语义。
 *
 * 可玩范围供正常逻辑使用：
 * block XZ: [MIN_PLAYABLE_BLOCK, MAX_PLAYABLE_BLOCK] = [-MAX_BLOCK,
 * MAX_BLOCK-1]
 * chunk: [MIN_PLAYABLE_CHUNK, MAX_PLAYABLE_CHUNK] = [-MAX_CHUNK, MAX_CHUNK-1]
 * Y: [worldGenMinY, worldGenMaxY)
 *
 * 缓冲带不可用于正常逻辑，各 16 格 = 恰好一个整 chunk：
 * 正侧 [MAX_BLOCK, MAX_BLOCK+15] = chunk MAX_CHUNK
 * 负侧 [Integer.MIN_VALUE, ~MAX_BLOCK] = chunk ~MAX_CHUNK
 *
 */
public final class WorldBounds {
    private WorldBounds() {
    }

    /** 正方向最后一个可玩方块 = 2,147,483,631 = 2^31-17。 */
    public static final int MAX_PLAYABLE_BLOCK = FarlandsConstant.MAX_BLOCK - 1;

    public static final int MIN_PLAYABLE_BLOCK = -FarlandsConstant.MAX_BLOCK;

    /** 正方向最后一个可玩 chunk = 134,217,726 = 2^27-2。 */
    public static final int MAX_PLAYABLE_CHUNK = FarlandsConstant.MAX_CHUNK - 1;

    public static final int MIN_PLAYABLE_CHUNK = -FarlandsConstant.MAX_CHUNK;

    public static boolean inBlock(int v) {
        return v >= MIN_PLAYABLE_BLOCK && v <= MAX_PLAYABLE_BLOCK;
    }

    public static boolean inBlock(long v) {
        return v >= MIN_PLAYABLE_BLOCK && v <= MAX_PLAYABLE_BLOCK;
    }

    public static boolean inBlockXZ(int x, int z) {
        return inBlock(x) && inBlock(z);
    }

    public static boolean inBlockXZ(long x, long z) {
        return inBlock(x) && inBlock(z);
    }

    public static boolean inChunk(int v) {
        return v >= MIN_PLAYABLE_CHUNK && v <= MAX_PLAYABLE_CHUNK;
    }

    public static boolean inChunkRange(int cx, int cz) {
        return inChunk(cx) && inChunk(cz);
    }

    public static boolean inBuildHeight(int y) {
        return y >= FarlandsConfig.worldGenMinY && y < FarlandsConfig.worldGenMaxY;
    }

    /** 范围 [minY, maxY) 是否完全在可玩高度内，沿用 hasChunksAt 语义。 */
    public static boolean inBuildHeightRange(int minY, int maxY) {
        return minY >= FarlandsConfig.worldGenMinY && maxY < FarlandsConfig.worldGenMaxY;
    }

    // clamp 含边界，永不落缓冲带

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
