package com.inf.farlands.light;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.world.level.chunk.DataLayer;

/**
 * Single-layer light storage replacing vanilla's queued/updating/visible
 * triple-buffer + swap + COW. One {@code ConcurrentHashMap<Long, DataLayer>}
 * per engine.
 *
 * <p>ConcurrentHashMap (JDK) gives lock-free reads on the hot query path
 * ({@code getLightValue} runs on the render thread every frame while
 * propagation writes run on the server thread and {@code initializeLight}
 * on a background thread). All call sites are single-method operations
 * (no cross-method compound dependency), so per-method atomicity is
 * sufficient. Weak consistency: a {@code get} concurrent with {@code remove}
 * may return a just-removed reference — a transient snapshot, corrected on
 * the next frame, harmless.
 */
public class FarLandsDataLayerStorage {
    private final ConcurrentHashMap<Long, DataLayer> map = new ConcurrentHashMap<>();

    public DataLayer get(long key) {
        return map.get(key);
    }

    public DataLayer getOrCreate(long key) {
        return map.computeIfAbsent(key, k -> new DataLayer());
    }

    public void put(long key, DataLayer layer) {
        map.put(key, layer);
    }

    public DataLayer remove(long key) {
        return map.remove(key);
    }

    public boolean containsKey(long key) {
        return map.containsKey(key);
    }

    /**
     * 按谓词移除（服务端 chunk 卸载清理用）。ConcurrentHashMap keySet.removeIf
     * 线程安全；弱一致遍历可能漏掉并发加入的条目——卸载场景无新层加入该 chunk，
     * 残留瞬态由重载 propagateLightSources 覆盖。
     */
    public void removeIf(Predicate<Long> predicate) {
        map.keySet().removeIf(predicate);
    }
}
