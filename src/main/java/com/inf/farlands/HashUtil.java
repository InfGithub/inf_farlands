package com.inf.farlands;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        return xb ^ ha;
    }

    // 双 Map

    private static volatile Map<Long, IntBlockPos> blockLookup = new ConcurrentHashMap<>();
    private static volatile Map<Long, IntBlockPos> oldBlockLookup;

    public static void putBlock(long key, IntBlockPos val) {
        IntBlockPos prev = blockLookup.putIfAbsent(key, val);
        if (prev != null && (prev.x != val.x || prev.y != val.y || prev.z != val.z)) {
            logConflict("block", key, prev.x, prev.y, prev.z, val.x, val.y, val.z);
        }
    }

    public static IntBlockPos getBlock(long key) {
        IntBlockPos bp = blockLookup.get(key);
        if (bp != null) return bp;
        Map<Long, IntBlockPos> old = oldBlockLookup;
        return old != null ? old.get(key) : null;
    }

    public static void swapBlockLookup() {
        oldBlockLookup = blockLookup;
        blockLookup = new ConcurrentHashMap<>();
    }

    // ------------------------------------------------------------
    // Aquifer 专用持久表：aquiferLocationCache 跨 tick 长期反查 block 哈希 key，
    // 通用 blockLookup（swap 瞬态，400 tick 无条件丢弃）会 miss → fallback 位解码
    // 垃圾坐标污染。专用表用 lastAccess 保活（生成期活跃不清理），反查不 miss。

    private static final Map<Long, IntBlockPos> aquiferLookup = new ConcurrentHashMap<>();

    public static IntBlockPos getAquiferBlock(long key) {
        IntBlockPos bp = aquiferLookup.get(key);
        if (bp != null) {
            bp.lastAccess = InfFarlands.getServerTickCount();
        }
        return bp;
    }

    public static void putAquiferBlock(long key, IntBlockPos val) {
        val.lastAccess = InfFarlands.getServerTickCount();
        aquiferLookup.put(key, val);
    }

    public static void trimAquiferLookup(long currentTick) {
        long cutoff = currentTick - 600;
        aquiferLookup.values().removeIf(p -> p.lastAccess < cutoff);
    }

    // ------------------------------------------------------------

    public static long hashSection(long x, long y, long z) {
        return hashPos(x, y, z);
    }
// ------------------------------------------------------------

    public static final Map<Long, IntSectionPos> sectionLookup = new ConcurrentHashMap<>();

    public static void putSection(long key, IntSectionPos val) {
        IntSectionPos prev = sectionLookup.putIfAbsent(key, val);
        if (prev != null && (prev.x != val.x || prev.y != val.y || prev.z != val.z)) {
            logConflict("section", key, prev.x, prev.y, prev.z, val.x, val.y, val.z);
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
        sectionLookup.values().removeIf(p -> p.lastAccess < cutoff);
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

    /**直写 queuedSections（绕过 ThreadedLevelLightEngine 异步任务队列） */
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

    /**直调 createDataLayer（server thread 建层入 queued） */
    public static DataLayer callCreateDataLayer(Object storage, long sectionPos) {
        try {
            return (DataLayer) M_CREATE_DATA_LAYER.invoke(storage, sectionPos);
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }

    // ============ Light Engine helpers（Light Engine windowing）============
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

            // setStoredLevel —— 定义在 LayerLightSectionStorage（sky/block 共用）
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

    /**入队 PRE_UPDATE 任务（直写 lightTasks + tryScheduleUpdate 触发调度） */
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
