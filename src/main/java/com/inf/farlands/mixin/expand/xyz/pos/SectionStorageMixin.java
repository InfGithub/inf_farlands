package com.inf.farlands.mixin.expand.xyz.pos;

import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.inf.farlands.util.pos.IntSectionPos;

@Mixin(SectionStorage.class)
public class SectionStorageMixin {

    // 替换 setDirty 中的 SectionPos.x
    @Redirect(method = "setDirty", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;x(J)I"))
    private int redirectX(long sectionPos) {
        return IntSectionPos.getSectionPos(sectionPos).x;
    }

    @Redirect(method = "outsideStoredRange", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;y(J)I"))
    private int redirectY(long sectionPos) {
        return IntSectionPos.getSectionPos(sectionPos).y;
    }

    @Redirect(method = "setDirty", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;z(J)I"))
    private int redirectZ(long sectionPos) {
        return IntSectionPos.getSectionPos(sectionPos).z;
    }
}