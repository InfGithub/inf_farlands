package com.inf.farlands.mixin.noise;

import net.minecraft.world.level.levelgen.Heightmap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Heightmap 的 @Invoker 访问器——CarverFiller 自研 prime 直调 private setHeight。
 *
 * <p>vanilla primeHeightmaps 扫描范围 = [getHighestSectionPosition+16, minBuildHeight]，
 * mod 窗口语义下 getHighestSectionPosition 在极端 Y 可达 2.14B → 21 亿次扫描炸弹，
 * 不能直接调 vanilla——自研 prime（列掩码 + 非空 section 降序）用 @Invoker 写入。
 */
@Mixin(Heightmap.class)
public interface HeightmapInvoker {
    @Invoker("setHeight")
    void farlands$setHeight(int x, int z, int y);
}
