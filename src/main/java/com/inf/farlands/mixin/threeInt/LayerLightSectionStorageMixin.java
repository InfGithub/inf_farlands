package com.inf.farlands.mixin.threeInt;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import com.inf.farlands.IntSectionPos;

import it.unimi.dsi.fastutil.longs.LongSet;

@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin {
    //     protected int getStoredLevel(long levelPos) {
    //     long i = SectionPos.blockToSection(levelPos);
    //     DataLayer datalayer = this.getDataLayer(i, true);
    //     return datalayer.get(
    //         SectionPos.sectionRelative(BlockPos.getX(levelPos)),
    //         SectionPos.sectionRelative(BlockPos.getY(levelPos)),
    //         SectionPos.sectionRelative(BlockPos.getZ(levelPos))
    //     );
    // }

    @Overwrite
    protected int getStoredLevel(long levelPos) {
        IntBlockPos pos = IntBlockPos.getBlockPos(levelPos);
        long secKey = SectionPos.blockToSection(levelPos);
        DataLayer dl = HashUtil.callGetDataLayer(this, secKey, true);
        if (dl == null)
            return 15;
        return dl.get(SectionPos.sectionRelative(pos.x), SectionPos.sectionRelative(pos.y),
                SectionPos.sectionRelative(pos.z));
    }
    // protected void setStoredLevel(long levelPos, int lightLevel) {
    // long i = SectionPos.blockToSection(levelPos);
    // DataLayer datalayer;
    // if (this.changedSections.add(i)) {
    // datalayer = this.updatingSectionData.copyDataLayer(i);
    // } else {
    // datalayer = this.getDataLayer(i, true);
    // }

    // datalayer.set(
    // SectionPos.sectionRelative(BlockPos.getX(levelPos)),
    // SectionPos.sectionRelative(BlockPos.getY(levelPos)),
    // SectionPos.sectionRelative(BlockPos.getZ(levelPos)),
    // lightLevel
    // );
    // SectionPos.aroundAndAtBlockPos(levelPos,
    // this.sectionsAffectedByLightUpdates::add);
    // }
    @Overwrite
    protected void setStoredLevel(long levelPos, int lightLevel) {
        IntBlockPos pos = IntBlockPos.getBlockPos(levelPos);
        long secKey = SectionPos.blockToSection(levelPos);
        DataLayer dl = HashUtil.callGetDataLayerToWrite(this, secKey);
        if (dl == null)
            return;
        dl.set(SectionPos.sectionRelative(pos.x), SectionPos.sectionRelative(pos.y), SectionPos.sectionRelative(pos.z),
                lightLevel);
    }

    // @Overwrite
    // protected boolean lightOnInSection(long sectionPos) {
    // return HashUtil.callGetDataLayer(this, sectionPos, true) != null;
    // }
    // 这个方法可能不需要 Mixin

    @Shadow
    private LongSet sectionsAffectedByLightUpdates;

    @Overwrite
    protected void markSectionAndNeighborsAsAffected(long sectionPos) {
        IntSectionPos sp = IntSectionPos.getSectionPos(sectionPos);
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
