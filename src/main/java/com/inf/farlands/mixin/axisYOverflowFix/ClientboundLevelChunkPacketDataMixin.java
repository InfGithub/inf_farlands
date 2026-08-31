package com.inf.farlands.mixin.axisYOverflowFix;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.block.entity.BlockEntityType;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 修复客户端方块实体位置重建。
 * <p>
 * {@code getBlockEntitiesTags} 从 {@code BlockEntityInfo.packedXZ} + {@code y}
 * 重建每个方块实体位置。vanilla 的 {@code y} 字段是 short 截断值；完整 Y 存于
 * mixin 注入的 {@code yCorrect} 字段。参见
 * {@code ClientboundLevelChunkPacketData$BlockEntityInfoMixin}。
 * {@code BlockEntityInfo} 是包私有，其字段经反射读取。
 */
@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ClientboundLevelChunkPacketDataMixin {

    @SuppressWarnings("rawtypes")
    @Shadow
    @Final
    private List blockEntitiesData;

    private static final Class<?> C_BLOCK_ENTITY_INFO;
    private static final Field F_PACKED_XZ;
    private static final Field F_Y_CORRECT;
    private static final Field F_TYPE;
    private static final Field F_TAG;

    static {
        try {
            C_BLOCK_ENTITY_INFO = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData$BlockEntityInfo");
            F_PACKED_XZ = C_BLOCK_ENTITY_INFO.getDeclaredField("packedXZ");
            F_PACKED_XZ.setAccessible(true);
            Field yCorrect = null;
            for (Field f : C_BLOCK_ENTITY_INFO.getDeclaredFields()) {
                if (f.getName().endsWith("yCorrect")) {
                    yCorrect = f;
                    break;
                }
            }
            if (yCorrect == null) {
                throw new RuntimeException("yCorrect field not found on BlockEntityInfo");
            }
            yCorrect.setAccessible(true);
            F_Y_CORRECT = yCorrect;
            F_TYPE = C_BLOCK_ENTITY_INFO.getDeclaredField("type");
            F_TYPE.setAccessible(true);
            F_TAG = C_BLOCK_ENTITY_INFO.getDeclaredField("tag");
            F_TAG.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    private void getBlockEntitiesTags(ClientboundLevelChunkPacketData.BlockEntityTagOutput output, int chunkX,
            int chunkZ) {
        int i = 16 * chunkX;
        int j = 16 * chunkZ;
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        try {
            for (Object info : this.blockEntitiesData) {
                int packedXZ = F_PACKED_XZ.getInt(info);
                int y = F_Y_CORRECT.getInt(info);
                int k = i + SectionPos.sectionRelative(packedXZ >> 4);
                int l = j + SectionPos.sectionRelative(packedXZ);
                mpos.set(k, y, l);
                output.accept(mpos, (BlockEntityType<?>) F_TYPE.get(info), (CompoundTag) F_TAG.get(info));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
