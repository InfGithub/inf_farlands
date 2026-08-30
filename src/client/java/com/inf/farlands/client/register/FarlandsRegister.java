package com.inf.farlands.client.register;

import com.inf.farlands.client.register.packet.*;

public class FarlandsRegister {
    public static void registerStatic() {
        ClampStatePacketRegister.registerType();
        ClampTogglePacketRegister.registerType();
    }

    public static void register() {
        ClampStatePacketRegister.registerHanlder();
    }
}
