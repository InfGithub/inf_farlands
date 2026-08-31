package com.inf.farlands.mixin.serialize;

import com.inf.farlands.window.WindowedChunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * per-section 脏标记的标脏点，fsa 序列化引擎。
 *
 * 玩家 setBlock/破坏最终都走 LevelChunk.setBlockState，主线程；修改实际发生
 * 时标脏：返回值非 null；vanilla L250 blockstate == state 分支返回 null = 未变。
 * 双端共享类：客户端标脏无害，SectionLifecycle 纯服务端，客户端从不查询。
 */
@Mixin(LevelChunk.class)
public abstract class LevelChunkSetBlockStateMixin {

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void onSetBlockState(BlockPos pos, BlockState state, boolean isMoving,
            CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null) {
            ((WindowedChunk) this).markSectionDirty(pos.getY() >> 4);
        }
    }
}
