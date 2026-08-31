package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public class FlowingFluidMixin {

    // 方法：
    // protected void spread(Level level, BlockPos pos, FluidState state) {
    //     if (!state.isEmpty()) {
    //         BlockState blockstate = level.getBlockState(pos);
    //         BlockPos blockpos = pos.below();
    //         BlockState blockstate1 = level.getBlockState(blockpos);
    //         FluidState fluidstate = this.getNewLiquid(level, blockpos, blockstate1);
    //         if (this.canSpreadTo(
    //             level, pos, blockstate, Direction.DOWN, blockpos, blockstate1, level.getFluidState(blockpos), fluidstate.getType()
    //         )) {
    //             this.spreadTo(level, blockpos, blockstate1, Direction.DOWN, fluidstate);
    //             if (this.sourceNeighborCount(level, pos) >= 3) {
    //                 this.spreadToSides(level, pos, state, blockstate);
    //             }
    //         } else if (state.isSource() || !this.isWaterHole(level, fluidstate.getType(), pos, blockstate, blockpos, blockstate1)) {
    //             this.spreadToSides(level, pos, state, blockstate);
    //         }
    //     }
    // }

    @Inject(method = "spread", at = @At("HEAD"), cancellable = true)
    private void onSpread(Level level, BlockPos pos, FluidState state, CallbackInfo ci) {

        if (Config.disableFluidSpread) {
            ci.cancel();
        }
    }
}