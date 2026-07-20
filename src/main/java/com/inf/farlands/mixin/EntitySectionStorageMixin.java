package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntSectionPos;
import it.unimi.dsi.fastutil.longs.*;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(EntitySectionStorage.class)
public abstract class EntitySectionStorageMixin<T extends EntityAccess> {

    @Shadow
    private LongSortedSet sectionIds;
    @Shadow
    private Long2ObjectMap<EntitySection<T>> sections;

    /**
     * Instead of subSet (which relies on packed-long ordering),
     * iterate all sectionIds and filter by decoded coordinates.
     */
    @Overwrite
    public void forEachAccessibleNonEmptySection(AABB bounds, AbortableIterationConsumer<EntitySection<T>> consumer) {
        int minSecX = SectionPos.posToSectionCoord(bounds.minX - 2.0);
        int maxSecX = SectionPos.posToSectionCoord(bounds.maxX + 2.0);
        int minSecZ = SectionPos.posToSectionCoord(bounds.minZ - 2.0);
        int maxSecZ = SectionPos.posToSectionCoord(bounds.maxZ + 2.0);
        int minY = SectionPos.posToSectionCoord(bounds.minY - 4.0);
        int maxY = SectionPos.posToSectionCoord(bounds.maxY + 0.0);

        LongIterator it = this.sectionIds.iterator();
        while (it.hasNext()) {
            long key = it.nextLong();
            IntSectionPos sp = HashUtil.sectionLookup.get(key);
            int sx, sy, sz;
            if (sp != null) {
                sx = sp.x; sy = sp.y; sz = sp.z;
            } else {
                sx = SectionPos.x(key);
                sy = SectionPos.y(key);
                sz = SectionPos.z(key);
            }
            if (sx >= minSecX && sx <= maxSecX && sy >= minY && sy <= maxY && sz >= minSecZ && sz <= maxSecZ) {
                EntitySection<T> section = this.sections.get(key);
                if (section != null && !section.isEmpty() && section.getStatus().isAccessible()) {
                    if (consumer.accept(section).shouldAbort()) return;
                }
            }
        }
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
            IntSectionPos sp = HashUtil.sectionLookup.get(key);
            if (sp != null) {
                if (sp.x == cx && sp.z == cz) result.add(key);
            } else {
                if (SectionPos.x(key) == cx && SectionPos.z(key) == cz) result.add(key);
            }
        }
        return result;
    }
}
