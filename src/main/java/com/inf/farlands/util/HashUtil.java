package com.inf.farlands.util;

import com.inf.farlands.InfFarlands;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectList;

import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.BlockLightEngine;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.lighting.SkyLightEngine;
import net.minecraft.world.level.lighting.SkyLightSectionStorage;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class HashUtil {
    public static long hashPos(long x, long y, long z) {
        long xb = ((y & 0xFFFFFFFFL) << 32) | (z & 0xFFFFFFFFL);
        long ha = x & 0xFFFFFFFFL;
        ha ^= ha >>> 33;
        ha *= 0xFF51AFD7ED558CCDL;
        ha ^= ha >>> 33;
        ha *= 0xC4CEB9FE1A85EC53L;
        ha ^= ha >>> 33;
        long h = xb ^ ha;
        // 外层 finalizer 即 fmix 双射包层，保持 P(y,z)^fmix(x) 无仿射恒等碰撞；
        // 证明的关键是碰撞方程不变，完整 key 高位充分混合 → CHM 桶分布均匀——
        // 消除塔顶坐标的桶树化：JFR 193G comparableClassFor 反射，树化桶
        // section 800→0、block 24576→38。
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    // 双 Map：Long2ObjectStripedMap 无装箱分段 map，消 CHM Long key 装箱 0.67G/s

    private static final int INITIAL_BLOCK_CAP = 1 << 23; // 8M 初始容量；峰值 ~29M 时锁内扩容，4M 时每段多 1 次 rehash，JFR 31.5G

    private static volatile Long2ObjectStripedMap<IntBlockPos> blockLookup = new Long2ObjectStripedMap<>(INITIAL_BLOCK_CAP);
    private static volatile Long2ObjectStripedMap<IntBlockPos> oldBlockLookup;

    public static void putBlock(long key, int x, int y, int z) {
        IntBlockPos prev = blockLookup.get(key);
        if (prev != null) {
            if (prev.x != x || prev.y != y || prev.z != z) {
                logConflict("block", key, prev.x, prev.y, prev.z, x, y, z);
            }
            return;
        }
        IntBlockPos np = new IntBlockPos(x, y, z);
        prev = blockLookup.putIfAbsent(key, np);
        if (prev != null) {
            // 竞态：并发注册，np 成垃圾，量小
            if (prev.x != x || prev.y != y || prev.z != z) {
                logConflict("block", key, prev.x, prev.y, prev.z, x, y, z);
            }
        }
    }

    public static IntBlockPos getBlock(long key) {
        IntBlockPos bp = blockLookup.get(key);
        if (bp != null) return bp;
        Long2ObjectStripedMap<IntBlockPos> old = oldBlockLookup;
        return old != null ? old.get(key) : null;
    }

    public static void swapBlockLookup() {
        oldBlockLookup = blockLookup;
        blockLookup = new Long2ObjectStripedMap<>(INITIAL_BLOCK_CAP);
    }

    // ------------------------------------------------------------
    // Aquifer 专用持久表：aquiferLocationCache 跨 tick 长期反查 block 哈希 key，
    // 通用 blockLookup 是 swap 瞬态，400 tick 无条件丢弃，会 miss → fallback 位解码
    // 垃圾坐标污染。专用表用 lastAccess 保活，生成期活跃不清理，反查不 miss。
    // 值类型 AquiferPos 含 lastAccess，与 IntBlockPos 分离；block 空间无 lastAccess
    // 原设计，恢复误加字段前的设计。

    private static final Long2ObjectStripedMap<AquiferPos> aquiferLookup = new Long2ObjectStripedMap<>(1 << 20);

    public static AquiferPos getAquiferBlock(long key) {
        AquiferPos bp = aquiferLookup.get(key);
        if (bp != null) {
            bp.lastAccess = InfFarlands.getServerTickCount();
        }
        return bp;
    }

    public static void putAquiferBlock(long key, int x, int y, int z) {
        AquiferPos prev = aquiferLookup.get(key);
        if (prev != null) {
            // 保活配合 TTL 600 trim：与现状每次 put 更新 lastAccess 语义一致
            prev.lastAccess = InfFarlands.getServerTickCount();
            return;
        }
        AquiferPos np = new AquiferPos(x, y, z);
        np.lastAccess = InfFarlands.getServerTickCount();
        AquiferPos race = aquiferLookup.putIfAbsent(key, np);
        if (race != null) {
            // 竞态：并发注册，np 成垃圾；现有条目保活
            race.lastAccess = InfFarlands.getServerTickCount();
        }
    }

    public static void trimAquiferLookup(long currentTick) {
        long cutoff = currentTick - 600;
        aquiferLookup.removeIf(p -> p.lastAccess < cutoff);
    }

    // ------------------------------------------------------------

    public static long hashSection(long x, long y, long z) {
        return hashPos(x, y, z);
    }
// ------------------------------------------------------------

    public static final Long2ObjectStripedMap<IntSectionPos> sectionLookup = new Long2ObjectStripedMap<>(1 << 20);

    public static void putSection(long key, int x, int y, int z) {
        IntSectionPos prev = sectionLookup.get(key);
        if (prev != null) {
            if (prev.x != x || prev.y != y || prev.z != z) {
                logConflict("section", key, prev.x, prev.y, prev.z, x, y, z);
            }
            return;
        }
        IntSectionPos np = new IntSectionPos(x, y, z);
        prev = sectionLookup.putIfAbsent(key, np);
        if (prev != null) {
            if (prev.x != x || prev.y != y || prev.z != z) {
                logConflict("section", key, prev.x, prev.y, prev.z, x, y, z);
            }
        }
    }

    public static IntSectionPos getSection(long key) {
        return sectionLookup.get(key);
    }

    // --------------------------------------------

    private static volatile long lastConflictLog = 0;

    private static void logConflict(String kind, long key, int ox, int oy, int oz, int nx, int ny, int nz) {
        long now = System.currentTimeMillis();
        long last = lastConflictLog;
        if (now - last < 1000) {
            return;
        }
        lastConflictLog = now;
        InfFarlands.LOGGER.info("HASHCONFLICT {} key=0x{} old={},{},{} new={},{},{}",
                kind, Long.toHexString(key), ox, oy, oz, nx, ny, nz);
    }

    // --------------------------------------------

    public static void trimLookups(long currentTick) {
        long cutoff = currentTick - 600;
        swapBlockLookup();
        sectionLookup.removeIf(p -> p.lastAccess < cutoff);
        trimAquiferLookup(currentTick);
    }

    // ============ LayerLightSectionStorage helpers ============

    private static final Method GET_DATA_LAYER;
    private static final Method GET_DATA_LAYER_TO_WRITE;
    private static final Method M_UPDATE_SECTION_STATUS;
    private static final Method M_CREATE_DATA_LAYER;

    static {
        try {
            GET_DATA_LAYER = LayerLightSectionStorage.class.getDeclaredMethod("getDataLayer", long.class, boolean.class);
            GET_DATA_LAYER.setAccessible(true);
            GET_DATA_LAYER_TO_WRITE = LayerLightSectionStorage.class
                    .getDeclaredMethod("getDataLayerToWrite", long.class);
            GET_DATA_LAYER_TO_WRITE.setAccessible(true);
            M_UPDATE_SECTION_STATUS = LayerLightSectionStorage.class
                    .getDeclaredMethod("updateSectionStatus", long.class, boolean.class);
            M_UPDATE_SECTION_STATUS.setAccessible(true);
            M_CREATE_DATA_LAYER = LayerLightSectionStorage.class
                    .getDeclaredMethod("createDataLayer", Long.TYPE);
            M_CREATE_DATA_LAYER.setAccessible(true);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    public static DataLayer callGetDataLayer(Object storage, long key, boolean cached) {
        try {
            return (DataLayer) GET_DATA_LAYER.invoke(storage, key, cached);
        } catch (Exception error) {
            return null;
        }
    }

    public static DataLayer callGetDataLayerToWrite(Object storage, long key) {
        try {
            return (DataLayer) GET_DATA_LAYER_TO_WRITE.invoke(storage, key);
        } catch (Exception error) {
            return null;
        }
    }

    public static void callUpdateSectionStatus(Object storage, long sectionPos, boolean isEmpty) {
        try {
            M_UPDATE_SECTION_STATUS.invoke(storage, sectionPos, isEmpty);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    /**直写 queuedSections，绕过 ThreadedLevelLightEngine 异步任务队列 */
    private static final Method M_QUEUE_SECTION_DATA;

    static {
        try {
            M_QUEUE_SECTION_DATA = LayerLightSectionStorage.class
                    .getDeclaredMethod("queueSectionData", Long.TYPE, DataLayer.class);
            M_QUEUE_SECTION_DATA.setAccessible(true);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    public static void callQueueSectionData(Object storage, long sectionPos, DataLayer data) {
        try {
            M_QUEUE_SECTION_DATA.invoke(storage, sectionPos, data);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    /**直调 createDataLayer，由 server thread 建层入 queued */
    public static DataLayer callCreateDataLayer(Object storage, long sectionPos) {
        try {
            return (DataLayer) M_CREATE_DATA_LAYER.invoke(storage, sectionPos);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    // ============ Light Engine windowing helpers ============
    private static final Field F_STORAGE;
    private static final Method M_ENQUEUE_INC;
    private static final Method M_ENQUEUE_DEC;
    private static final Method M_SET_STORED_LEVEL;
    private static final long ADD_SKY_SOURCE_ENTRY;
    private static final Method M_ADD_TASK;
    private static final Method M_RUN_UPDATE;
    private static final Field F_LIGHT_TASKS;
    private static final Object PRE_UPDATE;

    static {
        try {
            // 所有 LightEngine 的 storage 字段
            F_STORAGE = LightEngine.class.getDeclaredField("storage");
            F_STORAGE.setAccessible(true);

            // LightEngine 的入队方法
            M_ENQUEUE_INC = LightEngine.class.getDeclaredMethod("enqueueIncrease", Long.TYPE, Long.TYPE);
            M_ENQUEUE_INC.setAccessible(true);
            M_ENQUEUE_DEC = LightEngine.class.getDeclaredMethod("enqueueDecrease", Long.TYPE, Long.TYPE);
            M_ENQUEUE_DEC.setAccessible(true);

            // setStoredLevel —— 定义在 LayerLightSectionStorage，sky/block 共用
            M_SET_STORED_LEVEL = LayerLightSectionStorage.class
                    .getDeclaredMethod("setStoredLevel", Long.TYPE, Integer.TYPE);
            M_SET_STORED_LEVEL.setAccessible(true);

            // ADD_SKY_SOURCE_ENTRY —— SkyLightEngine 上的静态 long
            Field f = SkyLightEngine.class.getDeclaredField("ADD_SKY_SOURCE_ENTRY");
            f.setAccessible(true);
            ADD_SKY_SOURCE_ENTRY = f.getLong(null);

            // ThreadedLevelLightEngine 任务调度
            Class<?> taskTypeClass = Class
                    .forName("net.minecraft.server.level.ThreadedLevelLightEngine$TaskType");

            PRE_UPDATE = Enum.valueOf((Class) taskTypeClass, "PRE_UPDATE");
            M_ADD_TASK = ThreadedLevelLightEngine.class.getDeclaredMethod(
                    "addTask", int.class, int.class, taskTypeClass, Runnable.class);
            M_ADD_TASK.setAccessible(true);
            M_RUN_UPDATE = ThreadedLevelLightEngine.class.getDeclaredMethod("runUpdate");
            M_RUN_UPDATE.setAccessible(true);
            F_LIGHT_TASKS = ThreadedLevelLightEngine.class.getDeclaredField("lightTasks");
            F_LIGHT_TASKS.setAccessible(true);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    // ---- Sky ----

    public static SkyLightSectionStorage skyStorage(SkyLightEngine engine) {
        try {
            return (SkyLightSectionStorage) F_STORAGE.get(engine);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void skyEnqueueInc(SkyLightEngine engine, long pos, long entry) {
        try {
            M_ENQUEUE_INC.invoke(engine, pos, entry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void skyEnqueueDec(SkyLightEngine engine, long pos, long entry) {
        try {
            M_ENQUEUE_DEC.invoke(engine, pos, entry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void skySetStoredLevel(SkyLightEngine engine, long pos, int level) {
        try {
            M_SET_STORED_LEVEL.invoke(skyStorage(engine), pos, level);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static long skyAddSourceEntry() {
        return ADD_SKY_SOURCE_ENTRY;
    }

    // ---- Block ----

    public static Object blockStorage(BlockLightEngine engine) {
        try {
            return F_STORAGE.get(engine);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void blockEnqueueInc(BlockLightEngine engine, long pos, long entry) {
        try {
            M_ENQUEUE_INC.invoke(engine, pos, entry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void blockEnqueueDec(BlockLightEngine engine, long pos, long entry) {
        try {
            M_ENQUEUE_DEC.invoke(engine, pos, entry);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void blockSetStoredLevel(BlockLightEngine engine, long pos, int level) {
        try {
            M_SET_STORED_LEVEL.invoke(blockStorage(engine), pos, level);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- ThreadedLevelLightEngine ----

    /**入队 PRE_UPDATE 任务：直写 lightTasks + tryScheduleUpdate 触发调度 */
    public static void enqueuePreUpdateTask(ThreadedLevelLightEngine engine, int cx, int cz, Runnable task) {
        try {
            ObjectList lightTasks =
                    (ObjectList) F_LIGHT_TASKS.get(engine);
            lightTasks.add(Pair.of(PRE_UPDATE, task));
            engine.tryScheduleUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
