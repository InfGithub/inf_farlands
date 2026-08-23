package com.inf.farlands.mixin.axisYOverflowFix;

import com.inf.farlands.WindowedChunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.multiplayer.ClientLevel;

@Mixin(ClientLevel.class)
public class ClientLevelSetBlockMixin {

    // §7.5：窗口外 setBlock 拒绝（cir.setReturnValue(false)）。窗口外数据写入后
    // 会被 §7.3 丢弃（写入-丢弃循环风险），源头拒绝更干净。正常交互位置必然在
    // 窗口内（交互距离 ≤ 5 block << 窗口 272 block），拒绝只触发于物理连锁传播
    // 出窗口——服务端权威最终修正（setServerVerifiedBlockState 绕过本注入）。
    // expandWindowTo 已删除：渲染取数与窗口解耦，相机每帧拉回窗口，
    // 滑窗无意义。
    @SuppressWarnings({ "resource", "null" })
    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void rejectOutsideWindow(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir) {
        ClientLevel self = (ClientLevel) (Object) this;
        LevelChunk chunk = self.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
        if (chunk == null) {
            return;
        }
        int sectionY = pos.getY() >> 4;
        WindowedChunk wc = (WindowedChunk) chunk;
        if (sectionY < wc.getWindowMinY() || sectionY > wc.getWindowMaxY()) {
            cir.setReturnValue(false);
        }
    }
}
