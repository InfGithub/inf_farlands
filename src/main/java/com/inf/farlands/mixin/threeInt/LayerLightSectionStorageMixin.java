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

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.LongSet;

@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin {
    // protected int getStoredLevel(long levelPos) {
    // long i = SectionPos.blockToSection(levelPos);
    // DataLayer datalayer = this.getDataLayer(i, true);
    // return datalayer.get(
    // SectionPos.sectionRelative(BlockPos.getX(levelPos)),
    // SectionPos.sectionRelative(BlockPos.getY(levelPos)),
    // SectionPos.sectionRelative(BlockPos.getZ(levelPos))
    // );
    // }

    @Overwrite
    protected int getStoredLevel(long levelPos) {
        IntBlockPos pos = IntBlockPos.getBlockPos(levelPos);
        long secKey = SectionPos.blockToSection(levelPos);
        DataLayer dl = HashUtil.callGetDataLayer(this, secKey, true);
        if (dl == null) {
            return 15;
        }
        return dl.get(
                SectionPos.sectionRelative(pos.x),
                SectionPos.sectionRelative(pos.y),
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
        if (dl == null) {
            return;
        }
        dl.set(SectionPos.sectionRelative(pos.x), SectionPos.sectionRelative(pos.y), SectionPos.sectionRelative(pos.z),
                lightLevel);
    }

    @Overwrite
    protected boolean lightOnInSection(long sectionPos) {
        return HashUtil.callGetDataLayer(this, sectionPos, true) != null;
    }
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

    @Shadow
    private Long2ByteMap sectionStates;

    @Shadow
    protected abstract void putSectionState(long sectionPos, byte sectionState);

    /**
     * Replace SectionPos.offset in neighbor loop with local-coordinate arithmetic.
     * SectionPos.offset reads CHM for coordinates; CHM miss at extreme coords
     * produces garbage that cascades into infinite loops in createDataLayer.
     */
    @Overwrite
    protected void updateSectionStatus(long sectionPos, boolean isEmpty) {
        byte b0 = this.sectionStates.get(sectionPos);
        byte b1 = (byte)(!isEmpty ? b0 | 32 : b0 & -33);
        if (b0 != b1) {
            this.putSectionState(sectionPos, b1);
            int i = isEmpty ? -1 : 1;
            IntSectionPos sp = IntSectionPos.getSectionPos(sectionPos);
            int sx = sp.x, sy = sp.y, sz = sp.z;

            for (int j = -1; j <= 1; j++) {
                for (int k = -1; k <= 1; k++) {
                    for (int l = -1; l <= 1; l++) {
                        if (j != 0 || k != 0 || l != 0) {
                            long i1 = SectionPos.asLong(sx + j, sy + k, sz + l);
                            byte b2 = this.sectionStates.get(i1);
                            int nc = (b2 & 31) + i;
                            this.putSectionState(i1, (byte)(b2 & -32 | nc & 31));
                        }
                    }
                }
            }
        }
    }
}
