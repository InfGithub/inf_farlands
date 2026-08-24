package com.inf.farlands.mixin.tool.clamp;

import com.inf.farlands.tool.clamp.ClampMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 钳制模式虚拟面放置：虚拟命中（面下空气格）时 replaceClicked=false——
 * BlockPlaceContext.getClickedPos() 改为 relativePos（面下方块的 UP 邻居 = 面上方），
 * 而非命中位置本身（默认 replaceClicked=true 会放到面下）。
 * 识别不依赖客户端静态字段（服务端无 pick）：钳制开启 + 命中位置是虚拟面下空气格
 * （y == floor(玩家脚y/16)*16 - 1）——与服务端接收到的虚拟命中（位置特征一致）。
 */
@Mixin(BlockPlaceContext.class)
public class BlockPlaceContextMixin {

    @Shadow
    protected boolean replaceClicked;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/BlockHitResult;)V", at = @At("TAIL"))
    private void farlandsVirtualNotReplaceClicked(Level level, Player player, InteractionHand hand,
            ItemStack stack, BlockHitResult hit, CallbackInfo ci) {
        if (isVirtualHit(level, player, hit.getBlockPos())) {
            this.replaceClicked = false;
        }
    }

    private static boolean isVirtualHit(Level level, Player player, BlockPos pos) {
        if (player instanceof ClampMode clamp && clamp.farlandsIsClampEnabled()) {
            double floorY = Math.floor(player.getY() / 16.0) * 16.0;
            return pos.getY() == (int) floorY - 1 && level.getBlockState(pos).isAir();
        }
        return false;
    }
}