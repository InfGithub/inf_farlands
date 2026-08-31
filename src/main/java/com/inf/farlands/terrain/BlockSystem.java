package com.inf.farlands.terrain;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;

/**
 * 逐块几何地形系统：fillBlock 每格直接判定，绕过密度链。
 *
 * 分形/迷宫/Mandelbulb 等确定性几何——与种子无关，非噪声采样。
 * OverworldNoiseFiller 分派：fillBlock 恒非 null → 密度链（getInterpolatedState）永不执行，
 * 因此本类无 createFinalDensity；NoiseChunk 构造的 finalDensity 由调用方给廉价占位。
 */
public interface BlockSystem extends TerrainSystem {

    /**
     * 块级填充器，逐格几何判定。返回 AIR = 空、方块 = 实。
     * 密度链是 cell 角点采样 + 三线性插值，格点级分形会被磨成斜坡——
     * 分形系统必须逐格判定。
     */
    BlockState fillBlock(int x, int y, int z);

    /**
     * 空气 aquifer（覆写基默认 null）：fillBlock 短路下密度链不参与，vanilla aquifer
     * 纯构造浪费且极端 Y 有 gridY 隐患；恒 AIR 且非 null → MaterialRuleList 短路，
     * 矿脉规则不执行。与 VOID 同模式。
     */
    @Override
    default Aquifer createAquifer(NoiseChunk chunk, ChunkPos pos, NoiseRouter router,
            PositionalRandomFactory random, int minY, int height, Aquifer.FluidPicker picker) {
        return new Aquifer() {
            @Override
            public BlockState computeSubstance(DensityFunction.FunctionContext context, double substance) {
                return Blocks.AIR.defaultBlockState();
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }
}
