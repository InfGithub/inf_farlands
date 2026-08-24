package com.inf.farlands.mixin.axisY;

import java.io.IOException;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ThreadedLevelLightEngine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 保证 lightEngine.close 一定执行：vanilla close 里 save(true) 抛异常/卡住会跳过
 * lightEngine.close → 光照线程池泄漏（多引擎累积）。try/finally 让 save 无论成败
 * 都关闭引擎与 chunkMap。
 */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Shadow
    private ThreadedLevelLightEngine lightEngine;

    @Shadow
    public ChunkMap chunkMap;

    @Shadow
    public void save(boolean flush) {
    }

    @Overwrite
    public void close() throws IOException {
        try {
            this.save(true);
        } finally {
            this.lightEngine.close();
            this.chunkMap.close();
        }
    }
}