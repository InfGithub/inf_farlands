package com.inf.farlands.mixin.expand.xyz.pos;

import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.inf.farlands.util.pos.IntSectionPos;

@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerMixin {

    @Redirect(method = "lambda$dumpSections$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;x(J)I"))
    private int redirectSectionX(long sectionKey) {
        return IntSectionPos.getSectionPos(sectionKey).x;
    }

    @Redirect(method = "lambda$dumpSections$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;y(J)I"))
    private int redirectSectionY(long sectionKey) {
        return IntSectionPos.getSectionPos(sectionKey).y;
    }

    @Redirect(method = "lambda$dumpSections$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/core/SectionPos;z(J)I"))
    private int redirectSectionZ(long sectionKey) {
        return IntSectionPos.getSectionPos(sectionKey).z;
    }
}