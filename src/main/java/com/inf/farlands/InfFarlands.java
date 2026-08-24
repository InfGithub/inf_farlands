package com.inf.farlands;

import com.inf.farlands.network.FarLandsChunkDataPacket;
import com.inf.farlands.network.FarLandsLightUpdatePacket;
import com.inf.farlands.network.FarLandsSectionBlocksUpdatePacket;
import com.inf.farlands.terrain.BetaTerrain;
import com.inf.farlands.tool.clamp.ClampMode;
import com.inf.farlands.tool.clamp.FarLandsClampStatePacket;
import com.inf.farlands.tool.clamp.FarLandsClampTogglePacket;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.lighting.LevelLightEngine;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inf.farlands.client.ClientFarLandsCommands;
import com.inf.farlands.client.compat.sodium.SodiumWindowSync;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ChunkMap;

@Mod("inf_farlands")
public class InfFarlands {
    public static final String MODID = "inf_farlands";

    public static final Logger LOGGER = LoggerFactory.getLogger(InfFarlands.class);

    // --------------------------------

    private static volatile int tickCounter = 0;
    private static final int TRIM_INTERVAL = 200;

    public static long getServerTickCount() {
        return tickCounter;
    }

    // ---- §4.1 per-player 窗口状态（window.md，服务端主线程）----

    private static final class PlayerWindowState {
        int centerY;
        ResourceKey<Level> dimension;

        PlayerWindowState(int centerY, ResourceKey<Level> dimension) {
            this.centerY = centerY;
            this.dimension = dimension;
        }
    }

    /** UUID → 上次窗口中心（centerY）+ 维度。维度变化 = 首次记录语义（不发 difference）。 */
    private static final Map<UUID, PlayerWindowState> windowStates = new ConcurrentHashMap<>();

    /** §4.2 队列：UUID → (chunkPos asLong → 新进入 sectionY 集合)。已发送即出队（队列语义，无历史状态机）。 */
    private static final Map<UUID, Map<Long, IntSet>> pendingQueues = new ConcurrentHashMap<>();

    // --------------------------------

    public InfFarlands(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::registerPayloads);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onOverworldLoad);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(FarLandsCommands::register);
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(ClientFarLandsCommands::registerClient);
        }
        if (FMLEnvironment.dist.isClient()) {
            SodiumWindowSync.register();
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        windowStates.remove(id);
        pendingQueues.remove(id);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % TRIM_INTERVAL == 0) {
            HashUtil.trimLookups(tickCounter);
        }

        // §4.2 管线：窗口变化检测 → difference → 入队 → 限量发送
        MinecraftServer server = event.getServer();
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        boolean windowChanged = false;
        Set<UUID> roster = new HashSet<>();
        for (ServerPlayer player : players) {
            roster.add(player.getUUID());
            if (updatePlayerWindow(player)) {
                windowChanged = true;
            }
        }
        if (!roster.equals(lastPlayerRoster)) {
            lastPlayerRoster = roster;
            windowChanged = true;
        }
        // 实体 section 窗口并集更新（先于补触发，刷新用新窗口）
        EntitySectionWindow.update(players);
        if (windowChanged) {
            refreshEntitySectionStatus(server);
        }

        for (ServerPlayer player : players) {
            flushPendingSections(player);
        }
    }

    /**
     * 窗口变化检测 + difference：新窗口 34 个 sectionY 中不在旧窗口区间内的 = 新进入，入队。返回 true =
     * 窗口变化（含首次记录）。
     */
    private static boolean updatePlayerWindow(ServerPlayer player) {
        ResourceKey<Level> dim = player.level().dimension();
        UUID id = player.getUUID();
        int centerY = Mth.floorDiv(player.getBlockY(), 16);
        PlayerWindowState state = windowStates.get(id);
        if (state == null || state.dimension != dim) {
            windowStates.put(id, new PlayerWindowState(centerY, dim));
            // 首次注册（含重连）——全部窗口 section 入队
            int min = centerY - Config.verticalSimulationDistance;
            int max = centerY + Config.verticalSimulationDistance;
            for (int sy = min; sy <= max; sy++) {
                int s = sy;
                player.getChunkTrackingView().forEach(cp -> pendingQueues
                        .computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(cp.toLong(), k -> new IntArraySet())
                        .add(s));
            }
            return true;
        }
        if (state.centerY == centerY) {
            return false;
        }
        int oldMin = state.centerY - Config.verticalSimulationDistance;
        int oldMax = state.centerY + Config.verticalSimulationDistance;
        int newMin = centerY - Config.verticalSimulationDistance;
        int newMax = centerY + Config.verticalSimulationDistance;
        for (int sy = newMin; sy <= newMax; sy++) {
            if (sy >= oldMin && sy <= oldMax) {
                continue;
            }
            int s = sy;
            player.getChunkTrackingView().forEach(cp -> pendingQueues
                    .computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                    .computeIfAbsent(cp.toLong(), k -> new IntArraySet())
                    .add(s));
        }
        state.centerY = centerY;
        return true;
    }

    private static Set<UUID> lastPlayerRoster = new HashSet<>();

    private static final Field F_ENTITY_MANAGER;
    private static final Method M_UPDATE_CHUNK_STATUS;
    private static final Method M_GET_CHUNKS;
    static {
        try {
            F_ENTITY_MANAGER = ServerLevel.class.getDeclaredField("entityManager");
            F_ENTITY_MANAGER.setAccessible(true);
            M_UPDATE_CHUNK_STATUS = PersistentEntitySectionManager.class.getDeclaredMethod(
                    "updateChunkStatus", ChunkPos.class, FullChunkStatus.class);
            M_UPDATE_CHUNK_STATUS.setAccessible(true);
            M_GET_CHUNKS = ChunkMap.class.getDeclaredMethod("getChunks");
            M_GET_CHUNKS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 补触发：窗口滑动/玩家 roster 变化 → 对所有 TICKING chunk 重新调
     * updateChunkStatus（PersistentEntitySectionManagerMixin 内按新窗口过滤）——
     * 滑出 section 的实体 stopTicking、滑入的 startTicking。
     */
    @SuppressWarnings("unchecked")
    private static void refreshEntitySectionStatus(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            try {
                for (ChunkHolder holder : (Iterable<ChunkHolder>) M_GET_CHUNKS
                        .invoke(level.getChunkSource().chunkMap)) {
                    LevelChunk chunk = holder.getTickingChunk();
                    if (chunk != null) {
                        M_UPDATE_CHUNK_STATUS.invoke(
                                F_ENTITY_MANAGER.get(level), chunk.getPos(), chunk.getFullStatus());
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    /** 限量发送：按距离排序出队，累计 ≤ Config.sectionSendBytesPerTick，打包为一个 §5 section 包。 */
    @SuppressWarnings("null")
    private static void flushPendingSections(ServerPlayer player) {
        Map<Long, IntSet> queue = pendingQueues.get(player.getUUID());
        if (queue == null || queue.isEmpty()) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        ChunkPos playerChunk = player.chunkPosition();
        int budget = Config.sectionSendBytesPerTick;
        int windowMinY = Mth.floorDiv(player.getBlockY(), 16) - Config.verticalSimulationDistance;

        List<Map.Entry<Long, IntSet>> sorted = new ArrayList<>(queue.entrySet());
        sorted.sort(Comparator.comparingLong(e -> distSq(e.getKey(), playerChunk)));

        List<FarLandsChunkDataPacket.SectionEntry> batch = new ArrayList<>();
        int used = 0;
        outer: for (Map.Entry<Long, IntSet> e : sorted) {
            int cx = ChunkPos.getX(e.getKey());
            int cz = ChunkPos.getZ(e.getKey());
            ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
            if (!(chunk instanceof LevelChunk lc)) {
                continue;
            }
            IntIterator it = e.getValue().iterator();
            while (it.hasNext()) {
                int sy = it.nextInt();
                LevelChunkSection section = ((WindowedChunk) lc).windowedAllSections().get(sy);
                if (section == null || section.hasOnlyAir()) {
                    it.remove(); // 空 section 无需发送
                    continue;
                }
                FriendlyByteBuf tmp = new FriendlyByteBuf(Unpooled.buffer());
                section.write(tmp);
                byte[] data = new byte[tmp.readableBytes()];
                tmp.readBytes(data);
                int est = 24 + data.length;
                if (used + est > budget) {
                    break outer; // 预算耗尽，剩余留队列
                }
                SectionPos spos = SectionPos.of(lc.getPos(), sy);
                LevelLightEngine le = level.getChunkSource().getLightEngine();
                DataLayer bl = le.getLayerListener(LightLayer.BLOCK).getDataLayerData(spos);
                DataLayer sl = le.getLayerListener(LightLayer.SKY).getDataLayerData(spos);
                batch.add(new FarLandsChunkDataPacket.SectionEntry(
                        cx, cz, sy, data,
                        FarLandsChunkDataPacket.encodeLight(bl),
                        FarLandsChunkDataPacket.encodeLight(sl)));
                it.remove();
                used += est;
            }
            if (e.getValue().isEmpty()) {
                queue.remove(e.getKey());
            }
        }
        if (!batch.isEmpty()) {
            player.connection.send(new ClientboundCustomPayloadPacket(
                    new FarLandsChunkDataPacket(windowMinY, batch)));
        }
    }

    private static long distSq(long chunkKey, ChunkPos playerChunk) {
        long dx = (long) ChunkPos.getX(chunkKey) - playerChunk.x;
        long dz = (long) ChunkPos.getZ(chunkKey) - playerChunk.z;
        return dx * dx + dz * dz;
    }

    // --------------------------------

    private void onOverworldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel sl && sl.dimension() == Level.OVERWORLD) {
            BetaTerrain.initialize(sl.getSeed());
        }
    }

    @SuppressWarnings("null")
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // 钳制模式 toggle：F3+K（客户端）→ 服务端校验 OP → toggle per-player 状态
        registrar.playToServer(
                FarLandsClampTogglePacket.TYPE,
                FarLandsClampTogglePacket.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        if (player.hasPermissions(2)) {
                            ClampMode clamp = (ClampMode) player;
                            boolean enabled = !clamp.farlandsIsClampEnabled();
                            clamp.farlandsSetClampEnabled(enabled);
                            player.displayClientMessage(Component.translatable(
                                    enabled ? "commands.inf_farlands.clamp.on" : "commands.inf_farlands.clamp.off"), false);
                            // 状态同步到客户端（客户端钳制预测位置，阻止预测覆盖服务端钳制）
                            player.connection.send(new ClientboundCustomPayloadPacket(new FarLandsClampStatePacket(enabled)));
                        } else {
                            player.displayClientMessage(
                                    Component.translatable("commands.inf_farlands.clamp.no_permission"), false);
                        }
                    }
                });

        registrar.playToClient(
                FarLandsClampStatePacket.TYPE,
                FarLandsClampStatePacket.STREAM_CODEC,
                (payload, context) -> {
                    if (Minecraft.getInstance().player instanceof ClampMode clamp) {
                        clamp.farlandsSetClampEnabled(payload.enabled());
                    }
                });

        registrar.playToClient(
                FarLandsSectionBlocksUpdatePacket.TYPE,
                FarLandsSectionBlocksUpdatePacket.STREAM_CODEC,
                (payload, context) -> {
                    ClientLevel level = Minecraft.getInstance().level;
                    if (level != null) {
                        payload.runUpdates((pos, state) -> level.setServerVerifiedBlockState(pos, state, 19));
                    }
                });

        // §5 section 包（window.md）：写入 allSections + 光照 X2 分支 + 持有边界更新 + 标记重建。
        // section 数据 byte[] 中转：解码需 biomeRegistry，在 handler 里 new LevelChunkSection +
        // read。
        registrar.playToClient(
                FarLandsChunkDataPacket.TYPE,
                FarLandsChunkDataPacket.STREAM_CODEC,
                (payload, context) -> {
                    ClientLevel level = Minecraft.getInstance().level;
                    if (level == null) {
                        return;
                    }
                    int minY = payload.windowMinY();
                    LevelLightEngine le = level.getLightEngine();
                    for (FarLandsChunkDataPacket.SectionEntry e : payload.sections()) {
                        ChunkAccess chunk = level.getChunkSource()
                                .getChunk(e.chunkX(), e.chunkZ(), ChunkStatus.FULL, false);
                        if (!(chunk instanceof LevelChunk lc)) {
                            continue;
                        }
                        ((WindowedChunk) lc).setLastPacketWindow(minY, minY + ((WindowedChunk) lc).windowHalfBelow() + ((WindowedChunk) lc).windowHalfAbove());
                        // 不可变切换：新建 section 整体替换（PalettedContainer.read 的 createOrReuseData
                        // 会复用现有 Data 原地改 palette——与渲染编译线程并发读时读到 palette 中间状态
                        // → MissingPaletteEntryException CTD（重生窗口跳变场景）。新建对象无并发读者。
                        LevelChunkSection ns = new LevelChunkSection(
                                level.registryAccess().registryOrThrow(Registries.BIOME));
                        ns.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(e.sectionData())));
                        ((WindowedChunk) lc).windowedAllSections().put(e.sectionY(), ns);
                        lc.getSection(lc.getSectionIndexFromSectionY(e.sectionY())); // 数组同步（get 内部 arr[idx]=s）
                        DataLayer bl = FarLandsChunkDataPacket.decodeLight(e.blockLight());
                        if (bl != null) {
                            le.queueSectionData(LightLayer.BLOCK, SectionPos.of(lc.getPos(), e.sectionY()), bl);
                        }
                        DataLayer sl = FarLandsChunkDataPacket.decodeLight(e.skyLight());
                        if (sl != null) {
                            le.queueSectionData(LightLayer.SKY, SectionPos.of(lc.getPos(), e.sectionY()), sl);
                        }
                        level.setSectionDirtyWithNeighbors(e.chunkX(), e.sectionY(), e.chunkZ());
                    }
                });

        // 光照增量包（自定义，绝对 sectionY 无范围限制）：queueSectionData 写入/清空 + 标记渲染重编译。
        registrar.playToClient(
                FarLandsLightUpdatePacket.TYPE,
                FarLandsLightUpdatePacket.STREAM_CODEC,
                (payload, context) -> {
                    ClientLevel level = Minecraft.getInstance().level;
                    if (level == null) {
                        return;
                    }
                    LevelLightEngine le = level.getLightEngine();
                    for (FarLandsLightUpdatePacket.SectionLight e : payload.sky()) {
                        applyLight(le, LightLayer.SKY, payload.chunkX(), payload.chunkZ(), e);
                    }
                    for (FarLandsLightUpdatePacket.SectionLight e : payload.block()) {
                        applyLight(le, LightLayer.BLOCK, payload.chunkX(), payload.chunkZ(), e);
                    }
                });
    }

    /** 光照增量应用：data=null → 清空该 section 层（getLightValue 恢复搜索）；非 null → 覆盖。 */
    @SuppressWarnings("null")
    private static void applyLight(LevelLightEngine le, LightLayer layer, int cx, int cz,
            FarLandsLightUpdatePacket.SectionLight e) {
        le.queueSectionData(layer, SectionPos.of(cx, e.sectionY(), cz), FarLandsLightUpdatePacket.decodeSectionLight(e.data()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.setSectionDirty(cx, e.sectionY(), cz);
        }
    }

}
