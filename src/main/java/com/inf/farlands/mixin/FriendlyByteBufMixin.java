package com.inf.farlands.mixin;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
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

    @Overwrite
    public FriendlyByteBuf writeSectionPos(SectionPos pos) {
        FriendlyByteBuf self = (FriendlyByteBuf)(Object)this;
        self.writeInt(pos.x());
        self.writeInt(pos.y());
        self.writeInt(pos.z());
        return self;
    }

    @Overwrite
    public SectionPos readSectionPos() {
        FriendlyByteBuf self = (FriendlyByteBuf)(Object)this;
        return SectionPos.of(self.readInt(), self.readInt(), self.readInt());
    }
}
