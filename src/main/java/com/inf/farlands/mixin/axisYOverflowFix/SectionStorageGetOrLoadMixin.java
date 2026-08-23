package com.inf.farlands.mixin.axisYOverflowFix;

import java.util.Optional;

import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 极端 Y 放置 POI 方块（工作方块/床）时 PoiManager.add → getOrCreate → getOrLoad
 * 抛 IllegalStateException（L89）——getOrLoad 对该 section storage 无条目 →
 * readColumn（读磁盘）仍无 → 抛（vanilla 假设"维度范围内 readColumn 后必有条目"，
 * 我们的极端 Y section 无数据——readColumn 只预填充/恢复维度范围）。
 * 修复：throw → Optional.empty()——getOrCreate 收到 empty 后创建新 PoiSection
 * 并存储（POI 注册成功）。readColumn 保留（维度范围内已持久化 POI 的磁盘恢复
 * 不受影响）。
 * 新 mixin 而非并入 SectionStorageMixin：避免同类 @Shadow readColumn 与
 * @Overwrite readColumn 注解冲突（不同 mixin 类 @Shadow 被 @Overwrite 的方法可行）。
 */
@Mixin(SectionStorage.class)
public abstract class SectionStorageGetOrLoadMixin {

    @Shadow
    @SuppressWarnings("rawtypes")
    protected abstract Optional get(long sectionKey);

    @Shadow
    protected abstract boolean outsideStoredRange(long sectionKey);

    @Shadow
    private void readColumn(ChunkPos chunkPos) {
    }

    @SuppressWarnings({ "rawtypes"})
    @Overwrite
    protected Optional getOrLoad(long sectionKey) {
        if (this.outsideStoredRange(sectionKey)) {
            return Optional.empty();
        }
        Optional optional = this.get(sectionKey);
        if (optional != null) {
            return optional;
        }
        this.readColumn(SectionPos.of(sectionKey).chunk());
        optional = this.get(sectionKey);
        // 原抛 IllegalStateException——极端 Y section 无数据 → 返回 empty（getOrCreate 创建新 section）
        return optional == null ? Optional.empty() : optional;
    }
}
