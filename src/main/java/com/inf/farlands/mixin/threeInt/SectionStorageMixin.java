package com.inf.farlands.mixin.threeInt;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

import com.inf.farlands.IntSectionPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;

import net.minecraft.SharedConstants;
import net.minecraft.core.SectionPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.ChunkIOErrorReporter;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.resources.RegistryOps;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@SuppressWarnings({ "unchecked", "rawtypes" })
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin {

    @Shadow
    protected LevelHeightAccessor levelHeightAccessor;

    @Shadow
    private Long2ObjectMap storage;

    @Shadow
    private LongLinkedOpenHashSet dirty;

    @Shadow
    private Function<Runnable, Codec> codec;

    @Shadow
    private RegistryAccess registryAccess;

    @Shadow
    private SimpleRegionStorage simpleRegionStorage;

    @Shadow
    private ChunkIOErrorReporter errorReporter;

    @Shadow
    protected abstract void onSectionLoad(long sectionKey);

    @Shadow
    protected abstract void setDirty(long sectionPos);

    @Shadow
    public abstract boolean hasWork();

    private static final Logger LOGGER = LogUtils.getLogger();

    @Overwrite
    protected boolean outsideStoredRange(long sectionKey) {
        IntSectionPos sp = IntSectionPos.getSectionPos(sectionKey);
        int i = SectionPos.sectionToBlockCoord(sp.y);
        return this.levelHeightAccessor.isOutsideBuildHeight(i);
    }

    @SuppressWarnings("null")
    @Overwrite
    private void readColumn(ChunkPos chunkPos,
            RegistryOps<Tag> ops,
            @Nullable CompoundTag tag) {
        int minSection = this.levelHeightAccessor.getMinSection();
        int maxSection = this.levelHeightAccessor.getMaxSection();

        // 先初始化世界范围 —— getOrLoad 要求范围内每个 section 在 storage 都有非 null Optional。
        if (tag == null) {
            for (int i = minSection; i < maxSection; i++) {
                this.storage.put(SectionPos.asLong(chunkPos.x, i, chunkPos.z), Optional.empty());
            }
            return;
        }

        Dynamic<Tag> dynamic1 = new Dynamic<>(ops, tag);
        int version = dynamic1.get("DataVersion").asInt(1945);
        int current = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        boolean needsUpgrade = version != current;
        Dynamic<Tag> dynamic = this.simpleRegionStorage.upgradeChunkTag(dynamic1, version);

        CompoundTag sectionsTag = tag.getCompound("Sections");

        for (int i = minSection; i < maxSection; i++) {
            long i1 = SectionPos.asLong(chunkPos.x, i, chunkPos.z);
            String key = Integer.toString(i);
            if (sectionsTag.contains(key)) {
                Optional optional = dynamic.get("Sections").get(key)
                        .result()
                        .flatMap(p -> this.codec.apply(() -> this.setDirty(i1))
                                .parse((Dynamic) p)
                                .resultOrPartial(msg -> LOGGER.error("{}", msg)));
                this.storage.put(i1, optional);
                optional.ifPresent(r -> {
                    this.onSectionLoad(i1);
                    if (needsUpgrade) {
                        this.setDirty(i1);
                    }
                });
            } else {
                this.storage.put(i1, Optional.empty());
            }
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    private void writeColumn(ChunkPos chunkPos) {
        RegistryOps<Tag> registryops = this.registryAccess
                .createSerializationContext(NbtOps.INSTANCE);
        Dynamic dynamic = this.writeColumn(chunkPos, (DynamicOps) (Object) registryops);
        Object val = dynamic.getValue();
        if (val instanceof CompoundTag ct) {
            this.simpleRegionStorage.write(chunkPos, ct).exceptionally(p -> {
                this.errorReporter.reportChunkSaveFailure(p, this.simpleRegionStorage.storageInfo(), chunkPos);
                return null;
            });
        } else {
            LOGGER.error("Expected compound tag, got {}", val);
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    private Dynamic writeColumn(ChunkPos chunkPos, DynamicOps ops) {
        Map map = Maps.newHashMap();
        for (Object k : this.storage.keySet()) {
            long key = (long) k;
            IntSectionPos sp = IntSectionPos.getSectionPos(key);
            if (sp.x != chunkPos.x || sp.z != chunkPos.z)
                continue;
            this.dirty.remove(key);
            Optional optional = (Optional) this.storage.get(key);
            if (optional != null && !optional.isEmpty()) {
                DataResult dataresult = this.codec.apply(() -> this.setDirty(key))
                        .encodeStart(ops, optional.get());
                String s = Integer.toString(sp.y);
                dataresult.resultOrPartial(msg -> LOGGER.error("{}", msg))
                        .ifPresent(v -> map.put(ops.createString(s), v));
            }
        }
        return new Dynamic(ops, ops.createMap(
                ImmutableMap.of(
                        ops.createString("Sections"), ops.createMap(map),
                        ops.createString("DataVersion"),
                        ops.createInt(SharedConstants.getCurrentVersion().getDataVersion().getVersion()))));
    }

    @Overwrite
    public void flush(ChunkPos chunkPos) {
        if (this.hasWork()) {
            for (Object k : this.storage.keySet()) {
                long key = (long) k;
                IntSectionPos sp = IntSectionPos.getSectionPos(key);
                if (sp.x != chunkPos.x || sp.z != chunkPos.z)
                    continue;
                if (this.dirty.contains(key)) {
                    this.writeColumn(chunkPos);
                    return;
                }
            }
        }
    }
}
