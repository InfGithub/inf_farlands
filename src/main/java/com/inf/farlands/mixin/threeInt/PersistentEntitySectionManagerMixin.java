package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.window.EntitySectionWindow;
import com.inf.farlands.util.IntSectionPos;
import com.inf.farlands.window.ServerEntitySectionStorage;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.Map;

import net.minecraft.util.CsvOutput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.world.level.entity.LevelCallback;
import net.minecraft.world.level.entity.EntityPersistentStorage;

@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {

    /**
     * 服务端实体 section 存储标记——createSection 的窗口过滤仅服务端生效。
     * 客户端 TransientEntitySectionManager 也用 EntitySectionStorage，未标记则
     * 不过滤：客户端实体必须持续 tick 插值，否则瞬移/鬼畜。
     */
    @Inject(method = "<init>(Ljava/lang/Class;Lnet/minecraft/world/level/entity/LevelCallback;Lnet/minecraft/world/level/entity/EntityPersistentStorage;)V", at = @At("RETURN"))
    private void markStorageServerSide(Class<T> entityClass,
            LevelCallback<T> callbacks,
            EntityPersistentStorage<T> permanentStorage,
            CallbackInfo ci) {
        ((ServerEntitySectionStorage) this.sectionStorage).markServerSide();
    }

    @Shadow
    private EntitySectionStorage<T> sectionStorage;
    @Shadow
    private Long2ObjectMap<Object> chunkLoadStatuses;
    @Shadow
    private Long2ObjectMap<Visibility> chunkVisibility;
    @Shadow
    private LongSet chunksToUnload;

    @Shadow
    private void ensureChunkQueuedForLoad(long chunkPosValue) {
    }

    @Shadow
    void startTicking(T entity) {
    }

    @Shadow
    void stopTicking(T entity) {
    }

    @Shadow
    void startTracking(T entity) {
    }

    @Shadow
    void stopTracking(T entity) {
    }

    /**
     * 实体 section 状态窗口感知：与 vanilla 逐行一致，另加窗口过滤。
     * chunk 状态变化时遍历全部 sections——不在任何玩家 [secY−17, secY+16]
     * 内的窗口外 section 的 TICKING 降为 TRACKED：实体不 tick、保留
     * accessible，即碰撞/查询/交互仍可用。窗口滑动由 InfFarlands 补触发本方法。
     */
    @SuppressWarnings({ "rawtypes", "unchecked", "null" })
    @Overwrite
    public void updateChunkStatus(ChunkPos pos, Visibility visibility) {
        long i = pos.toLong();
        if (visibility == Visibility.HIDDEN) {
            this.chunkVisibility.remove(i);
            this.chunksToUnload.add(i);
        } else {
            this.chunkVisibility.put(i, visibility);
            this.chunksToUnload.remove(i);
            this.ensureChunkQueuedForLoad(i);
        }
        Map<Long, EntitySection> sections = sectionsMap(this.sectionStorage);
        for (Map.Entry<Long, EntitySection> e : sections.entrySet()) {
            IntSectionPos sp = IntSectionPos.getSectionPos(e.getKey());
            if (ChunkPos.asLong(sp.x, sp.z) != i) {
                continue;
            }
            Visibility applied = visibility;
            if (applied == Visibility.TICKING && !EntitySectionWindow.inAnyWindow(sp.y)) {
                applied = Visibility.TRACKED;
            }
            EntitySection<T> section = e.getValue();
            Visibility old = section.updateChunkStatus(applied);
            boolean oldAccessible = old.isAccessible();
            boolean newAccessible = applied.isAccessible();
            boolean oldTicking = old.isTicking();
            boolean newTicking = applied.isTicking();
            if (oldTicking && !newTicking) {
                section.getEntities().filter(ent -> !ent.isAlwaysTicking())
                    .forEach(this::stopTicking);
            }
            if (oldAccessible && !newAccessible) {
                section.getEntities().filter(ent -> !ent.isAlwaysTicking()).forEach(this::stopTracking);
            } else if (!oldAccessible && newAccessible) {
                section.getEntities().filter(ent -> !ent.isAlwaysTicking()).forEach(this::startTracking);
            }
            if (!oldTicking && newTicking) {
                section.getEntities().filter(ent -> !ent.isAlwaysTicking()).forEach(this::startTicking);
            }
        }
    }

    private static final Field F_SECTIONS;
    static {
        try {
            F_SECTIONS = EntitySectionStorage.class.getDeclaredField("sections");
            F_SECTIONS.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Unique
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Map<Long, EntitySection> sectionsMap(EntitySectionStorage storage) {
        try {
            return (Map<Long, EntitySection>) F_SECTIONS.get(storage);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    public void dumpSections(Writer writer) throws IOException {
        CsvOutput csvoutput = CsvOutput.builder()
                .addColumn("x")
                .addColumn("y")
                .addColumn("z")
                .addColumn("visibility")
                .addColumn("load_status")
                .addColumn("entity_count")
                .build(writer);

        this.sectionStorage.getAllChunksWithExistingSections().forEach(chunkKey -> {
            Object status = this.chunkLoadStatuses.get(chunkKey);
            this.sectionStorage.getExistingSectionPositionsInChunk(chunkKey).forEach(secKey -> {
                EntitySection<T> section = this.sectionStorage.getSection(secKey);
                if (section != null) {
                    try {
                        IntSectionPos sp = IntSectionPos.getSectionPos(secKey);
                        csvoutput.writeRow(sp.x, sp.y, sp.z, section.getStatus(), status, section.size());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        });
    }
}
