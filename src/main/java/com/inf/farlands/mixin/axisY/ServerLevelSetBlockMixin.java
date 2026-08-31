package com.inf.farlands.mixin.axisY;

import com.inf.farlands.Config;
import com.inf.farlands.window.EntitySectionWindow;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 服务端 setBlock 窗口约束，镜像客户端 ClientLevelSetBlockMixin，防命令漏洞。
 *
 * 背景：客户端已拒绝窗口外 setBlock；服务端因"交互距离 << 窗口"隐含保证而未实现
 * ——但命令路径 /setblock /fill 不受距离限制，可创建/修改窗口外 section，
 * 因为 getSection 懒创建；这与 fsa 清理快照存在数据竞争，该快照在滑出
 * section 时异步 encode。
 *
 * 约束：setBlock 只允许在"玩家窗口并集 ± 清理余量(8)"内——margin 取清理余量使
 * setBlock 允许范围与清理判定范围无重叠 → 清理快照的 section 无并发写 → fsa
 * 两段式提交的 encode 阶段无竞态。
 *
 * 影响面：玩家交互在窗口内，不受影响；命令在窗口并集+8 外失败，语义变化，接受；
 * 窗口边缘连锁如活塞/下落/流体，距离 << 窗口，实际不触发；流体已 disableFluidSpread。
 *
 * 双端防御：ClientLevel override setBlock 内部调 super.setBlock，即 Level.setBlock
 * ——isClientSide 检查跳过客户端，客户端另有 ClientLevelSetBlockMixin，约束更严。
 */
@Mixin(Level.class)
public abstract class ServerLevelSetBlockMixin {

    @SuppressWarnings("resource")
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void rejectWindowOutside(BlockPos pos, BlockState state, int flags, int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        Level self = (Level) (Object) this;
        if (self.isClientSide) {
            return;
        }
        int sectionY = pos.getY() >> 4;
        if (EntitySectionWindow.isOutsideAllWindows(sectionY, Config.fsaCleanupMargin)) {
            cir.setReturnValue(false);
        }
    }
}
