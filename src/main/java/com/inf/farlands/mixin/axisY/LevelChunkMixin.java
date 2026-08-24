package com.inf.farlands.mixin.axisY;

import com.inf.farlands.WindowedChunk;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    private static final Field F_ALL_SECTIONS;
    private static final Field F_BIOME_REGISTRY;
    static {
        try {
            F_ALL_SECTIONS = ChunkAccess.class.getDeclaredField("allSections");
            F_ALL_SECTIONS.setAccessible(true);
            F_BIOME_REGISTRY = ChunkAccess.class.getDeclaredField("biomeRegistry");
            F_BIOME_REGISTRY.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * LevelChunk 构造 this() 参数里的 chunk.getSections()（ProtoChunk 窗口视图，
     * 死状态 1 section）→ 改为从 allSections 按维度范围（ServerLevel 的
     * getMinSection/getMaxSection = -4/20）构造完整数组。initAllSections 全量
     * 转移 → BE 转移（构造器主体 L129）时 allSections 已完整（无 BE 的 chunk
     * 同样完整——不依赖 BE 循环）。
     */
    @Redirect(method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/ProtoChunk;Lnet/minecraft/world/level/chunk/LevelChunk$PostLoadProcessor;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ProtoChunk;getSections()[Lnet/minecraft/world/level/chunk/LevelChunkSection;"))
    private static LevelChunkSection[] redirectGetSections(ProtoChunk chunk) {
        LevelHeightAccessor lha = ((WindowedChunk) chunk).levelHeightAccessor();
        Map<Integer, LevelChunkSection> all = ((WindowedChunk) chunk).windowedAllSections();
        int min = lha.getMinSection();
        int max = lha.getMaxSection();
        LevelChunkSection[] arr = new LevelChunkSection[max - min];
        for (int i = min; i < max; i++) {
            arr[i - min] = all.get(i);
        }
        return arr;
    }

    @Overwrite
    public BlockState getBlockState(BlockPos pos) {
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        try {
            ChunkAccess ca = (ChunkAccess) (Object) this;
            LevelChunkSection s = ca.getSection(((LevelHeightAccessor) (Object) this).getSectionIndex(y));
            if (s != null && !s.hasOnlyAir()) {
                return s.getBlockState(x & 15, y & 15, z & 15);
            }
            return Blocks.AIR.defaultBlockState();
        } catch (Throwable t) {
            CrashReport cr = CrashReport.forThrowable(t, "Getting block state");
            CrashReportCategory cat = cr.addCategory("Block being got");
            cat.setDetail("Location", () -> CrashReportCategory.formatLocation(((LevelChunk) (Object) this), x, y, z));
            throw new ReportedException(cr);
        }
    }

    @Overwrite
    public FluidState getFluidState(int x, int y, int z) {
        try {
            ChunkAccess ca = (ChunkAccess) (Object) this;
            LevelChunkSection s = ca.getSection(((LevelHeightAccessor) (Object) this).getSectionIndex(y));
            if (s != null && !s.hasOnlyAir()) {
                return s.getFluidState(x & 15, y & 15, z & 15);
            }
            return Fluids.EMPTY.defaultFluidState();
        } catch (Throwable t) {
            CrashReport cr = CrashReport.forThrowable(t, "Getting fluid state");
            CrashReportCategory cat = cr.addCategory("Block being got");
            cat.setDetail("Location", () -> CrashReportCategory.formatLocation(((LevelChunk) (Object) this), x, y, z));
            throw new ReportedException(cr);
        }
    }

    @SuppressWarnings({ "null", "unchecked" })
    @Overwrite
    public void replaceWithPacketData(
            FriendlyByteBuf buffer, CompoundTag tag,
            Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> outputTagConsumer) {
        LevelChunk self = (LevelChunk) (Object) this;
        self.clearAllBlockEntities();
        ChunkAccess ca = (ChunkAccess) (Object) this;
        try {
            Map<Integer, LevelChunkSection> all = (Map<Integer, LevelChunkSection>) F_ALL_SECTIONS.get(this);
            int sectionCount = buffer.readVarInt();
            for (int i = 0; i < sectionCount; i++) {
                int sectionY = buffer.readVarInt();
                // 不可变切换：新建 section 整体替换——PalettedContainer.read 的 createOrReuseData
                // 会复用现有 Data 原地改 palette，与渲染编译线程并发读时读到 palette 中间状态
                // → MissingPaletteEntryException CTD。新建对象无并发读者。
                LevelChunkSection s = new LevelChunkSection(getBiomeRegistry(ca));
                s.read(buffer);
                all.put(sectionY, s);
                ca.getSection(ca.getSectionIndexFromSectionY(sectionY)); // 数组同步（get 内部 arr[idx]=s）
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        for (Heightmap.Types type : Heightmap.Types.values()) {
            String key = type.getSerializationKey();
            if (tag.contains(key, 12)) {
                ((ChunkAccess) (Object) this).setHeightmap(type, tag.getLongArray(key));
            }
        }
        ((ChunkAccess) (Object) this).initializeLightSources();
        outputTagConsumer.accept((pos, beType, updateTag) -> {
            BlockEntity be = self.getBlockEntity(pos,
                    LevelChunk.EntityCreationType.IMMEDIATE);
            if (be != null && updateTag != null && be.getType() == beType) {
                be.handleUpdateTag(updateTag, self.getLevel().registryAccess());
            }
        });
    }

    @Overwrite
    public void replaceBiomes(FriendlyByteBuf buffer) {
        ChunkAccess ca = (ChunkAccess) (Object) this;
        int sectionCount = buffer.readVarInt();
        for (int i = 0; i < sectionCount; i++) {
            int sectionY = buffer.readVarInt();
            LevelChunkSection s = ca.getSection(ca.getSectionIndexFromSectionY(sectionY));
            if (s != null) {
                s.readBiomes(buffer);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Registry<Biome> getBiomeRegistry(ChunkAccess ca) {
        try {
            return (Registry<Biome>) F_BIOME_REGISTRY.get(ca);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
