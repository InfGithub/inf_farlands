package com.inf.farlands.mixin;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockPos.MutableBlockPos.class)
public abstract class MutableBlockPosMixin {

    @Overwrite
    public BlockPos.MutableBlockPos set(long packedPos) {
        IntBlockPos pos = HashUtil.blockLookup.get(packedPos);
        BlockPos.MutableBlockPos self = (BlockPos.MutableBlockPos)(Object) this;
        if (pos != null) return self.set(pos.x, pos.y, pos.z);
        return self.set(BlockPos.getX(packedPos), BlockPos.getY(packedPos), BlockPos.getZ(packedPos));
    }
}
