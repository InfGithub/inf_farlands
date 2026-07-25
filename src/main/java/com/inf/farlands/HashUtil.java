package com.inf.farlands;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HashUtil {
    public static long hashPos(long x, long y, long z) {
        long h = x * 0x9E3779B97F4A7C15L;
        h ^= Long.rotateLeft(y * 0x9E3779B97F4A7C15L, 31);
        h ^= Long.rotateLeft(z * 0x9E3779B97F4A7C15L, 33);
        return h;
    }

    public static final Map<Long, IntBlockPos> blockLookup = new ConcurrentHashMap<>();

    public static void putBlock(long key, IntBlockPos val) {
        blockLookup.put(key, val);
    }

    public static IntBlockPos getBlock(long key) {
        return blockLookup.get(key);
    }

    // --------------------------------------------

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
        blockLookup.clear();
        sectionLookup.values().removeIf(p -> p.lastAccess < cutoff);
    }
}
