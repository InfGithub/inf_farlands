package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.BlockLightSectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockLightSectionStorage.class)
public abstract class BlockLightSectionStorageMixin {
    // @Override
    // protected int getLightValue(long levelPos) {
    // long i = SectionPos.blockToSection(levelPos);
    // DataLayer datalayer = this.getDataLayer(i, false);
    // return datalayer == null
    // ? 0
    // : datalayer.get(
    // SectionPos.sectionRelative(BlockPos.getX(levelPos)),
    // SectionPos.sectionRelative(BlockPos.getY(levelPos)),
    // SectionPos.sectionRelative(BlockPos.getZ(levelPos))
    // );
    // }
    @Overwrite
    protected int getLightValue(long levelPos) {
        IntBlockPos bp = IntBlockPos.getBlockPos(levelPos);
        long sec = SectionPos.blockToSection(levelPos);
        DataLayer dl = HashUtil.callGetDataLayer(this, sec, false);
        return dl == null ? 0
                : dl.get(SectionPos.sectionRelative(bp.x), SectionPos.sectionRelative(bp.y),
                        SectionPos.sectionRelative(bp.z));
    }
}
