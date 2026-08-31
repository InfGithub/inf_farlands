package com.inf.farlands.mixin.noise;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * NoiseChunk 的 @Invoker 访问器——OverworldNoiseFiller.fillCellColumn 直调替代反射。
 */
@Mixin(NoiseChunk.class)
public interface NoiseChunkInvoker {
    @Invoker("cellWidth")
    int farlands$cellWidth();

    @Invoker("cellHeight")
    int farlands$cellHeight();

    @Invoker("selectCellYZ")
    void farlands$selectCellYZ(int y, int z);

    @Invoker("advanceCellX")
    void farlands$advanceCellX(int increment);

    @Invoker("initializeForFirstCellX")
    void farlands$initializeForFirstCellX();

    @Invoker("updateForY")
    void farlands$updateForY(int cellEndBlockY, double y);

    @Invoker("updateForX")
    void farlands$updateForX(int cellEndBlockX, double x);

    @Invoker("updateForZ")
    void farlands$updateForZ(int cellEndBlockZ, double z);

    @Invoker("getInterpolatedState")
    BlockState farlands$getInterpolatedState();

    @Invoker("aquifer")
    Aquifer farlands$aquifer();
}