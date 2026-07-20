package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LayerLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LayerLightSectionStorage.class)
public abstract class LayerLightSectionStorageMixin {

    @Overwrite
    protected int getStoredLevel(long levelPos) {
        IntBlockPos pos = HashUtil.blockLookup.get(levelPos);
        if (pos != null) {
            long secKey = SectionPos.asLong(
                SectionPos.blockToSectionCoord(pos.x),
                SectionPos.blockToSectionCoord(pos.y),
                SectionPos.blockToSectionCoord(pos.z)
            );
            DataLayer dl = HashUtil.callGetDataLayer(this, secKey, true);
            if (dl == null) return 15;
            return dl.get(
                SectionPos.sectionRelative(pos.x),
                SectionPos.sectionRelative(pos.y),
                SectionPos.sectionRelative(pos.z)
            );
        }
        long i = SectionPos.blockToSection(levelPos);
        DataLayer dl = HashUtil.callGetDataLayer(this, i, true);
        if (dl == null) return 15;
        return dl.get(
            SectionPos.sectionRelative(net.minecraft.core.BlockPos.getX(levelPos)),
            SectionPos.sectionRelative(net.minecraft.core.BlockPos.getY(levelPos)),
            SectionPos.sectionRelative(net.minecraft.core.BlockPos.getZ(levelPos))
        );
    }

    @Overwrite
    protected void setStoredLevel(long levelPos, int lightLevel) {
        IntBlockPos pos = HashUtil.blockLookup.get(levelPos);
        if (pos != null) {
            long secKey = SectionPos.asLong(
                SectionPos.blockToSectionCoord(pos.x),
                SectionPos.blockToSectionCoord(pos.y),
                SectionPos.blockToSectionCoord(pos.z)
            );
            DataLayer dl = HashUtil.callGetDataLayerToWrite(this, secKey);
            if (dl == null) return;
            dl.set(
                SectionPos.sectionRelative(pos.x),
                SectionPos.sectionRelative(pos.y),
                SectionPos.sectionRelative(pos.z),
                lightLevel
            );
            return;
        }
        long i = SectionPos.blockToSection(levelPos);
        DataLayer dl = HashUtil.callGetDataLayerToWrite(this, i);
        if (dl == null) return;
        dl.set(
            SectionPos.sectionRelative(net.minecraft.core.BlockPos.getX(levelPos)),
            SectionPos.sectionRelative(net.minecraft.core.BlockPos.getY(levelPos)),
            SectionPos.sectionRelative(net.minecraft.core.BlockPos.getZ(levelPos)),
            lightLevel
        );
    }
}
