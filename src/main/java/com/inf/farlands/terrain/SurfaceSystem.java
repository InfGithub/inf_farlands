package com.inf.farlands.terrain;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 地表系统：决定 chunk 的地表规则来源与应用方式。
 *
 * <p>与地形系统（{@link TerrainSystem}）、群系系统（{@link BiomeSystem}）并列的第三
 * system 家族——surface 是独立阶段（NOISE 之后、光照之前），依赖 fill 的高度图产物
 * （WG 高度图由 fill 更新，surface 从高度图顶向下列扫描）。
 *
 * <p>由 {@link com.inf.farlands.terrain.system.SurfaceSystemRegistry} 按维度 Config 选择
 * 实现并持有单例。实现必须无状态、纯方法——applySurface 跑 genPool 多线程，实例被
 * 多个 surface 任务共享（局部构造 NoiseChunk/BiomeManager/Context，不存共享可变字段）。
 *
 * <p>维度分派：实现内部必须 {@code NoiseFillerContext.set(维度)} + finally clear——
 * 维度 NoiseChunk 构造会走 NoiseChunkMixin @Redirect finalDensity/aquifer 按维度分派，
 * set 错维度会构造错系统（运行期才爆）。
 */
public interface SurfaceSystem {

    /** 应用地表规则到该 chunk（16×16 列扫描），genPool 线程。状态推进（升 SURFACE）与
     * 脏标记由编排层（SurfaceFiller）负责，本接口只做纯应用。 */
    void applySurface(ServerLevel level, ChunkAccess chunk);
}
