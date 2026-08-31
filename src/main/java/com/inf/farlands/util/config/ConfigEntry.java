package com.inf.farlands.util.config;

import java.util.Map;

/**
 * 静态持有，读写经 Gson 分派。
 *
 * @param <T> 值类型：int/long/short/byte/float/double/String/Enum/Boolean
 */
public final class ConfigEntry<T> {
    /** 配置项名：仅允许 a-zA-Z0-9_-。 */
    public static boolean isValidName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                return false;
            }
        }
        return name.length() > 0;
    }

    private final String name;
    private final Map<String, String> notes; // 语言 -> 注释
    private final Class<?> type; // 值类型，枚举用 Enum 类
    private final T defaultValue;
    private T value;

    public ConfigEntry(String name, Map<String, String> notes, Class<?> type, T defaultValue) {
        if (!isValidName(name)) {
            throw new IllegalArgumentException("Invalid config entry name: %s".formatted(name));
        }
        this.name = name;
        this.notes = Map.copyOf(notes);
        this.type = type;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public String name() {
        return name;
    }

    public Map<String, String> notes() {
        return notes;
    }

    public Class<?> type() {
        return type;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public T get() {
        return value;
    }

    public void set(T v) {
        this.value = v;
    }
}
