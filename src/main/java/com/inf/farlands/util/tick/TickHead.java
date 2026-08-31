package com.inf.farlands.util.tick;

public class TickHead {
    private static int now = 0;

    public static void head(int tickCount) {
        now = tickCount;
    }

    public static int getNow() {
        return now;
    }
}
