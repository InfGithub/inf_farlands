package com.inf.farlands.mixin.threeInt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Unique;

import com.inf.farlands.EntitySectionWindow;
import com.inf.farlands.IntSectionPos;
import com.inf.farlands.ServerEntitySectionStorage;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;

@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> implements ServerEntitySectionStorage {

    /** 服务端标记（PersistentEntitySectionManager 构造时置 true；客户端 Transient 不标记）。 */
    @Unique
    private boolean serverSide;

    @Override
    public void markServerSide() {
        this.serverSide = true;
    }

    @Shadow
    private it.unimi.dsi.fastutil.longs.Long2ObjectFunction<Visibility> intialSectionVisibility;

    @Shadow
    private Class<? extends T> entityClass;

    /**
     * 新 section 初始 visibility 窗口感知。vanilla 继承 chunk 当前
     * visibility（TICKING chunk 的新 section = TICKING，不经过 updateChunkStatus
     * 过滤）——实体跨 section 移动/新实体加入时窗口外 section 会被错误地 ticking。
     * 创建时降级：TICKING + 窗口外 → TRACKED。
     * （现有 onCreateSection @Inject TAIL 的 3int 索引保留，注入到覆盖后方法体）
     */
    @Overwrite
    @SuppressWarnings({ "unchecked", "null" })
    private EntitySection<T> createSection(long sectionPos) {
        long i = getChunkKeyFromSectionKey(sectionPos);
        Visibility visibility = this.intialSectionVisibility.get(i);
        if (this.serverSide && visibility == Visibility.TICKING
                && !EntitySectionWindow.inAnyWindow(IntSectionPos.getSectionPos(sectionPos).y)) {
            visibility = Visibility.TRACKED;
        }
        this.sectionIds.add(sectionPos);
        return new EntitySection<>((Class<T>) this.entityClass, visibility);
    }

    @Unique
    private final Long2IntMap sectionXByKey = new Long2IntOpenHashMap();
    @Unique
    private final Int2ObjectMap<LongSet> sectionsByX = new Int2ObjectOpenHashMap<>();

    // 按 X 轴索引，提升效率
    @Inject(method = "createSection", at = @At("TAIL"))
    private void onCreateSection(long sectionPos, CallbackInfoReturnable<EntitySection<T>> cir) {
        int sx = IntSectionPos.getSectionPos(sectionPos).x;
        sectionXByKey.put(sectionPos, sx);
        sectionsByX.computeIfAbsent(sx, k -> new LongOpenHashSet()).add(sectionPos);
    }

    // public void remove(long sectionId) {
    // this.sections.remove(sectionId);
    // this.sectionIds.remove(sectionId);
    // }
    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(long sectionId, CallbackInfo ci) {
        int sx = sectionXByKey.remove(sectionId);
        int defaultX = sectionXByKey.defaultReturnValue();

        if (sx == defaultX) {
            return;
        }

        LongSet set = sectionsByX.get(sx);
        if (set == null) {
            return;
        }

        set.remove(sectionId);
        if (set.isEmpty()) {
            sectionsByX.remove(sx);
        }
    }

    private final Long2ObjectMap<EntitySection<T>> sections = new Long2ObjectOpenHashMap<>();

    @Overwrite
    public void forEachAccessibleNonEmptySection(AABB bounds, AbortableIterationConsumer<EntitySection<T>> consumer) {
        int minSecX = SectionPos.posToSectionCoord(bounds.minX - 2.0);
        int maxSecX = SectionPos.posToSectionCoord(bounds.maxX + 2.0);
        int minSecY = SectionPos.posToSectionCoord(bounds.minY - 4.0);
        int maxSecY = SectionPos.posToSectionCoord(bounds.maxY + 0.0);
        int minSecZ = SectionPos.posToSectionCoord(bounds.minZ - 2.0);
        int maxSecZ = SectionPos.posToSectionCoord(bounds.maxZ + 2.0);

        for (int sx = minSecX; sx <= maxSecX; sx++) {
            LongSet keys = sectionsByX.get(sx);
            if (keys == null) {
                continue;
            }

            for (long key : keys) {
                IntSectionPos pos = IntSectionPos.getSectionPos(key);
                if (pos.y < minSecY || pos.y > maxSecY || pos.z < minSecZ || pos.z > maxSecZ) {
                    continue;
                }
                EntitySection<T> section = this.sections.get(key);
                if (section != null &&
                        !section.isEmpty() &&
                        section.getStatus().isAccessible() &&
                        consumer.accept(section).shouldAbort()) {
                    return;
                }
            }
        }
    }

    // private LongSortedSet getChunkSections(int x, int z) {
    // long i = SectionPos.asLong(x, 0, z);
    // long j = SectionPos.asLong(x, -1, z);
    // return this.sectionIds.subSet(i, j + 1L);
    // }
    @Shadow
    private LongSortedSet sectionIds;

    @Overwrite
    private LongSortedSet getChunkSections(int cx, int cz) {
        LongAVLTreeSet result = new LongAVLTreeSet();
        LongIterator it = this.sectionIds.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            IntSectionPos sp = IntSectionPos.getSectionPos(key);
            if (sp.x == cx && sp.z == cz) {
                result.add(key);
            }
        }
        return result;
    }

    @Overwrite
    private static long getChunkKeyFromSectionKey(long pos) {
        IntSectionPos sp = IntSectionPos.getSectionPos(pos);
        return ChunkPos.asLong(sp.x, sp.z);
    }
}
