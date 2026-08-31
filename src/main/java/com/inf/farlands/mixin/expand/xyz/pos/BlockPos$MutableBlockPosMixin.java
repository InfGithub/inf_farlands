package com.inf.farlands.mixin.expand.xyz.pos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import com.inf.farlands.util.pos.IntBlockPos;

import net.minecraft.core.BlockPos;

@Mixin(BlockPos.MutableBlockPos.class)
public class BlockPos$MutableBlockPosMixin {
    @Overwrite
    public BlockPos.MutableBlockPos set(long packedPos) {
        IntBlockPos pos = IntBlockPos.getBlockPos(packedPos);
        BlockPos.MutableBlockPos self = (BlockPos.MutableBlockPos) (Object) this;
        return self.set(pos.x, pos.y, pos.z);
    }
}
