package com.inf.farlands.mixin.axisYOverflowFix;

import com.inf.farlands.WindowedChunk;
import com.inf.farlands.network.FarLandsLightUpdatePacket;
import com.inf.farlands.network.FarLandsSectionBlocksUpdatePacket;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import it.unimi.dsi.fastutil.shorts.ShortSet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
public class ChunkHolderMixin {

    /**
     * 方块变化按「绝对 sectionY」记录（Map），广播时用绝对坐标 + 3int 编码。
     * 这取代了 vanilla 的 changedBlocksPerSection 数组（其索引是 Level 的
     * section 索引，极端 Y 越界；若改用窗口索引则与 broadcastChanges 的
     * vanilla 解码错位，导致 sectionY 系统性偏移 -(windowMinY+4)——
     * 表现为真树叶上方 112 格出现无法破坏的假树叶投影）。
     *
     * 批量路径发送 mod 自定义包 FarLandsSectionBlocksUpdatePacket（3int 编码），
     * 因为 vanilla ClientboundSectionBlocksUpdatePacket 用 SectionPos.asLong()
     * 哈希编码，side-channel 是进程内的：客户端解码时 miss → 回退位解码 →
     * 极端 Y 截断。
     */
    @Unique
    private final Map<Integer, ShortSet> changedSectionsMap = new HashMap<>();

    /** 光照增量 affected section（绝对 sectionY）——sectionLightChanged @Inject 收集，broadcastChanges 消费。 */
    @Unique
    private final LongOpenHashSet affectedSkySections = new LongOpenHashSet();

    @Unique
    private final LongOpenHashSet affectedBlockSections = new LongOpenHashSet();

    @Shadow
    private boolean hasChangedSections;

    @Shadow
    private ChunkHolder.PlayerProvider playerProvider;

    @Shadow
    @Final
    private BitSet blockChangedLightSectionFilter;

    @Shadow
    @Final
    private BitSet skyChangedLightSectionFilter;

    @Shadow
    @Final
    private LevelLightEngine lightEngine;

    @Shadow
    private LevelChunk getTickingChunk() {
        return null;
    }

    @Shadow
    private void broadcast(List<ServerPlayer> players, Packet<?> packet) {
    }

    @Shadow
    private void broadcastBlockEntityIfNeeded(List<ServerPlayer> players, Level level, BlockPos pos, BlockState state) {
    }

    @Overwrite
    public void blockChanged(BlockPos pos) {
        LevelChunk levelchunk = this.getTickingChunk();
        if (levelchunk != null) {
            int sy = SectionPos.blockToSectionCoord(pos.getY());
            ShortSet set = this.changedSectionsMap.computeIfAbsent(sy, s -> new ShortOpenHashSet());
            set.add(SectionPos.sectionRelativePos(pos));
            this.hasChangedSections = true;
        }
    }

    /**
     * 光照变化收集（绝对 sectionY，无范围限制）。vanilla 原方法用 BitSet 索引
     * （sectionY - minLightSection，范围 [-5,21]）——极端 Y 会内存爆炸且被挡；
     * 这里 @Inject RETURN 追加收集（原方法体完整执行：setUnsaved/ticking/范围检查）。
     */
    @Inject(method = "sectionLightChanged", at = @At("RETURN"))
    private void collectLightSection(LightLayer type, int sectionY, CallbackInfo ci) {
        if (this.getTickingChunk() != null) {
            if (type == LightLayer.SKY) {
                this.affectedSkySections.add(sectionY);
            } else {
                this.affectedBlockSections.add(sectionY);
            }
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    public void broadcastChanges(LevelChunk chunk) {
        if (!this.hasChangedSections
                && this.skyChangedLightSectionFilter.isEmpty()
                && this.blockChangedLightSectionFilter.isEmpty()
                && this.affectedSkySections.isEmpty()
                && this.affectedBlockSections.isEmpty()) {
            return;
        }

        Level level = chunk.getLevel();
        // 光照增量：统一走自定义包 FarLandsLightUpdatePacket（绝对 sectionY，无范围限制）。
        // vanilla ClientboundLightUpdatePacket 的 BitSet 索引在极端 Y 会内存爆炸且范围
        // [-5,21] 挡掉极端 Y。BitSet filter 仍被 vanilla sectionLightChanged 填充，
        // 这里无条件清空防残留。
        if (!this.affectedSkySections.isEmpty() || !this.affectedBlockSections.isEmpty()) {
            List<ServerPlayer> list = this.playerProvider.getPlayers(chunk.getPos(), true);
            if (!list.isEmpty()) {
                this.broadcast(list, new ClientboundCustomPayloadPacket(buildLightUpdatePacket(chunk)));
            }
            this.affectedSkySections.clear();
            this.affectedBlockSections.clear();
        }
        this.skyChangedLightSectionFilter.clear();
        this.blockChangedLightSectionFilter.clear();

        if (!this.hasChangedSections) {
            return;
        }

        List<ServerPlayer> players = this.playerProvider.getPlayers(chunk.getPos(), false);
        for (Map.Entry<Integer, ShortSet> entry : this.changedSectionsMap.entrySet()) {
            ShortSet shortset = entry.getValue();
            if (shortset.isEmpty()) {
                continue;
            }

            if (players.isEmpty()) {
                continue;
            }

            int sy = entry.getKey();
            SectionPos sectionpos = SectionPos.of(chunk.getPos(), sy);
            if (shortset.size() == 1) {
                BlockPos blockpos = sectionpos.relativeToBlockPos(shortset.iterator().nextShort());
                BlockState blockstate = level.getBlockState(blockpos);
                this.broadcast(players, new ClientboundBlockUpdatePacket(blockpos, blockstate));
                this.broadcastBlockEntityIfNeeded(players, level, blockpos, blockstate);
            } else {
                LevelChunkSection section = ((WindowedChunk) chunk).windowedAllSections().get(sy);
                if (section == null) {
                    continue;
                }
                int size = shortset.size();
                short[] positions = new short[size];
                BlockState[] states = new BlockState[size];
                int idx = 0;
                for (short s : shortset) {
                    positions[idx] = s;
                    states[idx] = section.getBlockState(
                            SectionPos.sectionRelativeX(s),
                            SectionPos.sectionRelativeY(s),
                            SectionPos.sectionRelativeZ(s));
                    idx++;
                }
                FarLandsSectionBlocksUpdatePacket pkt = new FarLandsSectionBlocksUpdatePacket(
                        sectionpos, positions, states);
                this.broadcast(players, new ClientboundCustomPayloadPacket(pkt));
                pkt.runUpdates((p, st) -> this.broadcastBlockEntityIfNeeded(players, level, p, st));
            }
        }
        this.changedSectionsMap.clear();
        this.hasChangedSections = false;
    }

    // ---- 自定义光照增量包打包 ----

    @Unique
    private FarLandsLightUpdatePacket buildLightUpdatePacket(LevelChunk chunk) {
        ChunkPos cpos = chunk.getPos();
        List<FarLandsLightUpdatePacket.SectionLight> sky = buildLightLayer(cpos, LightLayer.SKY, this.affectedSkySections);
        List<FarLandsLightUpdatePacket.SectionLight> block = buildLightLayer(cpos, LightLayer.BLOCK, this.affectedBlockSections);
        return new FarLandsLightUpdatePacket(cpos.x, cpos.z, sky, block);
    }
    @SuppressWarnings({ "null" })
    @Unique
    private List<FarLandsLightUpdatePacket.SectionLight> buildLightLayer(
            ChunkPos cpos, LightLayer layer, LongOpenHashSet affected) {
        List<Integer> sys = new ArrayList<>();
        for (long v : affected) {
            sys.add((int) v);
        }
        Collections.sort(sys); // 确定性发送
        List<FarLandsLightUpdatePacket.SectionLight> out = new ArrayList<>();
        for (int sy : sys) {
            DataLayer dl = this.lightEngine.getLayerListener(layer).getDataLayerData(SectionPos.of(cpos, sy));
            out.add(new FarLandsLightUpdatePacket.SectionLight(sy, FarLandsLightUpdatePacket.encodeSectionLight(dl)));
        }
        return out;
    }
}
