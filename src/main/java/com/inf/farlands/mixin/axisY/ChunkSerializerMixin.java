package com.inf.farlands.mixin.axisY;

import com.inf.farlands.HashUtil;
import com.inf.farlands.WindowedChunk;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.lighting.LevelLightEngine;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

@Mixin(ChunkSerializer.class)
public class ChunkSerializerMixin {

    private static final Codec<PalettedContainer<BlockState>> BLOCK_EC;
    private static final Method MAKE_BIOME_CODEC;

    static {
        try {
            Field bf = ChunkSerializer.class.getDeclaredField("BLOCK_STATE_CODEC");
            bf.setAccessible(true);
            @SuppressWarnings("unchecked")
            Codec<PalettedContainer<BlockState>> c = (Codec<PalettedContainer<BlockState>>) bf.get(null);
            BLOCK_EC = c;

            MAKE_BIOME_CODEC = ChunkSerializer.class.getDeclaredMethod("makeBiomeCodec", Registry.class);
            MAKE_BIOME_CODEC.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private static LevelChunkSection[] redirectGetSections(ChunkAccess chunk) {
        try {
            LevelHeightAccessor lha = ((WindowedChunk) chunk).levelHeightAccessor();
            Map<Integer, LevelChunkSection> all = ((WindowedChunk) chunk).windowedAllSections();
            int cMin = chunk.getMinSection(), cMax = chunk.getMaxSection();
            int lMin = lha.getMinSection(), lMax = lha.getMaxSection();
            if (cMax <= lMin - 1 || cMin >= lMax + 1) {
                return new LevelChunkSection[0];
            }
            int min = Math.min(cMin, lMin);
            int max = Math.max(cMax, lMax);
            LevelChunkSection[] arr = new LevelChunkSection[max - min];
            for (int i = min; i < max; i++) {
                LevelChunkSection s = all.get(i);
                if (s == null) {
                    s = chunk.getSection(lha.getSectionIndexFromSectionY(i));
                }
                arr[i - min] = s;
            }
            return arr;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("null")
    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Lnet/minecraft/nbt/CompoundTag;getByte(Ljava/lang/String;)B"))
    private static byte redirectGetY(CompoundTag tag, String key) {
        if (tag.contains(key, 3)) {
            int sy = tag.getInt(key);
            if (sy < Byte.MIN_VALUE || sy > Byte.MAX_VALUE) {
                return Byte.MIN_VALUE;
            }
            return (byte) sy;
        }
        return tag.getByte(key);
    }

    @SuppressWarnings({ "unchecked", "null" })
    @Inject(method = "write", at = @At("RETURN"), cancellable = true)
    private static void appendWindowSections(ServerLevel level, ChunkAccess chunk,
            CallbackInfoReturnable<CompoundTag> cir) {
        try {
            CompoundTag tag = cir.getReturnValue();
            int lMin = level.getMinSection(), lMax = level.getMaxSection();
            LevelChunkSection[] secs = chunk.getSections();
            if (secs.length == 0)
                return;

            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            Codec<PalettedContainerRO<Holder<Biome>>> bioCodec = (Codec<PalettedContainerRO<Holder<Biome>>>) MAKE_BIOME_CODEC
                    .invoke(null, biomes);
            LevelLightEngine le = level.getChunkSource().getLightEngine();
            ChunkPos cpos = chunk.getPos();

            Map<Integer, LevelChunkSection> all = ((WindowedChunk) chunk).windowedAllSections();
            ListTag list = tag.getList("sections", 10);
            // Remove stale extreme-Y entries from previous saves to prevent duplicates
            list.removeIf(e -> {
                int ey = ((CompoundTag) e).contains("Y", 3) ? ((CompoundTag) e).getInt("Y")
                        : ((CompoundTag) e).getByte("Y");
                return ey < lMin - 1 || ey > lMax;
            });
            for (Map.Entry<Integer, LevelChunkSection> kv : all.entrySet()) {
                int sy = kv.getKey();
                // 跳过维度 section 范围 [-4, 19]（vanilla 循环处理）；-5/20（light 范围边界、
                // vanilla 数组越界不写方块）与极端 Y 由本方法补写——原范围 [-5,20] 漏 -5/20
                // 方块持久化（下界传送门往返重载后 y=320~335 / y=-96~-81 变空气）
                if (sy >= lMin && sy < lMax)
                    continue;

                LevelChunkSection s = kv.getValue();
                Object blListener = le.getLayerListener(LightLayer.BLOCK);
                Object slListener = le.getLayerListener(LightLayer.SKY);
                DataLayer bl = le.getLayerListener(LightLayer.BLOCK).getDataLayerData(SectionPos.of(cpos, sy));
                DataLayer sl = le.getLayerListener(LightLayer.SKY).getDataLayerData(SectionPos.of(cpos, sy));
                // updating 兜底：层未 swap 时 getDataLayerData（queued ∪ visible）可能为 null
                if (bl == null && blListener instanceof net.minecraft.world.level.lighting.BlockLightEngine ble) {
                    bl = HashUtil.callGetDataLayer(HashUtil.blockStorage(ble),
                            SectionPos.asLong(cpos.x, sy, cpos.z), true);
                }
                if (sl == null && slListener instanceof net.minecraft.world.level.lighting.SkyLightEngine sle) {
                    sl = HashUtil.callGetDataLayer(HashUtil.skyStorage(sle),
                            SectionPos.asLong(cpos.x, sy, cpos.z), true);
                }
                boolean hasBlock = s != null && !s.hasOnlyAir();
                boolean hasLight = (bl != null && !bl.isEmpty()) || (sl != null && !sl.isEmpty());
                if (!hasBlock && !hasLight)
                    continue;

                CompoundTag entry = new CompoundTag();
                if (hasBlock) {
                    entry.put("block_states", BLOCK_EC.encodeStart(NbtOps.INSTANCE, s.getStates()).getOrThrow());
                    entry.put("biomes", bioCodec.encodeStart(NbtOps.INSTANCE, s.getBiomes()).getOrThrow());
                }
                if (bl != null && !bl.isEmpty()) {
                    entry.putByteArray("BlockLight", bl.getData());
                }
                if (sl != null && !sl.isEmpty()) {
                    entry.putByteArray("SkyLight", sl.getData());
                }
                entry.putInt("Y", sy);
                list.add(entry);
            }
            tag.put("sections", list);
            cir.setReturnValue(tag);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({ "unchecked", "null" })
    @Inject(method = "read", at = @At("RETURN"), cancellable = true)
    private static void loadWindowSections(ServerLevel level,
            PoiManager poiManager,
            RegionStorageInfo regionStorageInfo,
            ChunkPos pos, CompoundTag tag,
            CallbackInfoReturnable<ProtoChunk> cir) {
        try {
            ProtoChunk proto = cir.getReturnValue();
            ChunkAccess target = proto instanceof ImposterProtoChunk ipc ? ipc.getWrapped() : proto;
            Map<Integer, LevelChunkSection> all = ((WindowedChunk) target).windowedAllSections();

            int lMin = level.getMinSection(), lMax = level.getMaxSection();
            Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
            Codec<PalettedContainerRO<Holder<Biome>>> bioCodec = (Codec<PalettedContainerRO<Holder<Biome>>>) MAKE_BIOME_CODEC
                    .invoke(null, biomes);

            ListTag list = tag.getList("sections", 10);

            LevelLightEngine le = level.getChunkSource().getLightEngine();
            // 清理 vanilla read 的 -128 错位污染：极端 Y entry 的 Y 被 redirectGetY 截断成
            // Byte.MIN_VALUE（-128）→ vanilla read 的光照恢复（L146/L150，在范围检查外）把
            // 极端 Y 光照恢复到 SectionPos.of(pos,-128)——正确位置由下面 queueSectionData 恢复，
            // 这里先清错位层（-128 在维度最低之下，无正常数据，remove 幂等无害）。
            le.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, -128), null);
            le.queueSectionData(LightLayer.SKY, SectionPos.of(pos, -128), null);

            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int sy = entry.contains("Y", 3) ? entry.getInt("Y") : entry.getByte("Y");
                // 恢复维度 section 范围外（含 -5/20 的 append 补写 entry——vanilla read 数组越界不恢复）
                if (sy >= lMin && sy < lMax)
                    continue;

                if (entry.contains("block_states", 10) && entry.contains("biomes", 10)) {
                    PalettedContainer<BlockState> states = BLOCK_EC
                            .parse(NbtOps.INSTANCE, entry.getCompound("block_states"))
                            .getOrThrow(ChunkSerializer.ChunkReadException::new);
                    PalettedContainerRO<Holder<Biome>> bios = (PalettedContainerRO<Holder<Biome>>) bioCodec
                            .parse(NbtOps.INSTANCE, entry.getCompound("biomes"))
                            .getOrThrow(ChunkSerializer.ChunkReadException::new);
                    LevelChunkSection s = new LevelChunkSection(states,
                            (PalettedContainerRO<Holder<Biome>>) bios);
                    all.put(sy, s);
                }
                // 恢复范围外光照：直调 FarLandsLightEngine.queueSectionData（public @Override，
                // data 非 null → setDataLayer 同步写 storage——绕过 vanilla TTLE 异步队列，同 tick
                // §5 flush 读 getDataLayerData 不 miss）。不能用 instanceof BlockLightEngine/SkyLightEngine
                // （vanilla 类）——引擎替换后 getLayerListener 返回 FarLands 引擎，instanceof 恒 false
                // → 极端 Y 光照持久化恢复失效（重进黑）。
                if (entry.contains("BlockLight", 7)) {
                    DataLayer bl = new DataLayer(entry.getByteArray("BlockLight"));
                    if (!bl.isEmpty()) {
                        le.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, sy), bl);
                    }
                }
                if (entry.contains("SkyLight", 7)) {
                    DataLayer sl = new DataLayer(entry.getByteArray("SkyLight"));
                    if (!sl.isEmpty()) {
                        le.queueSectionData(LightLayer.SKY, SectionPos.of(pos, sy), sl);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
