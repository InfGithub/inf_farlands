package com.inf.farlands;

import com.inf.farlands.util.HashUtil;
import com.inf.farlands.window.EntitySectionWindow;
import com.inf.farlands.window.WindowedChunk;
import com.inf.farlands.network.FarLandsChunkDataPacket;
import com.inf.farlands.network.FarLandsLightUpdatePacket;
import com.inf.farlands.network.FarLandsSectionBlocksUpdatePacket;
import com.inf.farlands.terrain.system.NoiseSystemRegistry;
import com.inf.farlands.terrain.pipeline.FarLandsGenState;
import com.inf.farlands.terrain.pipeline.GenQueue;
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
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
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
@SuppressWarnings({"null"})
public class InfFarlands {
    public static final String MODID = "inf_farlands";

    public static final Logger LOGGER = LoggerFactory.getLogger(InfFarlands.class);

    // --------------------------------

    private static volatile int tickCounter = 0;
    private static final int TRIM_INTERVAL = 200;

    public static long getServerTickCount() {
        return tickCounter;
    }

    // ---- §4.1 per-player 窗口状态：window.md 所述，服务端主线程维护 ----

    private static final class PlayerWindowState {
        int centerY;
        ResourceKey<Level> dimension;
        /** 上次 XZ chunk 坐标——P2 动态优先级触发检测用（任一玩家 chunk 变化即重排）。 */
        int lastChunkX = Integer.MIN_VALUE;
        int lastChunkZ = Integer.MIN_VALUE;

        PlayerWindowState(int centerY, ResourceKey<Level> dimension) {
            this.centerY = centerY;
            this.dimension = dimension;
        }
    }

    /** UUID → 上次窗口中心 centerY 与维度。维度变化按首次记录语义处理，不发 difference。 */
    private static final Map<UUID, PlayerWindowState> windowStates = new ConcurrentHashMap<>();

    /** §4.2 队列：UUID → (chunkPos asLong → 新进入 sectionY 集合)。已发送即出队，队列无历史状态机。 */
    private static final Map<UUID, Map<Long, IntSet>> pendingQueues = new ConcurrentHashMap<>();

    // ---- §5 客户端包缓存 ----
    // §5 section 包到达时 chunk 未加载 → 缓存，chunk 加载后于 handleLevelChunkWithLight
    // RETURN 处补应用——防丢弃后服务端已出队不重发 → 方块数据永久缺失，表现为空缺/双端不同步：
    // 客户端空预测下坠 vs 服务端实心判定不动。
    // key 含维度：tp 跨维度后旧维度缓存条目不与同坐标新维度冲突（下界/末地 (0,0) 同 key 污染）。

    private static final class PendingSections {
        final int minY;
        final List<FarLandsChunkDataPacket.SectionEntry> entries = new ArrayList<>();

        PendingSections(int minY) {
            this.minY = minY;
        }
    }

    /** 缓存 key：维度 + chunkPos——维度隔离，防跨维度同坐标 key 冲突。 */
    private record PendingKey(ResourceKey<Level> dimension, long chunkPos) {
    }

    private static final ConcurrentHashMap<PendingKey, PendingSections> PENDING_SECTION_DATA = new ConcurrentHashMap<>();

    /** §5 数据应用，handler 与补应用共用。 */
    private static void applySectionData(ClientLevel level, LevelLightEngine le, LevelChunk lc,
            FarLandsChunkDataPacket.SectionEntry e, int minY) {
        ((WindowedChunk) lc).setLastPacketWindow(minY,
                minY + ((WindowedChunk) lc).windowHalfBelow() + ((WindowedChunk) lc).windowHalfAbove());
        // 不可变切换：新建 section 整体替换。PalettedContainer.read 的 createOrReuseData
        // 会复用现有 Data 原地改 palette——与渲染编译线程并发读时读到 palette 中间状态
        // → MissingPaletteEntryException CTD，重生窗口跳变场景下触发。新建对象无并发读者。
        LevelChunkSection ns = new LevelChunkSection(
                level.registryAccess().registryOrThrow(Registries.BIOME));
        ns.read(new FriendlyByteBuf(Unpooled.wrappedBuffer(e.sectionData())));
        ((WindowedChunk) lc).windowedAllSections().put(e.sectionY(), ns);
        lc.getSection(lc.getSectionIndexFromSectionY(e.sectionY())); // 数组同步，get 内部执行 arr[idx]=s
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

    /** chunk 加载完成后补应用缓存的 §5 数据，由 handleLevelChunkWithLight RETURN 调用。
     * dimension 为当前维度——缓存 key 维度隔离，跨维度同坐标不误取。 */
    public static void applyPendingSectionData(ResourceKey<Level> dimension, int cx, int cz) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        PendingSections pending = PENDING_SECTION_DATA.remove(new PendingKey(dimension, ChunkPos.asLong(cx, cz)));
        if (pending == null) {
            return;
        }
        ChunkAccess chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (!(chunk instanceof LevelChunk lc)) {
            return; // 仍未加载；补应用在 handleLevelChunkWithLight 之后，理论不触发
        }
        LevelLightEngine le = level.getLightEngine();
        for (FarLandsChunkDataPacket.SectionEntry e : pending.entries) {
            applySectionData(level, le, lc, e, pending.minY);
        }
    }

    /** chunk 卸载时丢弃缓存，由 handleForgetLevelChunk 调用，防残留堆积。 */
    public static void discardPendingSectionData(ResourceKey<Level> dimension, ChunkPos pos) {
        PENDING_SECTION_DATA.remove(new PendingKey(dimension, pos.toLong()));
    }

    // --------------------------------

    public InfFarlands(IEventBus modBus, ModContainer container) {
        modBus.addListener(this::registerPayloads);
        modBus.addListener(Config::onLoad); // 配置读取：ModConfigEvent 写入静态字段，缺失时全部配置失效
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        FarLandsGenState.ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onOverworldLoad);
        NeoForge.EVENT_BUS.addListener(this::onPlayerJoin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
        NeoForge.EVENT_BUS.addListener(InfFarlands::onServerStopping);
        NeoForge.EVENT_BUS.addListener(FarLandsCommands::register);
        if (FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(ClientFarLandsCommands::registerClient);
        }
        if (FMLEnvironment.dist.isClient()) {
            SodiumWindowSync.register();
        }
    }

    /**
     * 关服走同步路径：等卸载编码任务全部提交（genPool → 主线程提交回调，循环内
     * drain 主线程消费）→ 等 IO 队列 drain，所有 doWrite 完成后 → 主线程排空
     * commit 回调并更新 offsets → 同步兜底写仍脏 section + 同步刷盘——无异步回调依赖，
     * 顺序保证：数据扇区 → offsets → 刷盘全部完成，重进可读。
     */
    private static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        try {
            MinecraftServer server = event.getServer();
            // A1'：卸载编码任务（flushChunk 提交 genPool）必须先于 awaitIODrain 完成提交，
            // 否则其 IO 写未被等待 → 关服丢已卸载 section 数据。
            com.inf.farlands.serialize.SectionLifecycle.awaitEncodeTasks(server, 5000);
            com.inf.farlands.serialize.SectionIO.awaitIODrain();
            com.inf.farlands.serialize.SectionIO.drainMainThreadTasks(server);
            // A+B：关服前等生成/光照在途收敛，有界 5s；超时由 shutdownSyncFlush 的
            // isChunkBusy 跳过兜底——在途 section 重进重生成。
            GenQueue.awaitIdle(5000);
            com.inf.farlands.serialize.SectionLifecycle.shutdownSyncFlush(server);
        } catch (Exception e) {
            LOGGER.error("farlands: fsa shutdown flush failed", e);
        }
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        windowStates.remove(id);
        pendingQueues.remove(id);
    }

    // ---- 出生点预加载，配合 MinecraftServerMixin.preloadSpawnArea ----
    // ticket 保留到第一个玩家加入：chunk 保持加载，玩家出生直接内存数据——避免卸载-重载
    // 读回/写盘竞态，表现为预加载退化 + 掉虚空；玩家加入后移除，由视距 ticket 接管，仍非常驻——
    // 玩家离开 chunk 卸载落盘。

    public static final TicketType<Unit> PRELOAD_TICKET =
            TicketType.create("inf_farlands_preload", (a, b) -> 0, 0);

    private static final Set<Long> PRELOAD_CHUNKS = ConcurrentHashMap.newKeySet();

    /** preloadSpawnArea 注册预加载 chunk，ticket 已加。 */
    public static void registerPreloadChunk(ChunkPos pos) {
        PRELOAD_CHUNKS.add(pos.toLong());
    }

    /** 玩家加入：移除预加载 ticket 一次，之后由视距 ticket 接管。 */
    public static void clearPreloadTickets(ServerLevel level) {
        if (PRELOAD_CHUNKS.isEmpty()) {
            return;
        }
        for (Long key : PRELOAD_CHUNKS) {
            level.getChunkSource().removeRegionTicket(PRELOAD_TICKET, new ChunkPos(key), 0, Unit.INSTANCE);
        }
        PRELOAD_CHUNKS.clear();
    }

    private void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            clearPreloadTickets(sp.serverLevel());
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % TRIM_INTERVAL == 0) {
            HashUtil.trimLookups(tickCounter);
        }
        if (tickCounter % Config.fsaPersistInterval == 0) {
            // fsa 定期持久化：窗口内脏 section 写盘，作为崩溃语义兜底，5 分钟一次
            com.inf.farlands.serialize.SectionLifecycle.flushAllDirty(event.getServer());
            // 偏移表与数据同节奏落盘；否则运行中磁盘偏移表陈旧 → 重进 getSlot 错位丢 section
            com.inf.farlands.serialize.SectionIO.flushAllOffsetTables();
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
        // P2 动态优先级：任一玩家 XZ chunk 位置变化 → 地形+光照队列按当前距离重排。
        // 多玩家全量触发（无单玩家快照、无 break）：队列排序基准 = 最近玩家距离
        // （GenTask.computePriority / FarLandsLightQueue.nearestPlayerDist），
        // 任一玩家移动都重排一次，静止零开销（lastChunkX/Z 不变不触发）。
        boolean anyPlayerMoved = false;
        for (ServerPlayer player : players) {
            ChunkPos pc = player.chunkPosition();
            PlayerWindowState st = windowStates.get(player.getUUID());
            if (st != null && (st.lastChunkX != pc.x || st.lastChunkZ != pc.z)) {
                st.lastChunkX = pc.x;
                st.lastChunkZ = pc.z;
                anyPlayerMoved = true;
            }
        }
        if (anyPlayerMoved) {
            GenQueue.rebuildQueue();
            for (ServerLevel lvl : server.getAllLevels()) {
                if (lvl.getChunkSource().getLightEngine() instanceof com.inf.farlands.light.FarLandsLightEngine fle) {
                    fle.rebuildLightQueue();
                }
            }
        }
        // 实体 section 窗口并集更新，先于补触发，刷新用新窗口
        EntitySectionWindow.update(players);
        if (windowChanged) {
            refreshEntitySectionStatus(server);
            // fsa 清理：窗口变化时扫描，窗口并集外 section 持久化后即删除，≤ 1024/tick
            com.inf.farlands.serialize.SectionLifecycle.cleanup(server);
        }
        // 重试"窗口未建立时加载"的 chunk 读回——这是重进瞬间 loadChunkSections 空转的兜底：
        // 磁盘数据在但重进漏读 → 窗口 section 消失。降频每 5 tick + 内部 budget——
        // 初次进入世界 Preparing 期 ranges 空时零开销早退，玩家生成后分批补读。
        if (tickCounter % 5 == 0) {
            com.inf.farlands.serialize.SectionLifecycle.retryPendingReads(server);
        }

        for (ServerPlayer player : players) {
            flushPendingSections(player);
        }

        // fsa：每 tick 编码消费，drain 预算内主线程现取现编码
        com.inf.farlands.serialize.SectionLifecycle.tick();

        // 地形管线：每 tick 唤醒生成消费，≤ maxGenTasksPerTick
        GenQueue.tick();
        // 动态扫描：每 tick 从玩家当前位置螺旋扫描视距内未生成 chunk 补入队——
        // 治本"空 chunk 长期不生成"：入队快照 priority 旧 + 一次性触发错过
        GenQueue.scanAndEnqueue(server);
    }

    /**
     * 窗口变化检测 + difference：新窗口 34 个 sectionY 中不在旧窗口区间内的 = 新进入，入队。返回 true =
     * 窗口变化，含首次记录。
     */
    private static boolean updatePlayerWindow(ServerPlayer player) {
        ResourceKey<Level> dim = player.level().dimension();
        UUID id = player.getUUID();
        int centerY = Mth.floorDiv(player.getBlockY(), 16);
        PlayerWindowState state = windowStates.get(id);
        if (state == null || state.dimension != dim) {
            windowStates.put(id, new PlayerWindowState(centerY, dim));
            // 首次注册或重连——全部窗口 section 入队
            int min = centerY - Config.verticalSimulationDistance;
            int max = centerY + Config.verticalSimulationDistance;
            for (int sy = min; sy <= max; sy++) {
                int s = sy;
                player.getChunkTrackingView().forEach(cp -> pendingQueues
                        .computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(cp.toLong(), k -> new IntArraySet())
                        .add(s));
                enqueueGenForSection(player, s);
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
                enqueueGenForSection(player, s);
            }
        state.centerY = centerY;
        return true;
    }

    /** Y 触发：该 section 在 tracking view 内已加载 chunk 上入生成队列，运行于主线程。 */
    private static void enqueueGenForSection(ServerPlayer player, int sectionY) {
        ServerLevel level = player.serverLevel();
        player.getChunkTrackingView().forEach(cp -> {
            ChunkAccess ca = level.getChunk(cp.x, cp.z, ChunkStatus.FULL, false);
            if (ca instanceof LevelChunk lc) {
                // fsa 读回优先：磁盘有数据 → 读回恢复数据+光照+stage+§5 补发，不重生成；
                // 无 → 入生成队列，onDone 后 enqueue 的 isOrAfter 检查自动跳过已读回的。
                com.inf.farlands.serialize.SectionLifecycle.loadSection(lc, sectionY, () -> {
                    // biome 独立阶段补触发：窗口滑入新 section（无磁盘数据，stage=UNPROCESSED）
                    // → 填 biome + BIOMES 态；读回 section 磁盘 stage 恢复（>=BIOMES）→ 内部跳过。
                    com.inf.farlands.terrain.biomeFiller.BiomeFiller.fillSectionBiomes(level, lc, sectionY);
                    GenQueue.enqueue(lc, sectionY);
                });
            }
        });
    }

    /** §5 发送：fill 完成后主动入队该 section，复用窗口 difference 发送链，flush 限量。
     * fill 直接写 section 不走 Level.setBlock → 无 vanilla 广播；此处对 tracking 玩家
     * 补入发送队列，下 tick flushPendingSections 发 §5 section 包。线程安全，用 CHM。
     * 距离判定不依赖 tracking view——其每 tick 更新，fill 完成时新加载 chunk 可能
     * 不在 tracking → §5 漏发 → 客户端空壳空缺/双端不同步；与 GenQueue.isNearPlayer 一致。 */
    public static void enqueueSectionSend(LevelChunk chunk, int sectionY) {
        if (!(chunk.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ChunkPos cp = chunk.getPos();
        long chunkKey = cp.toLong();
        for (ServerPlayer player : level.players()) {
            ChunkTrackingView view = player.getChunkTrackingView();
            int viewDistance = view instanceof ChunkTrackingView.Positioned pos ? pos.viewDistance() : 8;
            if (ChunkTrackingView.isWithinDistance(
                    player.chunkPosition().x, player.chunkPosition().z, viewDistance, cp.x, cp.z, true)) {
                pendingQueues.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
                        .computeIfAbsent(chunkKey, k -> new IntArraySet())
                        .add(sectionY);
            }
        }
    }

    /** 播种完成主动广播该 chunk 全部光照，含空 sec 的 15。
     * 增量包链由 ChunkHolder.sectionLightChanged 收集 + broadcastChanges 广播，依赖 chunk
     * 达 ENTITY_TICKING，而"空壳先发 + 光照后补"的管线里播种时往往未达 → 客户端永远收不到
     * 播种后的光照，保持邻居传播写入的渐黑 → 空 sec 黑。本方法绕开 ticking 依赖，
     * 播种完成即 lightChunk whenComplete 时对 tracking 玩家发全窗口 sec 光照增量包。 */
    public static void broadcastChunkLight(ServerLevel level, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.getChunkTrackingView().contains(cp)) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            return;
        }
        LevelLightEngine le = level.getChunkSource().getLightEngine();
        List<FarLandsLightUpdatePacket.SectionLight> sky = new ArrayList<>();
        List<FarLandsLightUpdatePacket.SectionLight> block = new ArrayList<>();
        for (Integer sy : ((WindowedChunk) chunk).windowedAllSections().keySet()) {
            SectionPos spos = SectionPos.of(cp, sy);
            sky.add(new FarLandsLightUpdatePacket.SectionLight(sy,
                    FarLandsLightUpdatePacket.encodeSectionLight(
                            le.getLayerListener(LightLayer.SKY).getDataLayerData(spos))));
            block.add(new FarLandsLightUpdatePacket.SectionLight(sy,
                    FarLandsLightUpdatePacket.encodeSectionLight(
                            le.getLayerListener(LightLayer.BLOCK).getDataLayerData(spos))));
        }
        FarLandsLightUpdatePacket pkt = new FarLandsLightUpdatePacket(level.dimension(), cp.x, cp.z, sky, block);
        for (ServerPlayer p : players) {
            p.connection.send(new ClientboundCustomPayloadPacket(pkt));
        }
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
     * updateChunkStatus——过滤逻辑在 PersistentEntitySectionManagerMixin 内按新窗口进行，
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
                if (GenQueue.isLightInFlight(lc) && ((WindowedChunk) lc).isSectionDirty(sy)) {
                    continue; // 生成 fill 的 section 光照播种未完成：留队列等光照；
                              // 读回的 section 光照已从磁盘恢复且未脏，放行发送，不受同 chunk 生成牵连
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
                    new FarLandsChunkDataPacket(level.dimension(), windowMinY, batch)));
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
            NoiseSystemRegistry.onLevelLoad(sl.getSeed());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // 钳制模式 toggle：客户端按 F3+K → 服务端校验 OP → toggle per-player 状态
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
                            // 状态同步到客户端：客户端据此钳制预测位置，阻止预测覆盖服务端钳制
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
                    if (level == null) {
                        return;
                    }
                    // 维度校验：tp 跨维度在途旧包丢弃，防污染新维度
                    if (!level.dimension().equals(payload.dimension())) {
                        return;
                    }
                    payload.runUpdates((pos, state) -> level.setServerVerifiedBlockState(pos, state, 19));
                });

        // §5 section 包，见 window.md：写入 allSections + 光照 X2 分支 + 持有边界更新 + 标记重建。
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
                    // 维度校验：tp 跨维度在途旧包丢弃，防污染新维度
                    if (!level.dimension().equals(payload.dimension())) {
                        return;
                    }
                    int minY = payload.windowMinY();
                    LevelLightEngine le = level.getLightEngine();
                    for (FarLandsChunkDataPacket.SectionEntry e : payload.sections()) {
                        ChunkAccess chunk = level.getChunkSource()
                                .getChunk(e.chunkX(), e.chunkZ(), ChunkStatus.FULL, false);
                        if (!(chunk instanceof LevelChunk lc)) {
                            // §5 到达时 chunk 未加载 → 缓存，等 chunk 加载后 handleLevelChunkWithLight
                            // RETURN 补应用——不再丢弃：服务端已出队不重发，丢弃会导致方块数据永久缺失。
                            PENDING_SECTION_DATA.computeIfAbsent(
                                    new PendingKey(level.dimension(), ChunkPos.asLong(e.chunkX(), e.chunkZ())),
                                    k -> new PendingSections(minY)).entries.add(e);
                            continue;
                        }
                        applySectionData(level, le, lc, e, minY);
                    }
                });

        // 光照增量包为自定义包，绝对 sectionY 无范围限制：queueSectionData 写入/清空 + 标记渲染重编译。
        registrar.playToClient(
                FarLandsLightUpdatePacket.TYPE,
                FarLandsLightUpdatePacket.STREAM_CODEC,
                (payload, context) -> {
                    ClientLevel level = Minecraft.getInstance().level;
                    if (level == null) {
                        return;
                    }
                    // 维度校验：tp 跨维度在途旧包丢弃，防污染新维度
                    if (!level.dimension().equals(payload.dimension())) {
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

    /** 光照增量应用：data=null → 清空该 section 层，getLightValue 恢复搜索；非 null → 覆盖。 */
    private static void applyLight(LevelLightEngine le, LightLayer layer, int cx, int cz,
            FarLandsLightUpdatePacket.SectionLight e) {
        le.queueSectionData(layer, SectionPos.of(cx, e.sectionY(), cz), FarLandsLightUpdatePacket.decodeSectionLight(e.data()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer != null) {
            mc.levelRenderer.setSectionDirty(cx, e.sectionY(), cz);
        }
    }

}
