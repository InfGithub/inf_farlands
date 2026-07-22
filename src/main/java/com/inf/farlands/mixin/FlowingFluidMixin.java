package com.inf.farlands.mixin;

import com.inf.farlands.Config;
import java.util.Map;
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
    @Shadow protected abstract int getDropOff(LevelReader level);
    @Shadow protected abstract Map<Direction, FluidState> getSpread(Level level, BlockPos pos, BlockState state);
    @Invoker("sourceNeighborCount") protected abstract int invokeSourceNeighborCount(LevelReader level, BlockPos pos);
    @Invoker("spreadToSides")        protected abstract void invokeSpreadToSides(Level level, BlockPos pos, FluidState fluidState, BlockState blockState);
    @Invoker("isWaterHole")         protected abstract boolean invokeIsWaterHole(BlockGetter level, Fluid fluid, BlockPos pos, BlockState state, BlockPos spreadPos, BlockState spreadState);

    @Overwrite
    protected void spread(Level level, BlockPos pos, FluidState state) {
        if (state.isEmpty()) return;
        if (Config.disableFluidSpread) return;
        BlockPos blockpos = pos.below();
        if (!level.isLoaded(blockpos)) return;
        BlockState blockstate = level.getBlockState(pos);
        BlockState blockstate1 = level.getBlockState(blockpos);
        FluidState fluidstate = this.getNewLiquid(level, blockpos, blockstate1);
        if (this.canSpreadTo(level, pos, blockstate, Direction.DOWN, blockpos, blockstate1, level.getFluidState(blockpos), fluidstate.getType())) {
            this.spreadTo(level, blockpos, blockstate1, Direction.DOWN, fluidstate);
            if (this.invokeSourceNeighborCount(level, pos) >= 3) {
                this.invokeSpreadToSides(level, pos, state, blockstate);
            }
        } else if (state.isSource() || !this.invokeIsWaterHole(level, fluidstate.getType(), pos, blockstate, blockpos, blockstate1)) {
            this.invokeSpreadToSides(level, pos, state, blockstate);
        }
    }

    @Overwrite
    private void spreadToSides(Level level, BlockPos pos, FluidState fluidState, BlockState blockState) {
        int i = fluidState.getAmount() - this.getDropOff(level);
        if (i > 0) {
            Map<Direction, FluidState> map = this.getSpread(level, pos, blockState);
            for (Map.Entry<Direction, FluidState> entry : map.entrySet()) {
                Direction direction = entry.getKey();
                FluidState fluidstate = entry.getValue();
                BlockPos blockpos = pos.relative(direction);
                if (!level.isLoaded(blockpos)) continue;
                BlockState blockstate = level.getBlockState(blockpos);
                if (this.canSpreadTo(level, pos, blockState, direction, blockpos, blockstate, level.getFluidState(blockpos), fluidstate.getType())) {
                    this.spreadTo(level, blockpos, blockstate, direction, fluidstate);
                }
            }
        }
    }
}
