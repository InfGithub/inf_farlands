package com.inf.farlands.terrain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 雕刻系统：决定目标 chunk 的洞穴/峡谷雕刻来源与应用方式。
 *
 * <p>与地形/群系/地表并列的第四 system 家族——CARVERS 是独立阶段（SURFACE 之后、
 * 光照之前），复用 vanilla WorldCarver/ConfiguredWorldCarver/CarvingContext 机制。
 *
 * <p>由 {@link com.inf.farlands.terrain.system.CarverSystemRegistry} 按维度 Config 选择
 * 实现并持有单例。实现必须无状态、纯方法——applyCarvers 跑 genPool 多线程，实例被
 * 多个 carver 任务共享（局部构造 CarvingContext/NoiseChunk/WorldgenRandom，不存共享
 * 可变字段）。
 *
 * <p>维度分派：实现内部必须 {@code NoiseFillerContext.set(维度)} + finally clear——
 * 维度全高 NoiseChunk 构造会走 NoiseChunkMixin @Redirect finalDensity/aquifer 按维度
 * 分派，set 错维度会构造错系统（运行期才爆，同 SurfaceSystem）。
 *
 * <p>雕刻语义（vanilla 机制）：目标 chunk 为中心 ±8 chunk 起点网格（289 次调用），
 * 每起点从 biomeSource 查 carver 列表（不依赖邻居 chunk 数据）、setLargeFeatureSeed
 * 确定性随机、carvingMask 去重。carve 只替换 config.replaceable（stone）；替换方块
 * 由 aquifer.computeSubstance 决定（空气/水/岩浆）。
 */
public interface CarverSystem {

    /** 对目标 chunk 应用全部 carver（17×17 起点网格），genPool 线程。状态推进（升
     * CARVERS）与脏标记、高度图 prime 由编排层（CarverFiller）负责，本接口只做纯应用。 */
    void applyCarvers(ServerLevel level, ChunkAccess chunk);
}
