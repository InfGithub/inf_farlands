package com.inf.farlands.mixin.tool.clamp;

import com.inf.farlands.tool.clamp.ClampMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * 钳制模式真实碰撞：玩家 move 时在 collideBoundingBox 的碰撞盒列表加一个虚拟板
 * （顶面 = 玩家当前 section 底，覆盖移动范围）。玩家下落接触该面真实撞到——
 * onGround/deltaMovement 由物理引擎自然处理（等同站在方块上，可跳起、掉落被接住）。
 * 按住 shift（isShiftKeyDown）跳过——可穿过。
 */
@Mixin(Entity.class)
public class EntityCollisionMixin {

    /** getEntityCollisions 返回不可变列表，add 虚拟板会抛 UnsupportedOperationException——包一层可变 ArrayList。 */
    @SuppressWarnings("null")
    @Redirect(method = "collide", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private static List<VoxelShape> mutableEntityCollisions(Level level, Entity entity, AABB aabb) {
        return new ArrayList<>(level.getEntityCollisions(entity, aabb));
    }

    @Inject(method = "collideBoundingBox", at = @At("HEAD"))
    private static void addVirtualFloor(@Nullable Entity entity, Vec3 vec,
            AABB collisionBox, Level level, List<VoxelShape> potentialHits,
            CallbackInfoReturnable<Vec3> cir) {
        if (entity instanceof ClampMode clamp && clamp.farlandsIsClampEnabled() && !entity.isShiftKeyDown()) {
            double floorY = Math.floor(entity.getY() / 16.0) * 16.0;
            potentialHits.add(Shapes.create(new AABB(
                    collisionBox.minX - 1.0, floorY - 1.0, collisionBox.minZ - 1.0,
                    collisionBox.maxX + 1.0, floorY, collisionBox.maxZ + 1.0)));
        }
    }
}