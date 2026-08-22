package com.inf.farlands.mixin.axisY;

import com.inf.farlands.WindowedChunk;
import com.inf.farlands.WorldBounds;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LevelHeightAccessor.class)
public interface LevelHeightAccessorMixin {

    @Overwrite
    default int getMinSection() {
        if ((Object) this instanceof ImposterProtoChunk ipc) {
            return ((WindowedChunk) ipc.getWrapped()).getWindowMinY();
        }
        if ((Object) this instanceof LevelChunk lc) {
            return ((WindowedChunk) lc).getWindowMinY();
        }
        if ((Object) this instanceof ChunkAccess) {
            LevelHeightAccessor self = (LevelHeightAccessor) (Object) this;
            return SectionPos.blockToSectionCoord(self.getMinBuildHeight());
        }
        return -4;
    }

    @Overwrite
    default int getMaxSection() {
        if ((Object) this instanceof ChunkAccess ca) {
            return ca.getMinSection() + ca.getSectionsCount();
        }
        return 20;
    }

    @Overwrite
    default int getSectionsCount() {
        if ((Object) this instanceof ChunkAccess ca) {
            LevelChunkSection[] w = ca.getSections();
            if (w.length > 0)
                return w.length;
        }
        return 24;
    }

    @Overwrite
    default int getSectionYFromSectionIndex(int sectionIndex) {
        if ((Object) this instanceof ImposterProtoChunk ipc) {
            return ((WindowedChunk) ipc.getWrapped()).getWindowMinY() + sectionIndex;
        }
        if ((Object) this instanceof LevelChunk ca) {
            return ((WindowedChunk) ca).getWindowMinY() + sectionIndex;
        }
        LevelHeightAccessor self = (LevelHeightAccessor) (Object) this;
        return sectionIndex + self.getMinSection();
    }

    @Overwrite
    default int getSectionIndexFromSectionY(int sectionY) {
        if ((Object) this instanceof ImposterProtoChunk ipc) {
            return sectionY - ((WindowedChunk) ipc.getWrapped()).getWindowMinY();
        }
        if ((Object) this instanceof LevelChunk ca) {
            return sectionY - ((WindowedChunk) ca).getWindowMinY();
        }
        LevelHeightAccessor self = (LevelHeightAccessor) (Object) this;
        return sectionY - self.getMinSection();
    }

    @Overwrite
    default boolean isOutsideBuildHeight(int y) {
        if ((Object) this instanceof ChunkAccess) {
            LevelHeightAccessor self = (LevelHeightAccessor) (Object) this;
            return y < self.getMinBuildHeight() || y >= self.getMaxBuildHeight();
        }
        return !WorldBounds.inBuildHeight(y);
    }
}
