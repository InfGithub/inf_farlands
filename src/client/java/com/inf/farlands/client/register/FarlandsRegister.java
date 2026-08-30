package com.inf.farlands.client.register;

import com.inf.farlands.client.register.packet.*;

public class FarlandsRegister {
    public static void register() {
        ClampStatePacketRegister.register();
        ClampTogglePacketRegister.register();
    }
}
