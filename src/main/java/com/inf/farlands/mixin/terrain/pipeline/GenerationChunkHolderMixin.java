package com.inf.farlands.mixin.terrain.pipeline;

import com.inf.farlands.InfFarlands;
import com.inf.farlands.terrain.pipeline.FarLandsGenState;
import com.inf.farlands.terrain.pipeline.GenQueue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.Util;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 地形管线：拦截 vanilla chunk 生成调度，短路到 FULL 空壳。
 *
 * 所有 chunk 生成入口，getChunk 任意状态请求、ChunkHolder.updateFutures 的
 * prepare* 链，都汇聚到 {@code scheduleChunkGenerationTask}。拦截后不创建
 * ChunkGenerationTask，ChunkPyramid/ChunkStatusTasks 一行不跑，改为：
 * 读盘/建空壳 ProtoChunk → 构造 LevelChunk，存在性容器，保留 → 逐状态
 * completeFuture → 全部请求立即拿到 LevelChunk。per-section 生成由独立的
 * terrainPipeline 系统异步驱动。
 *
 * 线程：拦截在主线程；scheduleChunkLoad 内部后台读盘 + 主线程建 ProtoChunk；
 * thenAccept 跟随主线程，与 vanilla full 一致。
 */
@Mixin(GenerationChunkHolder.class)
public abstract class GenerationChunkHolderMixin {

    // ---- @Shadow：startedWork 必须置 FULL，否则 getLatestChunk 返回 null
    // → saveChunkIfNeeded 永不保存。final 字段用 @Shadow 非 final 声明：mixin
    // 生成 putfield 直写，绕开反射写 final 的 JVM 限制。----

    @Shadow
    private AtomicReference<ChunkStatus> startedWork;

    @Shadow
    public abstract ChunkPos getPos();

    @Shadow
    public abstract FullChunkStatus getFullStatus();

    // ---- 反射：ChunkMap.scheduleChunkLoad private / level final，只读 ----

    private static final Method M_SCHEDULE_CHUNK_LOAD;
    private static final Field F_LEVEL;
    static {
        try {
            M_SCHEDULE_CHUNK_LOAD = ChunkMap.class.getDeclaredMethod("scheduleChunkLoad", ChunkPos.class);
            M_SCHEDULE_CHUNK_LOAD.setAccessible(true);
            F_LEVEL = ChunkMap.class.getDeclaredField("level");
            F_LEVEL.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- 反射：GenerationChunkHolder 私有方法 getOrCreateFuture/completeFuture/isStatusDisallowed ----

    private static final Method M_GET_OR_CREATE_FUTURE;
    private static final Method M_COMPLETE_FUTURE;
    private static final Method M_IS_STATUS_DISALLOWED;
    static {
        try {
            M_GET_OR_CREATE_FUTURE = GenerationChunkHolder.class.getDeclaredMethod("getOrCreateFuture", ChunkStatus.class);
            M_GET_OR_CREATE_FUTURE.setAccessible(true);
            M_COMPLETE_FUTURE = GenerationChunkHolder.class.getDeclaredMethod("completeFuture", ChunkStatus.class, ChunkAccess.class);
            M_COMPLETE_FUTURE.setAccessible(true);
            M_IS_STATUS_DISALLOWED = GenerationChunkHolder.class.getDeclaredMethod("isStatusDisallowed", ChunkStatus.class);
            M_IS_STATUS_DISALLOWED.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 每 holder 短路只跑一次；completeFuture 对已成功完成的 future 再 complete 会抛。 */
    @Unique
    private final AtomicBoolean farlandsExistenceStarted = new AtomicBoolean();

    @Overwrite
    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus targetStatus, ChunkMap chunkMap) {
        if (farlandsIsStatusDisallowed(targetStatus)) {
            return GenerationChunkHolder.UNLOADED_CHUNK_FUTURE;
        }
        CompletableFuture<ChunkResult<ChunkAccess>> future = farlandsGetOrCreateFuture(targetStatus);
        if (future.isDone()) {
            return future;
        }
        if (this.farlandsExistenceStarted.compareAndSet(false, true)) {
            ChunkPos pos = this.getPos();
            try {
                CompletableFuture<ChunkAccess> load = farlandsScheduleChunkLoad(chunkMap, pos);
                load.thenAccept(proto -> this.farlandsCompleteEmpty(chunkMap, proto));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return future;
    }

    /** 主线程：ProtoChunk → LevelChunk 容器构造 + 生命周期 + 逐状态 completeFuture。 */
    @Unique
    @SuppressWarnings("null")
    private void farlandsCompleteEmpty(ChunkMap chunkMap, ChunkAccess proto) {
        try {
            ServerLevel level = (ServerLevel) F_LEVEL.get(chunkMap);
            boolean isNew = !(proto instanceof ImposterProtoChunk);
            LevelChunk levelchunk;
            if (!isNew) {
                levelchunk = ((ImposterProtoChunk) proto).getWrapped();
            } else {
                levelchunk = new LevelChunk(level, (ProtoChunk) proto, p -> { });
            }
            levelchunk.setFullStatus(this::getFullStatus);
            levelchunk.runPostLoad();
            levelchunk.setLoaded(true);
            levelchunk.registerAllBlockEntitiesAfterLevelLoad();
            levelchunk.registerTickContainerInLevel(level);
            NeoForge.EVENT_BUS.post(new ChunkEvent.Load(levelchunk, isNew));

            // 主线程初始化 per-section 状态 attachment；IdentityHashMap 写只在主线程
            FarLandsGenState.initialize(levelchunk);

            this.startedWork.set(ChunkStatus.FULL);

            for (ChunkStatus status : ChunkStatus.getStatusList()) {
                farlandsCompleteFuture(status, levelchunk);
            }

            // biome 阶段独立（BiomeFiller）：后台填窗口 section + BIOMES 态（vanilla BIOMES
            // 先于 NOISE、独立于地形）→ 完成后回主线程 fsa 读回 → enqueueChunk。thenAccept
            // 在 background 线程执行，读回/入队须回主线程（SectionIO.runOnMainThread）。
            CompletableFuture.runAsync(
                    () -> com.inf.farlands.terrain.biomeFiller.BiomeFiller.fillChunkBiomes(level, levelchunk),
                    Util.backgroundExecutor())
                    .thenAccept(v -> com.inf.farlands.serialize.SectionIO.runOnMainThread(() -> {
                        // fsa 读回：先查磁盘窗口内 section，异步 → 有则读回恢复，数据+光照+stage+补发；
                        // 完成后再 enqueueChunk——collectSegments 的 isOrAfter(NOISE) 自动跳过已读回的；
                        // 磁盘无的 section 正常入生成队列。
                        com.inf.farlands.serialize.SectionLifecycle.loadChunkSections(levelchunk,
                                () -> GenQueue.enqueueChunk(levelchunk));
                    }, level));
        } catch (Exception e) {
            InfFarlands.LOGGER.error("farlands: existence flow failed chunk={}", proto.getPos(), e);
            throw new RuntimeException(e);
        }
    }

    // ---- 反射 helper ----

    @Unique
    private boolean farlandsIsStatusDisallowed(ChunkStatus status) {
        try {
            return (Boolean) M_IS_STATUS_DISALLOWED.invoke(this, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private CompletableFuture<ChunkResult<ChunkAccess>> farlandsGetOrCreateFuture(ChunkStatus status) {
        try {
            return (CompletableFuture<ChunkResult<ChunkAccess>>) M_GET_OR_CREATE_FUTURE.invoke(this, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    private void farlandsCompleteFuture(ChunkStatus status, ChunkAccess chunk) {
        try {
            M_COMPLETE_FUTURE.invoke(this, status, chunk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Unique
    private static CompletableFuture<ChunkAccess> farlandsScheduleChunkLoad(ChunkMap chunkMap, ChunkPos pos) {
        try {
            return (CompletableFuture<ChunkAccess>) M_SCHEDULE_CHUNK_LOAD.invoke(chunkMap, pos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}