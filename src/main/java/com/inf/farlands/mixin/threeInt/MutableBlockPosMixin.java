package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.HashUtil;
import com.inf.farlands.IntBlockPos;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockPos.MutableBlockPos.class)
public abstract class MutableBlockPosMixin {

    private static IntBlockPos getBlockPos(long key) {
        IntBlockPos bp = HashUtil.getBlock(key);
        return bp != null ? bp
                : new IntBlockPos(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key));
    }

    // 方法：
    // public BlockPos.MutableBlockPos set(long packedPos) {
    // return this.set(getX(packedPos), getY(packedPos), getZ(packedPos));
    // }

    @Overwrite
    public BlockPos.MutableBlockPos set(long packedPos) {
        IntBlockPos pos = getBlockPos(packedPos);
        BlockPos.MutableBlockPos self = (BlockPos.MutableBlockPos) (Object) this;
        return self.set(pos.x, pos.y, pos.z);
    }
}
