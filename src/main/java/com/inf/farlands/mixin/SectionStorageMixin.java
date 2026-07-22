package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntSectionPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin {

    @Shadow protected LevelHeightAccessor levelHeightAccessor;

    // === method3: section-level key → IntSectionPos ===
    private static IntSectionPos getSectionPos(long key) {
        IntSectionPos sp = HashUtil.getSection(key);
        return sp != null ? sp
            : new IntSectionPos(SectionPos.x(key), SectionPos.y(key), SectionPos.z(key));
    }

    @Overwrite
    protected boolean outsideStoredRange(long sectionKey) {
        IntSectionPos sp = getSectionPos(sectionKey);
        int i = SectionPos.sectionToBlockCoord(sp.y);
        return this.levelHeightAccessor.isOutsideBuildHeight(i);
    }
}
