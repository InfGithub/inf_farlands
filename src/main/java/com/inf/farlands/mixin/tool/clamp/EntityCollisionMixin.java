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
 * 钳制模式真实碰撞：玩家 move 时在 collideBoundingBox 的碰撞盒列表加一个虚拟板，
 * 其顶面为玩家当前 section 底，覆盖移动范围。玩家下落接触该面真实撞到——
 * onGround/deltaMovement 由物理引擎自然处理。
 * 按住 shift 跳过——可穿过。
 */
@Mixin(Entity.class)
public class EntityCollisionMixin {

    /** getEntityCollisions 返回不可变列表，add 虚拟板会抛 UnsupportedOperationException。 */
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
            // potentialHits 可能是不可变列表（如其他 mod 直调 collideBoundingBox 传 vanilla
            // getEntityCollisions 的产物）——不可变列表 add 抛 UnsupportedOperationException。
            // 防御：仅 ArrayList（mutableEntityCollisions @Redirect 的产物）add 虚拟板——
            // 非 ArrayList 的调用路径钳制模式退化为无板，不崩优先。
            if (potentialHits instanceof ArrayList) {
                double floorY = Math.floor(entity.getY() / 16.0) * 16.0;
                potentialHits.add(Shapes.create(new AABB(
                        collisionBox.minX - 1.0, floorY - 1.0, collisionBox.minZ - 1.0,
                        collisionBox.maxX + 1.0, floorY, collisionBox.maxZ + 1.0)));
            }
        }
    }
}