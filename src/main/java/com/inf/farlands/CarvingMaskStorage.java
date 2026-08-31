package com.inf.farlands;

import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.GenerationStep;

/**
 * carvingMask 存储接口，由 ChunkAccessMixin 注入到 ChunkAccess。
 *
 * <p>vanilla 的 carvingMasks 在 ProtoChunk 上（step → CarvingMask map），mod 短路后
 * chunk 是 LevelChunk（无此字段）。接口注入（仿 WindowedChunk 模式）：
 * 目标 chunk 生成期 17×17 起点 carver 调用的同格去重；LIGHTED 后可丢弃（mod 无
 * FEATURES 地物，CarvingMaskPlacement 不需要），内存有界。
 */
public interface CarvingMaskStorage {

    /** 取该 step 的 carving mask，不存在返回 null。 */
    CarvingMask getCarvingMask(GenerationStep.Carving step);

    /** 取或创建（懒建：CarvingMask(getHeight(), getMinBuildHeight())，维度带范围）。 */
    CarvingMask getOrCreateCarvingMask(GenerationStep.Carving step);

    /** 显式设置（调试/未来用途）。 */
    void setCarvingMask(GenerationStep.Carving step, CarvingMask mask);
}
