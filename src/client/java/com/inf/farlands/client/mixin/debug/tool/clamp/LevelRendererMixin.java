package com.inf.farlands.client.mixin.debug.tool.clamp;

import com.inf.farlands.client.debug.tool.clamp.VirtualHit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 钳制模式虚拟面选择框：
 * 虚拟命中空气格时放行 isAir 检查，
 * 并用完整方块形状渲染框。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Redirect(method = "extractBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z"))
    private boolean virtualHitNotAir(BlockState state) {
        if (VirtualHit.isCurrent(Minecraft.getInstance().hitResult)) {
            return false;
        }
        return state.isAir();
    }

    @Redirect(method = "extractBlockOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape virtualHitShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (VirtualHit.isPos(pos)) {
            return Shapes.create(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        }
        return state.getShape(level, pos, ctx);
    }
}