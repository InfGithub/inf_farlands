package com.inf.farlands.window;

import com.inf.farlands.Config;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.inf.farlands.terrain.pipeline.GenQueue;

import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * 服务端发包线程的 per-player 窗口状态，存于 ThreadLocal。
 *
 * PlayerChunkSender.sendChunk / ChunkMap.resendBiomesForChunks 在发送前
 * 设置当前玩家的窗口 minY，extractChunkData 据此决定发送范围。发送是
 * 服务端主线程串行的，ThreadLocal 设/清成对；sendChunk 循环内每 chunk
 * 重新设置同玩家时值相同，无害。异常路径残留会被下一次 HEAD 覆盖。
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
     * 发送范围 = ThreadLocal 窗口，即 34 section 的 minY..minY+33；无则
     * fallback chunk 窗口，即现状语义中 mark 时 moveWindowTo 的窗口。
     */
    private static int windowMinY(LevelChunk chunk) {
        Integer v = WINDOW_MIN_Y.get();
        return v != null ? v : ((WindowedChunk) chunk).getWindowMinY();
    }

    /**
     * 窗口内非空 section 列表，按绝对 sectionY 升序。
     * calculateChunkSize 与 extractChunkData 必须共用同一过滤，
     * 否则 buffer 尺寸与实际写入不匹配。
     *
     * <p>fill/光照在途（GenQueue.isChunkBusy）的 chunk 返回空：genPool fill 线程
     * 并发写 section 时，calculateChunkSize 与 extractChunkData 两次遍历间的
     * section 集合/内容会不一致 → chunk 包 buffer 超支 CTD（迷宫高频生成触发）。
     * 过滤后 section 稳定才打包发送；数据由 fill 完成后的 §5 section 包补齐。
     */
    @SuppressWarnings("null")
    public static List<Map.Entry<Integer, LevelChunkSection>> sendableSections(LevelChunk chunk) {
        if (GenQueue.isChunkBusy(chunk)) {
            return List.of(); // fill/光照在途：不读半成品 section，防打包竞态
        }
        int minY = windowMinY(chunk);
        int maxY = minY + Config.verticalSimulationDistance * 2;
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
