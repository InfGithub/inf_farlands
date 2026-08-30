package com.inf.farlands.register;

import com.inf.farlands.register.packet.*;

public class FarlandsRegister {
    public static void registerStatic() {
        ClampStatePacketRegister.registerType();
        ClampTogglePacketRegister.registerType();
    }

    public static void register() {
        ClampTogglePacketRegister.registerHanlder();
    }
}
