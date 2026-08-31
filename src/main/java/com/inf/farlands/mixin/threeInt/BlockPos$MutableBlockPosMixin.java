package com.inf.farlands.mixin.threeInt;

import com.inf.farlands.util.IntBlockPos;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(BlockPos.MutableBlockPos.class)
public abstract class BlockPos$MutableBlockPosMixin {

    // 方法：
    // public BlockPos.MutableBlockPos set(long packedPos) {
    // return this.set(getX(packedPos), getY(packedPos), getZ(packedPos));
    // }

    @Overwrite
    public BlockPos.MutableBlockPos set(long packedPos) {
        IntBlockPos pos = IntBlockPos.getBlockPos(packedPos);
        BlockPos.MutableBlockPos self = (BlockPos.MutableBlockPos) (Object) this;
        return self.set(pos.x, pos.y, pos.z);
    }
}
