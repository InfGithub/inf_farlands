package com.inf.farlands.serialize;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.mojang.serialization.Codec;

import net.jpountz.lz4.LZ4BlockInputStream;
import net.jpountz.lz4.LZ4BlockOutputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;

/**
 * section 级编解码：fsa 条目字节 ↔ LevelChunkSection + 光照 + stage。
 *
 * 条目格式与 Anvil chunk 条目同构：
 *   长度 int 采用 +1 语义，防御性区分"存在但空"与"不存在"——fsa 内部 offset=0 已表示不存在，保留一致性
 *   压缩版本 byte，lz4
 *   lz4 压缩 NBT
 *
 * NBT 结构：
 *   DataVersion / Y(int 绝对 sectionY，不截断) / block_states / biomes
 *   / BlockLight(byte[2048], isEmpty 跳过) / SkyLight(同上) / stage
 *
 * 不接 vanilla datafix，mod 锁定 1.21.1，不跨版本；DataVersion 仅诊断/未来迁移。
 */
@SuppressWarnings("null")
public final class SectionSerializer {

    private static final Codec<PalettedContainer<BlockState>> BLOCK_EC;
    private static final Method MAKE_BIOME_CODEC;

    static {
        try {
            Field bf = ChunkSerializer.class.getDeclaredField("BLOCK_STATE_CODEC");
            bf.setAccessible(true);
            @SuppressWarnings("unchecked")
            Codec<PalettedContainer<BlockState>> c = (Codec<PalettedContainer<BlockState>>) bf.get(null);
            BLOCK_EC = c;

            MAKE_BIOME_CODEC = ChunkSerializer.class.getDeclaredMethod("makeBiomeCodec", Registry.class);
            MAKE_BIOME_CODEC.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SectionSerializer() {
    }

    @SuppressWarnings("unchecked")
    private static Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec(Registry<Biome> biomes) {
        try {
            return (Codec<PalettedContainerRO<Holder<Biome>>>) MAKE_BIOME_CODEC.invoke(null, biomes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 编码：section + 光照层 + stage → fsa 条目字节，含长度头。 */
    public static byte[] encode(LevelChunkSection section, DataLayer blockLight, DataLayer skyLight,
            int stage, Registry<Biome> biomes, int sectionY) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        tag.putInt("Y", sectionY);
        tag.put("block_states", BLOCK_EC.encodeStart(NbtOps.INSTANCE, section.getStates()).getOrThrow());
        tag.put("biomes", biomeCodec(biomes).encodeStart(NbtOps.INSTANCE, section.getBiomes()).getOrThrow());
        if (blockLight != null && !blockLight.isEmpty()) {
            tag.putByteArray("BlockLight", blockLight.getData());
        }
        if (skyLight != null && !skyLight.isEmpty()) {
            tag.putByteArray("SkyLight", skyLight.getData());
        }
        tag.putInt("stage", stage);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
            LZ4BlockOutputStream lz4 = new LZ4BlockOutputStream(baos);
            DataOutputStream out = new DataOutputStream(lz4);
            NbtIo.write(tag, out);
            out.flush();
            lz4.close();
            byte[] payload = baos.toByteArray();

            ByteArrayOutputStream entry = new ByteArrayOutputStream(payload.length + 5);
            DataOutputStream entryOut = new DataOutputStream(entry);
            entryOut.writeInt(payload.length + 1); // +1 语义，vanilla 惯例
            entryOut.writeByte(4); // lz4 版本 ID，对齐 RegionFileVersion.VERSION_LZ4
            entryOut.write(payload);
            entryOut.flush();
            return entry.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("fsa encode sectionY=" + sectionY, e);
        }
    }

    /** 解码结果。 */
    public record DecodedSection(LevelChunkSection section, DataLayer blockLight, DataLayer skyLight, int stage) {
    }

    /** 解码：fsa 条目字节 → section + 光照 + stage。数据损坏抛异常，调用方按不存在/重生成处理。 */
    public static DecodedSection decode(byte[] entry, Registry<Biome> biomes, int sectionY) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(entry));
            int len = in.readInt();
            in.readByte(); // 版本，lz4；不校验，格式版本由文件级 magic 保证
            int payloadLen = len - 1;
            if (payloadLen < 0 || payloadLen > entry.length - 5) {
                throw new IllegalStateException("fsa entry length corrupt: " + len);
            }
            byte[] payload = new byte[payloadLen];
            in.readFully(payload);

            LZ4BlockInputStream lz4 = new LZ4BlockInputStream(new ByteArrayInputStream(payload));
            DataInputStream nbtIn = new DataInputStream(lz4);
            CompoundTag tag = NbtIo.read(nbtIn);
            nbtIn.close();

            if (!tag.contains("block_states", 10) || !tag.contains("biomes", 10)) {
                throw new IllegalStateException("fsa entry missing block_states/biomes");
            }
            PalettedContainer<BlockState> states = BLOCK_EC
                    .parse(NbtOps.INSTANCE, tag.getCompound("block_states"))
                    .getOrThrow();
            PalettedContainerRO<Holder<Biome>> bios = biomeCodec(biomes)
                    .parse(NbtOps.INSTANCE, tag.getCompound("biomes"))
                    .getOrThrow();
            LevelChunkSection section = new LevelChunkSection(states, bios);
            DataLayer bl = tag.contains("BlockLight", 7) ? new DataLayer(tag.getByteArray("BlockLight")) : null;
            DataLayer sl = tag.contains("SkyLight", 7) ? new DataLayer(tag.getByteArray("SkyLight")) : null;
            int stage = tag.getInt("stage");
            return new DecodedSection(section, bl, sl, stage);
        } catch (Exception e) {
            throw new RuntimeException("fsa decode sectionY=" + sectionY, e);
        }
    }
}
