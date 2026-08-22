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
 * Fix BE position reconstruction on the client.
 * <p>
 * {@code getBlockEntitiesTags} rebuilds each BE position from
 * {@code BlockEntityInfo.packedXZ} + {@code y}. The vanilla {@code y} field is
 * the short-truncated value; the full Y is stored in the mixin-injected
 * {@code yCorrect} field (see
 * {@code ClientboundLevelChunkPacketData$BlockEntityInfoMixin}).
 * {@code BlockEntityInfo} is package-private, so its fields are read via
 * reflection (same pattern as {@code F_WINDOW_MIN_Y}).
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
