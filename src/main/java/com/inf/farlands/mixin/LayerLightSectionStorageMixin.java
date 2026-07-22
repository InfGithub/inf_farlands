package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin {

    @Shadow private LongSet sectionsAffectedByLightUpdates;

    // === method3: block-level key → IntBlockPos ===
    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp
            : new IntBlockPos(net.minecraft.core.BlockPos.getX(key), net.minecraft.core.BlockPos.getY(key), net.minecraft.core.BlockPos.getZ(key));
    }

    // === method3: section-level key → IntSectionPos ===
    private static IntSectionPos getSectionPos(long key) {
        IntSectionPos sp = HashUtil.getSection(key);
        return sp != null ? sp
            : new IntSectionPos(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
    }

    // === method1 @Overwrite: getStoredLevel ===
    @Overwrite
    protected int getStoredLevel(long levelPos) {
        IntBlockPos pos = getBlockPos(levelPos);
        long secKey = SectionPos.asLong(
            SectionPos.blockToSectionCoord(pos.x),
            SectionPos.blockToSectionCoord(pos.y),
            SectionPos.blockToSectionCoord(pos.z));
        DataLayer dl = HashUtil.callGetDataLayer(this, secKey, true);
        if (dl == null) return 15;
        return dl.get(SectionPos.sectionRelative(pos.x), SectionPos.sectionRelative(pos.y), SectionPos.sectionRelative(pos.z));
    }

    // === method1 @Overwrite: setStoredLevel ===
    @Overwrite
    protected void setStoredLevel(long levelPos, int lightLevel) {
        IntBlockPos pos = getBlockPos(levelPos);
        long secKey = SectionPos.asLong(
            SectionPos.blockToSectionCoord(pos.x),
            SectionPos.blockToSectionCoord(pos.y),
            SectionPos.blockToSectionCoord(pos.z));
        DataLayer dl = HashUtil.callGetDataLayerToWrite(this, secKey);
        if (dl == null) return;
        dl.set(SectionPos.sectionRelative(pos.x), SectionPos.sectionRelative(pos.y), SectionPos.sectionRelative(pos.z), lightLevel);
    }

    @Overwrite
    protected boolean lightOnInSection(long sectionPos) {
        return HashUtil.callGetDataLayer(this, sectionPos, true) != null;
    }

    // === method1 @Overwrite: markSectionAndNeighborsAsAffected ===
    @Overwrite
    protected void markSectionAndNeighborsAsAffected(long sectionPos) {
        IntSectionPos sp = getSectionPos(sectionPos);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    this.sectionsAffectedByLightUpdates.add(
                        SectionPos.asLong(sp.x + dx, sp.y + dy, sp.z + dz));
                }
            }
        }
    }
}
