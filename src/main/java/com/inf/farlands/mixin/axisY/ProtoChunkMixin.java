package com.inf.farlands.mixin.axisY;

import com.inf.farlands.WorldBounds;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Field;

@Mixin(ProtoChunk.class)
public abstract class ProtoChunkMixin {

    private static final Field F_LHA;
    static {
        try {
            F_LHA = ChunkAccess.class.getDeclaredField("levelHeightAccessor");
            F_LHA.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Overwrite
    public BlockState getBlockState(BlockPos pos) {
        int y = pos.getY();
        ChunkAccess ca = (ChunkAccess) (Object) this;
        if (!WorldBounds.inBuildHeight(y)) {
            return Blocks.VOID_AIR.defaultBlockState();
        }
        LevelChunkSection s = ca.getSection(getLevelHeightAccessor(ca).getSectionIndex(y));
        return (s == null || s.hasOnlyAir())
                ? Blocks.AIR.defaultBlockState()
                : s.getBlockState(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    @Overwrite
    public FluidState getFluidState(BlockPos pos) {
        int y = pos.getY();
        ChunkAccess ca = (ChunkAccess) (Object) this;
        if (!WorldBounds.inBuildHeight(y)) {
            return Fluids.EMPTY.defaultFluidState();
        }
        LevelChunkSection s = ca.getSection(getLevelHeightAccessor(ca).getSectionIndex(y));
        return (s == null || s.hasOnlyAir())
                ? Fluids.EMPTY.defaultFluidState()
                : s.getFluidState(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    private static LevelHeightAccessor getLevelHeightAccessor(ChunkAccess ca) {
        try {
            return (LevelHeightAccessor) F_LHA.get(ca);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
