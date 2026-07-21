package com.inf.farlands.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public class PlayerMixin {

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(DDD)D",
            ordinal = 0
        )
    )
    private double skipXClamp(double value, double min, double max) {
        return value;
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/Mth;clamp(DDD)D",
            ordinal = 1
        )
    )
    private double skipZClamp(double value, double min, double max) {
        return value;
    }

    @Redirect(
        method = "attack",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"
        )
    )
    private void redirectAttackSound(
        Level level, Player player, double x, double y, double z,
        SoundEvent sound, SoundSource source, float volume, float pitch
    ) {
        level.playSound(player, (Entity) (Object) this, sound, source, volume, pitch);
    }
}
