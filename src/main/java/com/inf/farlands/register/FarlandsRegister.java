package com.inf.farlands.register;

import com.inf.farlands.register.packet.*;

public class FarlandsRegister {
    public static void register() {
        ClampStatePacketRegister.register();
        ClampTogglePacketRegister.register();
    }
}
