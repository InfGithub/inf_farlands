package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockLightSectionStorage.class)
public abstract class BlockLightSectionStorageMixin {

    // === method3: block-level key → IntBlockPos ===
    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp
            : new IntBlockPos(net.minecraft.core.BlockPos.getX(key), net.minecraft.core.BlockPos.getY(key), net.minecraft.core.BlockPos.getZ(key));
    }

    // === method1 @Overwrite: getLightValue ===
    @Overwrite
    protected int getLightValue(long levelPos) {
        IntBlockPos bp = getBlockPos(levelPos);
        long sec = SectionPos.blockToSection(levelPos);
        DataLayer dl = HashUtil.callGetDataLayer(this, sec, false);
        return dl == null ? 0 : dl.get(SectionPos.sectionRelative(bp.x), SectionPos.sectionRelative(bp.y), SectionPos.sectionRelative(bp.z));
    }
}
