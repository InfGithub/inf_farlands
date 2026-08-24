package com.inf.farlands.light;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

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

    /** chunkKey → 该 chunk 的 section key 集合（removeChunk O(k) 用，替代全遍历）。 */
    private final ConcurrentHashMap<Long, LongOpenHashSet> chunkSections = new ConcurrentHashMap<>();

    public DataLayer get(long key) {
        return map.get(key);
    }

    public DataLayer getOrCreate(long key, long chunkKey) {
        DataLayer dl = map.computeIfAbsent(key, k -> new DataLayer());
        indexAdd(chunkKey, key);
        return dl;
    }

    public void put(long key, DataLayer layer, long chunkKey) {
        map.put(key, layer);
        indexAdd(chunkKey, key);
    }

    public DataLayer remove(long key, long chunkKey) {
        DataLayer dl = map.remove(key);
        LongOpenHashSet set = chunkSections.get(chunkKey);
        if (set != null) {
            synchronized (set) {
                set.remove(key);
                if (set.isEmpty()) {
                    chunkSections.remove(chunkKey);
                }
            }
        }
        return dl;
    }

    public boolean containsKey(long key) {
        return map.containsKey(key);
    }

    /** 移除一个 chunk 的全部光照层（O(sections/chunk)，替代全遍历）。 */
    public void removeChunk(long chunkKey) {
        LongOpenHashSet set = chunkSections.remove(chunkKey);
        if (set != null) {
            synchronized (set) {
                for (long k : set) {
                    map.remove(k);
                }
            }
        }
    }

    private void indexAdd(long chunkKey, long key) {
        LongOpenHashSet set = chunkSections.computeIfAbsent(chunkKey, k -> new LongOpenHashSet());
        synchronized (set) {
            set.add(key);
        }
    }

    /**
     * 按谓词移除（旧接口，removeChunk 已用索引替代；保留供未来批量清理）。
     * ConcurrentHashMap keySet.removeIf 线程安全；弱一致遍历可能漏掉并发加入的条目。
     */
    public void removeIf(Predicate<Long> predicate) {
        map.keySet().removeIf(predicate);
    }
}
