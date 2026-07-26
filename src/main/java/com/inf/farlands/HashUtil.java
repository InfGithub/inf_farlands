package com.inf.farlands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;

public class HashUtil {
    public static long hashPos(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= Long.rotateLeft(y * 0x9E3779B97F4A7C15L, 31);
        h ^= Long.rotateLeft(z * 0x9E3779B97F4A7C15L, 33);
        return h;
    }

    // 双 Map

    private static volatile Map<Long, IntBlockPos> blockLookup = new ConcurrentHashMap<>();
    private static volatile Map<Long, IntBlockPos> oldBlockLookup;

    public static void putBlock(long key, IntBlockPos val) {
        blockLookup.put(key, val);
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

    public static long hashSection(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= Long.rotateLeft(z * 0x9E3779B97F4A7C15L, 21);
        h ^= Long.rotateLeft(y * 0x9E3779B97F4A7C15L, 42);
        return h;
    }

    public static final Map<Long, IntSectionPos> sectionLookup = new ConcurrentHashMap<>();

    public static void putSection(long key, IntSectionPos val) {
        sectionLookup.put(key, val);
    }

    public static IntSectionPos getSection(long key) {
        return sectionLookup.get(key);
    }

    // --------------------------------------------

    public static void trimLookups(long currentTick) {
        long cutoff = currentTick - 600;
        swapBlockLookup();
        sectionLookup.values().removeIf(p -> p.lastAccess < cutoff);
    }

    // --------------------------------------------

    private static final Method GET_DATA_LAYER;
    private static final Method GET_DATA_LAYER_TO_WRITE;

    static {
        try {
            GET_DATA_LAYER = LayerLightSectionStorage.class.getDeclaredMethod("getDataLayer", long.class, boolean.class);
            GET_DATA_LAYER.setAccessible(true);
            GET_DATA_LAYER_TO_WRITE = LayerLightSectionStorage.class
                    .getDeclaredMethod("getDataLayerToWrite", long.class);
            GET_DATA_LAYER_TO_WRITE.setAccessible(true);
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
}
