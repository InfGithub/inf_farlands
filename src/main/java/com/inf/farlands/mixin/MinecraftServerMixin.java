package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    // 方法：
    // public int getAbsoluteMaxWorldSize() {
    //     return 29999984;
    // }

    
    @Overwrite
    public int getAbsoluteMaxWorldSize() {
        return Config.borderAbsoluteMax - 16; // chunk
    }
}
