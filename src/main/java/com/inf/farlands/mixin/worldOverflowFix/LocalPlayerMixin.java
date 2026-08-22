package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @SuppressWarnings("null")
    @Overwrite
    public void resetPos() {
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.setPose(Pose.STANDING);
        if (self.level() != null) {
            int minY = Math.max(self.level().getMinBuildHeight(), -100000);
            int maxY = Math.min(self.level().getMaxBuildHeight(), 100000);
            for (double d0 = self.getY(); d0 > (double) minY && d0 < (double) maxY; d0++) {
                self.setPos(self.getX(), d0, self.getZ());
                if (self.level().noCollision(self)) break;
            }
            self.setDeltaMovement(Vec3.ZERO);
            self.setXRot(0.0F);
        }
        self.setHealth(self.getMaxHealth());
        self.deathTime = 0;
    }
}
