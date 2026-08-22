package com.inf.farlands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 服务端发包线程的 per-player 窗口状态（ThreadLocal）。
 *
 * PlayerChunkSender.sendChunk / ChunkMap.resendBiomesForChunks 在发送前
 * 设置当前玩家的窗口 minY，extractChunkData 据此决定发送范围。发送是
 * 服务端主线程串行的，ThreadLocal 设/清成对；sendChunk 循环内每 chunk
 * 重新设置（同玩家，值相同，无害）。异常路径残留会被下一次 HEAD 覆盖。
 */
public final class WindowSendState {
    private WindowSendState() {
    }

    private static final ThreadLocal<Integer> WINDOW_MIN_Y = new ThreadLocal<>();

    public static void setWindowMinY(int minY) {
        WINDOW_MIN_Y.set(minY);
    }

    public static void clear() {
        WINDOW_MIN_Y.remove();
    }

    /**
     * 发送范围 = ThreadLocal 窗口（34 section，minY..minY+33）；无则
     * fallback chunk 窗口（现状语义——mark 时 moveWindowTo 的窗口）。
     */
    private static int windowMinY(LevelChunk chunk) {
        Integer v = WINDOW_MIN_Y.get();
        return v != null ? v : ((WindowedChunk) chunk).getWindowMinY();
    }

    /**
     * 窗口内非空 section 列表（绝对 sectionY 升序）。
     * calculateChunkSize 与 extractChunkData 必须共用同一过滤，
     * 否则 buffer 尺寸与实际写入不匹配。
     */
    @SuppressWarnings("null")
    public static List<Map.Entry<Integer, LevelChunkSection>> sendableSections(LevelChunk chunk) {
        int minY = windowMinY(chunk);
        int maxY = minY + WindowedChunk.WINDOW_HALF_BELOW + WindowedChunk.WINDOW_HALF_ABOVE;
        List<Map.Entry<Integer, LevelChunkSection>> out = new ArrayList<>();
        for (Map.Entry<Integer, LevelChunkSection> e : ((WindowedChunk) chunk).windowedAllSections().entrySet()) {
            int sy = e.getKey();
            LevelChunkSection s = e.getValue();
            if (sy >= minY && sy <= maxY && s != null && !s.hasOnlyAir()) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingInt(Map.Entry::getKey));
        return out;
    }
}
