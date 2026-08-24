package com.inf.farlands.mixin.tool.clamp;

import com.inf.farlands.tool.clamp.FarLandsVirtualHit;
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
 * 钳制模式虚拟面选择框：虚拟命中（空气格）时放行 isAir 检查（选择框出现），
 * 并用完整方块形状渲染框（空气形状为空，需 AABB.ofFull）。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;isAir()Z", ordinal = 0))
    private boolean virtualHitNotAir(BlockState state) {
        if (FarLandsVirtualHit.isCurrent(Minecraft.getInstance().hitResult)) {
            return false;
        }
        return state.isAir();
    }

    @Redirect(method = "renderHitOutline", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape virtualHitShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (FarLandsVirtualHit.isPos(pos)) {
            // renderShape 用局部坐标 + (pos-cam) 偏移——必须返回局部单位立方体，
            // 不是绝对坐标 AABB(pos)（会 double 偏移到 2*pos-cam，框画在屏幕外）。
            return Shapes.create(new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0));
        }
        return state.getShape(level, pos, ctx);
    }
}