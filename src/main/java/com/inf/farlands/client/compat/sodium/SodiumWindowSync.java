package com.inf.farlands.client.compat.sodium;

import com.inf.farlands.Config;
import com.inf.farlands.InfFarlands;
import com.inf.farlands.WindowedChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.lighting.LevelLightEngine;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * sodium 环境下的窗口跟随（§7.6 全视距）+ 集合一致性 + §7.3 滑出丢弃。
 *
 * vanilla 下 ViewAreaMixin 每帧 moveWindowTo(相机Y)；sodium @Overwrite setupRender
 * 后 ViewArea.repositionCamera 不再被调用——客户端窗口只在包/setblock 滑窗时更新，
 * sodium 的 RenderSection 集合（ChunkTracker XZ 驱动）也不跟随垂直移动——纯 Y tp 后
 * 放置方块无法渲染。
 *
 * 每帧（RenderFrameEvent.Pre，主线程）遍历视距内全部已加载 chunk：
 * 1. moveWindowTo(相机 sectionY)——buildWindow 早退，窗口不变零开销
 * 2. 窗口 vs 记录 Map<ChunkPos, Integer>：变化 → onChunkAdded（新窗口补齐，
 * sodium onSectionAdded 幂等）+ 滑出 sectionY 逐个 onSectionRemoved
 * （旧窗口 − 新窗口，防 sectionByPosition 无限累积——sodium 的树按 XZ
 * 管理，Y 残留无自动清理，极端 Y 往返会累积 GB 级）
 * 3. §7.3 丢弃检查（全视距，与 vanilla ViewArea 对称）
 * 4. 记录 Map retainAll 清理（卸载 chunk 条目）
 *
 * 反射链全字符串（sodium 不在编译 classpath）；失败禁用（WARN 一次），功能退化不崩。
 */
public final class SodiumWindowSync {

    private static boolean checkedSodium;
    private static boolean sodiumLoaded;

    private static Class<?> swrClass;
    private static Method mInstanceNullable;
    private static Field fRenderSectionManager;
    private static Method mOnChunkAdded;
    private static Method mOnSectionRemoved;

    private static ClientLevel lastLevel;
    private static final Map<ChunkPos, Integer> lastWindowByChunk = new HashMap<>();

    /** 每帧视距内已加载 chunk 的 vanilla 32-32 编码集合（原始类型，零对象分配）。 */
    private static final LongOpenHashSet visited = new LongOpenHashSet(8192);

    /** §7.3 丢弃触发：view 变化帧立即清 + 每 1 秒兜底（包到达使 hold 收缩时，区间外数据最长残留 1 秒）。 */
    private static int lastDiscardViewMin = Integer.MIN_VALUE;
    private static int lastDiscardViewMax = Integer.MIN_VALUE;
    private static long lastDiscardTime;

    private static boolean disabled;
    private static boolean warned;

    private SodiumWindowSync() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SodiumWindowSync::onRenderFrame);
    }

    private static boolean sodiumLoaded() {
        if (!checkedSodium) {
            checkedSodium = true;
            try {
                sodiumLoaded = ModList.get().isLoaded("sodium");
            } catch (Exception e) {
                sodiumLoaded = false;
            }
        }
        return sodiumLoaded;
    }

    @SuppressWarnings("null")
    private static void onRenderFrame(RenderFrameEvent.Pre event) {
        if (!sodiumLoaded() || disabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        // 世界切换时重置
        if (level != lastLevel) {
            lastLevel = level;
            lastWindowByChunk.clear();
            lastDiscardViewMin = Integer.MIN_VALUE;
            lastDiscardViewMax = Integer.MIN_VALUE;
            lastDiscardTime = 0;
        }

        int camSecY = Mth.floorDiv(player.getBlockY(), 16);
        int chunkX = SectionPos.blockToSectionCoord(player.getBlockX());
        int chunkZ = SectionPos.blockToSectionCoord(player.getBlockZ());
        int radius = mc.options.getEffectiveRenderDistance();

        int viewMin = camSecY - Config.verticalSimulationDistance;
        int viewMax = camSecY + Config.verticalSimulationDistance;
        boolean viewChanged = viewMin != lastDiscardViewMin || viewMax != lastDiscardViewMax;
        long now = System.nanoTime();
        boolean discardNeeded = viewChanged || (now - lastDiscardTime >= 1_000_000_000L);
        if (discardNeeded) {
            lastDiscardViewMin = viewMin;
            lastDiscardViewMax = viewMax;
            lastDiscardTime = now;
        }

        visited.clear();
        for (int cx = chunkX - radius; cx <= chunkX + radius; cx++) {
            for (int cz = chunkZ - radius; cz <= chunkZ + radius; cz++) {
                ChunkAccess ca = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (!(ca instanceof LevelChunk chunk) || chunk instanceof EmptyLevelChunk) {
                    continue;
                }
                ChunkPos cpos = chunk.getPos();
                visited.add(encode(cpos));

                WindowedChunk wc = (WindowedChunk) chunk;
                wc.moveWindowTo(camSecY);
                // §7.3 滑出丢弃（view 变化帧 + 每 20 帧兜底）
                if (discardNeeded) {
                    discardOutsideHoldBoundary(chunk);
                }

                int minY = wc.getWindowMinY();
                Integer lastMinY = lastWindowByChunk.get(cpos);
                if (lastMinY != null && lastMinY == minY) {
                    continue; // 窗口未变
                }
                // 窗口变化：补齐新窗口集合 + 清理滑出 section
                try {
                    Object rsm = renderSectionManager();
                    if (rsm != null) {
                        mOnChunkAdded.invoke(rsm, cx, cz);
                        if (lastMinY != null) {
                            removeSectionsOutsideWindow(rsm, cx, cz, lastMinY, minY);
                        }
                    }
                } catch (Exception e) {
                    InfFarlands.LOGGER.warn("SodiumWindowSync: window sync failed", e);
                    warnOnce();
                    disabled = true;
                    return;
                }
                lastWindowByChunk.put(cpos, minY);
            }
        }

        // 清理记录：不在本次视距的 chunk（已卸载）
        lastWindowByChunk.keySet().removeIf(p -> !visited.contains(encode(p)));
    }

    /**
     * 旧窗口 [oldMin, oldMin+33] 中不在新窗口 [newMin, newMin+33] 的 sectionY →
     * onSectionRemoved。
     */
    private static void removeSectionsOutsideWindow(Object rsm, int cx, int cz, int oldMin, int newMin)
            throws Exception {
        int oldMax = oldMin + Config.verticalSimulationDistance * 2;
        int newMax = newMin + Config.verticalSimulationDistance * 2;
        // 下边界滑出（玩家上移）
        for (int sy = oldMin; sy < newMin && sy <= oldMax; sy++) {
            mOnSectionRemoved.invoke(rsm, cx, sy, cz);
        }
        // 上边界滑出（玩家下移）：起点限制在旧窗口内——tp 向下大跳时 newMax+1 远低于
        // oldMin，无下限会遍历 [newMax+1, oldMax]（-2.14B 场景 1.34 亿次反射调用）冻结
        for (int sy = Math.max(newMax + 1, oldMin); sy <= oldMax; sy++) {
            mOnSectionRemoved.invoke(rsm, cx, sy, cz);
        }
    }

    /**
     * §7.3 滑出丢弃（sodium 版，修订同 ViewArea：丢弃参考 = 持有边界 ∪ 视图窗口并集）。
     * 空 section 不丢（防懒创建循环）。onSectionRemoved 反射失败仅 WARN 不禁用——
     * 窗口跟随（onChunkAdded）独立可用，丢弃降级为保守持有（内存略增，无碍）。
     */
    @SuppressWarnings("null")
    private static void discardOutsideHoldBoundary(LevelChunk chunk) {
        WindowedChunk wc = (WindowedChunk) chunk;
        int viewMin = wc.getWindowMinY();
        int viewMax = wc.getWindowMaxY();
        int holdMin = wc.lastPacketMinY();
        int dropBelow;
        int dropAbove;
        if (holdMin == Integer.MIN_VALUE) {
            dropBelow = viewMin - 2;
            dropAbove = viewMax + 2;
        } else {
            dropBelow = Math.min(holdMin, viewMin) - 2;
            dropAbove = Math.max(wc.lastPacketMaxY(), viewMax) + 2;
        }
        ChunkPos cpos = chunk.getPos();
        ClientLevel level = Minecraft.getInstance().level;
        Iterator<Map.Entry<Integer, LevelChunkSection>> it = wc.windowedAllSections().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, LevelChunkSection> e = it.next();
            int sy = e.getKey();
            LevelChunkSection s = e.getValue();
            if (s != null && !s.hasOnlyAir() && (sy < dropBelow || sy > dropAbove)) {
                it.remove();
                if (level != null) {
                    SectionPos spos = SectionPos.of(cpos, sy);
                    LevelLightEngine le = level.getLightEngine();
                    le.queueSectionData(LightLayer.BLOCK, spos, null);
                    le.queueSectionData(LightLayer.SKY, spos, null);
                    le.updateSectionStatus(spos, true);
                }
                sodiumOnSectionRemoved(chunk, sy);
            }
        }
    }

    private static void sodiumOnSectionRemoved(LevelChunk chunk, int sy) {
        try {
            Object rsm = renderSectionManager(); // 集中初始化含 mOnSectionRemoved
            if (rsm != null) {
                mOnSectionRemoved.invoke(rsm, chunk.getPos().x, sy, chunk.getPos().z);
            }
        } catch (Exception e) {
            InfFarlands.LOGGER.warn("SodiumWindowSync: section removal failed", e);
            warnOnce();
        }
    }

    private static Object renderSectionManager() throws Exception {
        if (swrClass == null) {
            swrClass = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
            mInstanceNullable = swrClass.getMethod("instanceNullable");
            fRenderSectionManager = swrClass.getDeclaredField("renderSectionManager");
            fRenderSectionManager.setAccessible(true);
            Class<?> rsmClass = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager");
            mOnChunkAdded = rsmClass.getMethod("onChunkAdded", int.class, int.class);
            mOnSectionRemoved = rsmClass.getMethod("onSectionRemoved", int.class, int.class, int.class);
        }
        Object swr = mInstanceNullable.invoke(null);
        return swr == null ? null : fRenderSectionManager.get(swr);
    }

    private static void warnOnce() {
        if (!warned) {
            warned = true;
            InfFarlands.LOGGER.warn("SodiumWindowSync: sodium reflection failed, window sync disabled");
        }
    }

    /** vanilla 32-32 无损编码（与 ChunkPos.asLong 同布局；手写以绕开其 side-channel）。 */
    private static long encode(ChunkPos p) {
        return (long) p.x & 4294967295L | ((long) p.z & 4294967295L) << 32;
    }
}
