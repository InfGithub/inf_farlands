package com.inf.farlands.light;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.world.level.chunk.DataLayer;

/**
 * 单层光照存储，替代 vanilla 的 queued/updating/visible 三层缓冲 + swap + COW。
 * 每引擎一个 {@code ConcurrentHashMap<Long, DataLayer>}。
 *
 * <p>ConcurrentHashMap（JDK）在热查询路径上提供无锁读（{@code getLightValue}
 * 每帧跑在渲染线程，传播写在服务端线程，{@code initializeLight} 在后台线程）。
 * 所有调用点都是单方法操作（无跨方法复合依赖），单方法原子性足够。
 * 弱一致：与 {@code remove} 并发的 {@code get} 可能返回刚移除的引用——
 * 瞬时快照，下一帧修正，无害。
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
