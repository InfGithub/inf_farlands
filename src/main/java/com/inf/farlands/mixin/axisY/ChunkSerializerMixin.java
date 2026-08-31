package com.inf.farlands.mixin.axisY;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.lighting.LayerLightEventListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * fsa 退役：ChunkSerializer.write 的 sections 空转——chunk NBT 不再含方块/光照数据，
 * 由 fsa 全权接管。read 侧无 sections → vanilla 循环 0 次，天然无读。
 *
 * write 的 sections 循环条件 (flag1 || datalayer != null || datalayer1 != null)：
 *   1. redirectGetSections → 空数组：flag1 = false，方块不写
 *   2. getDataLayerData → null：光照查询恒 null，光照不写——FarLands 引擎 storage
 *      有层，不拦截会双写光照到 chunk NBT
 * 条件恒 false → sections 列表空 → chunk NBT 只剩高度图/BE/实体/结构/tick 等元数据。
 *
 * 方块/光照持久化由 fsa 负责：滑出清理、卸载 flushChunk、定期 flushAllDirty。
 */
@Mixin(ChunkSerializer.class)
public class ChunkSerializerMixin {

    @Redirect(method = "write",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private static LevelChunkSection[] emptySections(ChunkAccess chunk) {
        return new LevelChunkSection[0];
    }

    @Redirect(method = "write",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/lighting/LayerLightEventListener;getDataLayerData(Lnet/minecraft/core/SectionPos;)Lnet/minecraft/world/level/chunk/DataLayer;"))
    private static DataLayer noLight(LayerLightEventListener listener, SectionPos pos) {
        return null;
    }

    /**
     * stage 即 FarLandsGenState.SECTION_STATE attachment，其持久化由 fsa 条目负责：
     * encodeNow 写 stage 字段、applyDecoded 恢复 setStage。chunk NBT 不再含 attachment，
     * 消除双写冗余；重进时 fsa 读回优先，applyDecoded setStage 覆盖。注意：其他 mod 的
     * chunk attachment 也会一并跳过，本项目无其他 attachment，将来引入需另议。
     * javap 确认字节码 invokevirtual 目标 = ChunkAccess.writeAttachmentsToNBT。
     */
    @Redirect(method = "write",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/ChunkAccess;writeAttachmentsToNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;"))
    private static CompoundTag noAttachments(ChunkAccess chunk, HolderLookup.Provider provider) {
        return null;
    }
}
