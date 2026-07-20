package com.inf.farlands;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;

public class HashUtil {

    public static long hashPos(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= Long.rotateLeft(y * 0x9E3779B97F4A7C15L, 31);
        h ^= Long.rotateLeft(z * 0x9E3779B97F4A7C15L, 33);
        return h;
    }

    public static final Map<Long, IntBlockPos> blockLookup = new ConcurrentHashMap<>();
    public static final Map<Long, IntSectionPos> sectionLookup = new ConcurrentHashMap<>();

    /** Drop half the side-channel entries. Called periodically to bound memory. */
    public static void trimLookups() {
        int blockSize = blockLookup.size();
        int sectionSize = sectionLookup.size();
        if (blockSize > 100_000) {
            var it = blockLookup.entrySet().iterator();
            int target = blockSize / 2;
            int count = 0;
            while (it.hasNext() && count < target) {
                it.next();
                it.remove();
                count++;
            }
        }
        if (sectionSize > 100_000) {
            var it = sectionLookup.entrySet().iterator();
            int target = sectionSize / 2;
            int count = 0;
            while (it.hasNext() && count < target) {
                it.next();
                it.remove();
                count++;
            }
        }
    }

    // ---- Reflection-based access to LayerLightSectionStorage protected methods ----

    private static final Method GET_DATA_LAYER;
    private static final Method GET_DATA_LAYER_TO_WRITE;
    static {
        try {
            GET_DATA_LAYER = LayerLightSectionStorage.class
                .getDeclaredMethod("getDataLayer", long.class, boolean.class);
            GET_DATA_LAYER.setAccessible(true);
            GET_DATA_LAYER_TO_WRITE = LayerLightSectionStorage.class
                .getDeclaredMethod("getDataLayerToWrite", long.class);
            GET_DATA_LAYER_TO_WRITE.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static DataLayer callGetDataLayer(Object storage, long key, boolean cached) {
        try {
            return (DataLayer) GET_DATA_LAYER.invoke(storage, key, cached);
        } catch (Exception e) {
            return null;
        }
    }

    public static DataLayer callGetDataLayerToWrite(Object storage, long key) {
        try {
            return (DataLayer) GET_DATA_LAYER_TO_WRITE.invoke(storage, key);
        } catch (Exception e) {
            return null;
        }
    }

    public static long hashSection(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= Long.rotateLeft(z * 0x9E3779B97F4A7C15L, 21);
        h ^= Long.rotateLeft(y * 0x9E3779B97F4A7C15L, 42);
        return h;
    }

}
