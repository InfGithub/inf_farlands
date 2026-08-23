package com.inf.farlands.mixin.axisYOverflowFix;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 BlockEntityInfo 的 Y 坐标溢出 vanilla short 编码的问题。
 * <p>
 * vanilla 以 {@code writeShort} 编码方块实体世界 Y——|Y| > 32767 会被截断
 * （如 2,147,483,631 → -17），客户端在垃圾位置创建方块实体失败，永不渲染。
 * 线上格式改为 4 字节 int（两端都是本 mod）。原 {@code y} 字段保留截断值
 * （不使用）；完整值存于 {@code yCorrect}，由重建位置的 chunk 包 mixin 读取。
 */
@Mixin(targets = "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo")
public abstract class ClientboundLevelChunkPacketData$BlockEntityInfoMixin {

    @Unique
    private int yCorrect;

    @Redirect(method = "<init>(Lnet/minecraft/network/RegistryFriendlyByteBuf;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;readShort()S"))
    private short redirectReadShort(RegistryFriendlyByteBuf buffer) {
        this.yCorrect = buffer.readInt();
        return (short) this.yCorrect;
    }

    @Redirect(method = "write", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/RegistryFriendlyByteBuf;writeShort(I)Lnet/minecraft/network/FriendlyByteBuf;"))
    private FriendlyByteBuf redirectWriteShort(RegistryFriendlyByteBuf buffer, int value) {
        return buffer.writeInt(value);
    }
}
