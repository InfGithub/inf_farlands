package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {

    @Overwrite
    public int getAbsoluteMaxWorldSize() {
        return Config.borderAbsoluteMax;
    }
}
