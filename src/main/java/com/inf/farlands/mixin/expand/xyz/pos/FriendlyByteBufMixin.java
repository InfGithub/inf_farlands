package com.inf.farlands.mixin.expand.xyz.pos;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(FriendlyByteBuf.class)
public class FriendlyByteBufMixin {

    @Overwrite
    public static void writeBlockPos(ByteBuf buffer, BlockPos pos) {
        buffer.writeInt(pos.getX());
        buffer.writeInt(pos.getY());
        buffer.writeInt(pos.getZ());
    }

    @Overwrite
    public static BlockPos readBlockPos(ByteBuf buffer) {
        return new BlockPos(buffer.readInt(), buffer.readInt(), buffer.readInt());
    }
}