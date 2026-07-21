package com.inf.farlands.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Shadow protected abstract FluidState getNewLiquid(Level level, BlockPos pos, BlockState state);
    @Shadow protected abstract boolean canSpreadTo(BlockGetter level, BlockPos pos, BlockState state, Direction direction, BlockPos spreadPos, BlockState spreadState, FluidState fluidState, Fluid fluid);
    @Shadow protected abstract void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState);
    @Invoker("sourceNeighborCount") protected abstract int invokeSourceNeighborCount(LevelReader level, BlockPos pos);
    @Invoker("spreadToSides")        protected abstract void invokeSpreadToSides(Level level, BlockPos pos, FluidState fluidState, BlockState blockState);
    @Invoker("isWaterHole")         protected abstract boolean invokeIsWaterHole(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState);

    /**
     * Skip fluid spread when the block below is in an unloaded chunk.
     * Prevents synchronous chunk loading on the server thread
     * at extreme coordinates where neighbor chunks are often missing.
     */
    @Overwrite
    protected void spread(Level level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) return;
        BlockPos blockpos = pos.below();
        if (!level.isLoaded(blockpos)) return;

        BlockState blockstate = level.getBlockState(pos);
        BlockState blockstate1 = level.getBlockState(blockpos);
        FluidState fluidstate = this.getNewLiquid(level, blockpos, blockstate1);
        if (this.canSpreadTo(
            level, pos, blockstate, Direction.DOWN, blockpos, blockstate1, level.getFluidState(blockpos), fluidstate.getType()
        )) {
            this.spreadTo(level, blockpos, blockstate1, Direction.DOWN, fluidstate);
            if (this.invokeSourceNeighborCount(level, pos) >= 3) {
                this.invokeSpreadToSides(level, pos, state, blockstate);
            }
        } else if (state.isSource() || !this.invokeIsWaterHole(level, fluidstate.getType(), pos, blockstate, blockpos, blockstate1)) {
            this.invokeSpreadToSides(level, pos, state, blockstate);
        }
    }
}
