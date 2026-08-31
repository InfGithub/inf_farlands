package com.inf.farlands.terrain.pipeline;

import com.inf.farlands.InfFarlands;

import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.ChunkAccess;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * per-section 生成状态机，XYZ 同结构，状态挂在 section 立方体上。
 *
 * 顺序单值：UNPROCESSED → NOISE → LIGHTED。未来扩展 SURFACE/DECORATED 为更大的 int 值。
 * 存储：NeoForge attachment，ChunkAccess 已实现 IAttachmentHolder，
 * 用 ConcurrentHashMap<sectionY, stage> 保存状态。无 key 时 getOrDefault 返回 UNPROCESSED。
 * 持久化：自定义 IAttachmentSerializer 直写 NBT，格式为 list of {y, stage}。Mojang 通用
 * Codec 的 unboundedMap/compoundList/pair 在 NbtOps 下都要求 String key 或 prefix
 * append，int key 无法编码，手动序列化最稳。
 * LevelChunk 构造自动从 ProtoChunk 复制，走 copyChunkAttachmentsOnPromotion。
 *
 * 重要并发约束：AttachmentHolder 的 attachments 容器是 IdentityHashMap，非线程安全。
 * getData 首次调用会 put 默认值进该容器——因此【首次初始化只允许在主线程】：
 * 短路完成在主线程调 initialize()；genPool 线程此后 getData 只读容器 + 操作 CHM 值。
 */
@SuppressWarnings("null")
public final class FarLandsGenState {

    // ---- per-section 状态：顺序单值 ----
    // 值顺序即依赖顺序：biome（BIOMES）→ 地形（NOISE）→ 地表（SURFACE）→ 雕刻（CARVERS）
    // → 光照（LIGHTED）。
    // 迁移注意：CARVERS 插入后 LIGHTED 4→5（不兼容旧档——旧 fsa stage=4(LIGHTED) 按新
    // 语义解释为 CARVERS，不做补雕/补光照，用户决定）；SURFACE 插入时 LIGHTED 3→4 同理。

    public static final int UNPROCESSED = 0;
    public static final int BIOMES = 1;
    public static final int NOISE = 2;
    public static final int SURFACE = 3;
    public static final int CARVERS = 4;
    public static final int LIGHTED = 5;

    private static final IAttachmentSerializer<CompoundTag, ConcurrentHashMap<Integer, Integer>> SECTION_STATE_SERIALIZER =
            new IAttachmentSerializer<>() {
                @Override
                public ConcurrentHashMap<Integer, Integer> read(IAttachmentHolder holder, CompoundTag tag,
                        HolderLookup.Provider provider) {
                    ConcurrentHashMap<Integer, Integer> m = new ConcurrentHashMap<>();
                    ListTag list = tag.getList("sections", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag e = list.getCompound(i);
                        m.put(e.getInt("y"), e.getInt("stage"));
                    }
                    return m;
                }

                @Override
                public CompoundTag write(ConcurrentHashMap<Integer, Integer> attachment, HolderLookup.Provider provider) {
                    CompoundTag tag = new CompoundTag();
                    ListTag list = new ListTag();
                    for (var e : attachment.entrySet()) {
                        CompoundTag entry = new CompoundTag();
                        entry.putInt("y", e.getKey());
                        entry.putInt("stage", e.getValue());
                        list.add(entry);
                    }
                    tag.put("sections", list);
                    return tag;
                }
            };

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, InfFarlands.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ConcurrentHashMap<Integer, Integer>>> SECTION_STATE =
            ATTACHMENTS.register("section_state",
                    () -> AttachmentType.<ConcurrentHashMap<Integer, Integer>>builder(() -> new ConcurrentHashMap<>())
                            .serialize(SECTION_STATE_SERIALIZER)
                            .build());

    private FarLandsGenState() {
    }

    // ---- helper ----

    /** 主线程初始化：attachments 容器只在主线程写入，IdentityHashMap 非线程安全。 */
    public static void initialize(ChunkAccess chunk) {
        chunk.getData(SECTION_STATE);
    }

    /** 推进状态：getData 初始化后 genPool 线程只读容器并 CHM.put，多线程安全。 */
    public static void setStage(ChunkAccess chunk, int sectionY, int stage) {
        chunk.getData(SECTION_STATE).put(sectionY, stage);
    }

    /** 某阶段是否已做：无 key 时按 UNPROCESSED 处理。 */
    public static boolean isOrAfter(ChunkAccess chunk, int sectionY, int stage) {
        return chunk.getData(SECTION_STATE).getOrDefault(sectionY, UNPROCESSED) >= stage;
    }

    /** 删除某 section 的状态：fsa 清理时状态随 section 落盘，attachment 同步删除。 */
    public static void removeStage(ChunkAccess chunk, int sectionY) {
        chunk.getData(SECTION_STATE).remove(sectionY);
    }

    /** 当前 stage：无 key 时按 UNPROCESSED。 */
    public static int getStage(ChunkAccess chunk, int sectionY) {
        return chunk.getData(SECTION_STATE).getOrDefault(sectionY, UNPROCESSED);
    }

    /** 光照完成回调：该 chunk 全部 NOISE/SURFACE/CARVERS 升 LIGHTED（光照覆盖全 chunk，
     * 含已地表/雕刻处理），CHM.replaceAll 线程安全。 */
    public static void promoteAllGenToLighted(ChunkAccess chunk) {
        chunk.getData(SECTION_STATE)
                .replaceAll((k, v) -> (v == NOISE || v == SURFACE || v == CARVERS) ? LIGHTED : v);
    }

    /** 该 chunk 是否还有 NOISE 未 LIGHTED 的 section；光照完成后再检查，驱动下一批。 */
    public static boolean hasAnyGen(ChunkAccess chunk) {
        for (int v : chunk.getData(SECTION_STATE).values()) {
            if (v == NOISE) {
                return true;
            }
        }
        return false;
    }

    /** 遍历该 chunk 的 section 状态。 */
    public static void forEachStage(ChunkAccess chunk, java.util.function.BiConsumer<Integer, Integer> consumer) {
        chunk.getData(SECTION_STATE).forEach(consumer);
    }
}