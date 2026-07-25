package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.IntSectionPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin {

    @Shadow
    protected LevelHeightAccessor levelHeightAccessor;

    @Overwrite
    protected boolean outsideStoredRange(long sectionKey) {
        IntSectionPos sp = IntSectionPos.getSectionPos(sectionKey);
        int i = SectionPos.sectionToBlockCoord(sp.y);
        return this.levelHeightAccessor.isOutsideBuildHeight(i);
    }
}
