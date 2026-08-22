package com.inf.farlands.mixin.axisYOverflowFix;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Entity.class)
public class EntityMixin {

    @Overwrite
    public void checkBelowWorld() {
    }
}
