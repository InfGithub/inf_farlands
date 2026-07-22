package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntSectionPos;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Shadow private LongSortedSet sectionIds;
    @Shadow private Long2ObjectMap<EntitySection<T>> sections;

    @Unique private final Int2ObjectMap<LongSet> sectionsByX = new Int2ObjectOpenHashMap<>();
    @Unique private final Long2IntMap sectionXByKey = new Long2IntOpenHashMap();

    // === method3: section-level key → IntSectionPos ===
    private static IntSectionPos getSectionPos(long key) {
        IntSectionPos sp = HashUtil.getSection(key);
        return sp != null ? sp
            : new IntSectionPos(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
    }

    /** Register section in X-index when created. */
    @Inject(method = "createSection", at = @At("TAIL"))
    private void onCreateSection(long sectionPos, CallbackInfoReturnable<EntitySection<T>> cir) {
        int sx = getX(sectionPos);
        sectionXByKey.put(sectionPos, sx);
        sectionsByX.computeIfAbsent(sx, k -> new LongOpenHashSet()).add(sectionPos);
    }

    /** Remove section from X-index. */
    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(long sectionId, CallbackInfo ci) {
        int sx = sectionXByKey.remove(sectionId);
        int defaultX = sectionXByKey.defaultReturnValue();
        if (sx == defaultX) return;
        LongSet set = sectionsByX.get(sx);
        if (set != null) {
            set.remove(sectionId);
            if (set.isEmpty()) sectionsByX.remove(sx);
        }
    }

    /** Iterate only the X-columns that intersect the AABB, via X-index. */
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
            if (keys == null) continue;
            for (long key : keys) {
                int sy, sz;
                IntSectionPos sp = getSectionPos(key);
                sy = sp.y; sz = sp.z;
                if (sy >= minSecY && sy <= maxSecY && sz >= minSecZ && sz <= maxSecZ) {
                    EntitySection<T> section = this.sections.get(key);
                    if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                        if (consumer.accept(section).shouldAbort()) return;
                    }
                }
            }
        }
    }

    /** X-coordinate of a section key, avoiding the side-channel on stale entries. */
    @Unique
    private int getX(long key) {
        return getSectionPos(key).x;
    }

    /**
     * Instead of SectionPos.asLong(x,0,z) subSet, iterate and filter by chunk.
     */
    @Overwrite
    private LongSortedSet getChunkSections(int cx, int cz) {
        LongAVLTreeSet result = new LongAVLTreeSet();
        LongIterator it = this.sectionIds.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            IntSectionPos sp = getSectionPos(key);
            if (sp.x == cx && sp.z == cz) result.add(key);
        }
        return result;
    }

    /** Replace SectionPos.x/z(long) with CHM lookup. */
    @Overwrite
    private static long getChunkKeyFromSectionKey(long pos) {
        IntSectionPos sp = getSectionPos(pos);
        return ChunkPos.asLong(sp.x, sp.z);
    }
}
