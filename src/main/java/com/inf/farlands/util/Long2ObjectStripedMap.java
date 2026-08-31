package com.inf.farlands.util;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.concurrent.locks.StampedLock;
import java.util.function.LongFunction;
import java.util.function.Predicate;

/**
 * 无装箱分段 map：fastutil Long2ObjectOpenHashMap × 64 段 + StampedLock。
 *
 * 替代 ConcurrentHashMap&lt;Long, V&gt;——后者在传播热路径上每秒 ~2800 万次 get/put
 * 的 Long key 自动装箱达 JFR 134.5G + 桶树化 comparableClassFor 反射，已由 hashPos
 * 外层 fmix 消除 + 扩容 Node 分配 ≈ 每秒 0.96G 垃圾 → young GC 风暴。
 *
 * 本实现：long key 直接进 fastutil 原始类型 map 实现零装箱、线性探测无 Node，
 * 64 段 StampedLock——**get 用乐观读：tryOptimisticRead 无锁快路径 + validate 失败
 * 退化 readLock**——传播实际 get:put ≈ 85:15 读多——基准 put15% 场景
 * Stamp64 198-249M vs Sync64 150-213M，+32%——get 方法 CPU 降。
 *
 * 并发：写操作 put/putIfAbsent/remove/computeIfAbsent 在 writeLock 段内互斥；
 * get 乐观读 validate 保证结构一致；写中读则 validate 失败 → readLock 重读；
 * 值对象内部状态即 DataLayer 内容由调用方管理，返回引用后锁外读写，与
 * synchronized 版/CHM 语义一致，非新问题；swap 双 map 窗口在引用级。
 * StampedLock 不可重入——方法内不嵌套，mapping 不回调 map。
 */
public final class Long2ObjectStripedMap<V> {

    private static final int SEG_BITS = 8;
    private static final int SEG_COUNT = 1 << SEG_BITS;
    private static final int SEG_MASK = SEG_COUNT - 1;

    private final Long2ObjectOpenHashMap<V>[] segments;
    private final StampedLock[] locks;

    /** expectedCapacity = 预期条目峰值；每段容量 = peak/64，预分配防扩容。 */
    @SuppressWarnings("unchecked")
    public Long2ObjectStripedMap(int expectedCapacity) {
        int perSeg = Math.max(16, (expectedCapacity >> SEG_BITS) + 1);
        segments = new Long2ObjectOpenHashMap[SEG_COUNT];
        locks = new StampedLock[SEG_COUNT];
        for (int i = 0; i < SEG_COUNT; i++) {
            segments[i] = new Long2ObjectOpenHashMap<>(perSeg);
            locks[i] = new StampedLock();
        }
    }

    /** 分段：key 高/低位混合后取低 6 位，与 CHM spread 同思路，均匀。 */
    private int seg(long key) {
        return (int) ((key ^ (key >>> 32)) & SEG_MASK);
    }

    /**
     * 乐观读是无锁快路径——写中读则 validate 失败 → readLock 重读，保证一致。
     * fastutil 的 key 数组非 volatile：rehash（扩容）时乐观读可能看到"新数组 + 旧 mask"
     * 的中间态 → get 内部索引越界抛 AIOOBE（异常发生在 validate 之前）→ catch 后走读锁
     * 重读（rehash 与写/读锁互斥，读锁内必然一致）。
     */
    public V get(long key) {
        StampedLock l = locks[seg(key)];
        long stamp = l.tryOptimisticRead();
        V v;
        try {
            v = segments[seg(key)].get(key);
        } catch (ArrayIndexOutOfBoundsException e) {
            // rehash 中间态：乐观读失效，直接读锁重读（rehash 与写锁互斥，必无此态）
            stamp = l.readLock();
            try {
                v = segments[seg(key)].get(key);
            } finally {
                l.unlockRead(stamp);
            }
            return v;
        }
        if (!l.validate(stamp)) {
            stamp = l.readLock();
            try {
                v = segments[seg(key)].get(key);
            } finally {
                l.unlockRead(stamp);
            }
        }
        return v;
    }

    public V put(long key, V val) {
        StampedLock l = locks[seg(key)];
        long stamp = l.writeLock();
        try {
            return segments[seg(key)].put(key, val);
        } finally {
            l.unlockWrite(stamp);
        }
    }

    /** 返回被删旧值；null 表示不存在。 */
    public V remove(long key) {
        StampedLock l = locks[seg(key)];
        long stamp = l.writeLock();
        try {
            return segments[seg(key)].remove(key);
        } finally {
            l.unlockWrite(stamp);
        }
    }

    /** 返回旧值；null 表示首次——语义与 CHM.putIfAbsent 一致，logConflict 依赖此语义。 */
    public V putIfAbsent(long key, V val) {
        StampedLock l = locks[seg(key)];
        long stamp = l.writeLock();
        try {
            return segments[seg(key)].putIfAbsent(key, val);
        } finally {
            l.unlockWrite(stamp);
        }
    }

    /** 已存在则不重算，storage.getOrCreate / LightTaskLock 依赖此行为。mapping 轻量且不回调 map。 */
    public V computeIfAbsent(long key, LongFunction<V> mapping) {
        StampedLock l = locks[seg(key)];
        long stamp = l.writeLock();
        try {
            Long2ObjectOpenHashMap<V> m = segments[seg(key)];
            V v = m.get(key);
            if (v == null) {
                v = mapping.apply(key);
                m.put(key, v);
            }
            return v;
        } finally {
            l.unlockWrite(stamp);
        }
    }

    public boolean containsKey(long key) {
        StampedLock l = locks[seg(key)];
        long stamp = l.readLock();
        try {
            return segments[seg(key)].containsKey(key);
        } finally {
            l.unlockRead(stamp);
        }
    }

    /** 逐段写锁内 removeIf，用于 section/aquifer 的 lastAccess TTL 清理，低频。 */
    public void removeIf(Predicate<V> predicate) {
        for (int i = 0; i < SEG_COUNT; i++) {
            StampedLock l = locks[i];
            long stamp = l.writeLock();
            try {
                segments[i].values().removeIf(predicate);
            } finally {
                l.unlockWrite(stamp);
            }
        }
    }

    public int size() {
        int n = 0;
        for (int i = 0; i < SEG_COUNT; i++) {
            StampedLock l = locks[i];
            long stamp = l.readLock();
            try {
                n += segments[i].size();
            } finally {
                l.unlockRead(stamp);
            }
        }
        return n;
    }

    public boolean isEmpty() {
        for (int i = 0; i < SEG_COUNT; i++) {
            StampedLock l = locks[i];
            long stamp = l.readLock();
            try {
                if (!segments[i].isEmpty()) {
                    return false;
                }
            } finally {
                l.unlockRead(stamp);
            }
        }
        return true;
    }
}
