package com.inf.farlands.mixin.axisY;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(FriendlyByteBuf.class)
public class FriendlyByteBufMixin {

    @Overwrite
    public FriendlyByteBuf writeChunkPos(ChunkPos pos) {
        FriendlyByteBuf self = (FriendlyByteBuf) (Object) this;
        self.writeInt(pos.x);
        self.writeInt(pos.z);
        return self;
    }

    @Overwrite
    public ChunkPos readChunkPos() {
        FriendlyByteBuf self = (FriendlyByteBuf) (Object) this;
        return new ChunkPos(self.readInt(), self.readInt());
    }
}
