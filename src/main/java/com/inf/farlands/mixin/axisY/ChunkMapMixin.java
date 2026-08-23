package com.inf.farlands.mixin.axisY;

import com.inf.farlands.Config;
import com.inf.farlands.WindowSendState;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Shadow
    private ServerLevel level;

    @Shadow
    private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @Shadow
    private ThreadedLevelLightEngine lightEngine;

    @Shadow
    private ChunkProgressListener progressListener;

    @Shadow
    private Long2ByteMap chunkTypeCache;

    @Shadow
    private Long2LongMap chunkSaveCooldowns;

    @Shadow
    private Queue<Runnable> unloadQueue;

    @Shadow
    private PoiManager poiManager;

    @Shadow
    private static Logger LOGGER;

    @Shadow
    private boolean save(ChunkAccess chunk) {
        return false; // stub：@Shadow 方法仅编译解析用，运行时不执行
    }

    @Unique
    private static final java.lang.reflect.Method M_UPDATE_CHUNK_STATUS;
    static {
        try {
            M_UPDATE_CHUNK_STATUS = ThreadedLevelLightEngine.class
                    .getDeclaredMethod("updateChunkStatus", ChunkPos.class);
            M_UPDATE_CHUNK_STATUS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** updateChunkStatus 是 protected（同包 vanilla 可调），Mixin 跨包编译需反射。 */
    @Unique
    private static void lightUpdateChunkStatus(ThreadedLevelLightEngine le, ChunkPos pos) {
        try {
            M_UPDATE_CHUNK_STATUS.invoke(le, pos);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Shadow
    protected abstract List<ServerPlayer> getPlayers(ChunkPos chunkPos, boolean onlyPlayersWithChunkTracked);

    // ---- 退出卡"保存世界中"修复（反射读 MinecraftServer.isSaving，无 public getter）----

    private static final Field F_IS_SAVING;
    static {
        try {
            F_IS_SAVING = MinecraftServer.class.getDeclaredField("isSaving");
            F_IS_SAVING.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 保存关闭中（stopServer 置 isSaving=true）——该模式下强制卸载保存，不无限等待生成完成。 */
    @Unique
    private static boolean isSavingMode(ServerLevel level) {
        try {
            return (Boolean) F_IS_SAVING.get(level.getServer());
        } catch (Exception e) {
            return false; // 反射失败 → 非保存模式（保守，保持 vanilla 递归）
        }
    }

    /**
     * scheduleUnload 原版在 genRef 归零前无限递归重投 unloadQueue——genRef 归零依赖
     * light/worldgen 完成链，退出时该链断（light 异步化后 worldgen 不再驱动挂起任务）→
     * 主线程 100% CPU 卡"保存世界中"。isSaving（退出）模式下 genRef!=0 直接强制保存，
     * 不递归；正常游戏 isSaving=false 行为不变。
     */
    @SuppressWarnings("null")
    @Overwrite
    private void scheduleUnload(long chunkPos, ChunkHolder chunkHolder) {
        chunkHolder.getSaveSyncFuture().thenRunAsync(() -> {
            if (!chunkHolder.isReadyForSaving() && !isSavingMode(this.level)) {
                this.scheduleUnload(chunkPos, chunkHolder);
            } else {
                ChunkAccess chunkaccess = chunkHolder.getLatestChunk();
                if (this.pendingUnloads.remove(chunkPos, chunkHolder) && chunkaccess != null) {
                    if (chunkaccess instanceof LevelChunk levelchunk) {
                        levelchunk.setLoaded(false);
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.ChunkEvent.Unload(chunkaccess));
                    }
                    this.save(chunkaccess);
                    if (chunkaccess instanceof LevelChunk levelchunk1) {
                        this.level.unload(levelchunk1);
                    }
                    net.neoforged.neoforge.common.CommonHooks.onChunkUnload(this.poiManager, chunkaccess);
                    this.chunkTypeCache.remove(chunkaccess.getPos().toLong());
                    lightUpdateChunkStatus(this.lightEngine, chunkaccess.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.progressListener.onStatusChange(chunkaccess.getPos(), null);
                    this.chunkSaveCooldowns.remove(chunkaccess.getPos().toLong());
                }
            }
        }, this.unloadQueue::add).whenComplete((v, t) -> {
            if (t != null) {
                LOGGER.error("Failed to save chunk {}", chunkHolder.getPos(), t);
            }
        });
    }

    // 服务端窗口死状态（window.md §4.4）：不再在 mark 时 moveWindowTo，
    // windowMinY 保持构造默认 -4——发送范围由 per-player ThreadLocal 决定。

    /**
     * per-player 发送：每个玩家设自己的窗口 ThreadLocal 后发包，
     * extractChunkData 据此只发该玩家窗口内的非空 section。
     */
    @SuppressWarnings("null")
    @Overwrite
    public void resendBiomesForChunks(List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap<>();

        for (ChunkAccess chunkaccess : chunks) {
            ChunkPos chunkpos = chunkaccess.getPos();
            LevelChunk levelchunk;
            if (chunkaccess instanceof LevelChunk levelchunk1) {
                levelchunk = levelchunk1;
            } else {
                levelchunk = this.level.getChunk(chunkpos.x, chunkpos.z);
            }

            for (ServerPlayer serverplayer : this.getPlayers(chunkpos, false)) {
                map.computeIfAbsent(serverplayer, p -> new ArrayList<>()).add(levelchunk);
            }
        }

        map.forEach((serverplayer, list) -> {
            int centerY = Mth.floorDiv(serverplayer.getBlockY(), 16);
            WindowSendState.setWindowMinY(centerY - Config.verticalSimulationDistance);
            try {
                serverplayer.connection.send(ClientboundChunksBiomesPacket.forChunks(list));
            } finally {
                WindowSendState.clear();
            }
        });
    }
}
