package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockPos.MutableBlockPos.class)
public abstract class MutableBlockPosMixin {

    // === method3: block-level key → IntBlockPos ===
    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp
            : new IntBlockPos(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
    }

    @Overwrite
    public BlockPos.MutableBlockPos set(long packedPos) {
        IntBlockPos pos = getBlockPos(packedPos);
        BlockPos.MutableBlockPos self = (BlockPos.MutableBlockPos)(Object) this;
        return self.set(pos.x, pos.y, pos.z);
    }
}
