package com.inf.farlands.mixin.axisYOverflowFix;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.SectionStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 极端 Y 村民无法认领工作方块/床、无法繁殖——PoiManager.getInChunk 的 POI 查找
 * 遍历 section 范围固定为 levelHeightAccessor 的 [-4, 20)（LevelHeightAccessorMixin
 * 对 ServerLevel 固定）——极端 Y（section ≈ 1.34 亿）的 POI 查不到（数据存在，
 * 存储正常，只是查找范围不含）。
 *
 * 修复：@Overwrite getInSquare——Y 遍历范围 = 维度范围 ∪ 查询位置附近（并集）：
 * 正常 Y 时 pos 范围被 distinct 吸收（vanilla 完全等价——PortalForcer 传送门查找
 * 不受影响）；极端 Y 时覆盖极端 section（村民查到 POI）。距离过滤（|dx|/|dz|）
 * 保持 vanilla。维度范围硬编码 -4/20（与 getInChunk 的 levelHeightAccessor
 * getMinSection/getMaxSection 运行时值一致——LevelHeightAccessorMixin 固定）。
 *
 * getOrLoad 是 SectionStorage 的 protected 继承方法——@Shadow 不解析继承方法
 * → 反射。**不能用 getOrLoad**：它对无条目 section（极端 Y
 * 未预填充）readColumn 读磁盘后仍无 → 抛 IllegalStateException（vanilla 假设
 * 维度范围内必有条目）。改用 **get**（storage 直查，无条目返回 null 不抛）：
 * 正常 Y 等价（readColumn 预填充维度范围 empty）；极端 Y 覆盖内存 POI（刚 add
 * 的必在 storage）。
 */
@Mixin(PoiManager.class)
public class PoiManagerMixin {

    private static final Method M_GET;
    static {
        try {
            M_GET = SectionStorage.class.getDeclaredMethod("get", long.class);
            M_GET.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({ "unchecked" })
    private static Optional<PoiSection> reflectGet(PoiManager self, long sectionKey) {
        try {
            return (Optional<PoiSection>) M_GET.invoke(self, sectionKey);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("null")
    @Overwrite
    public Stream<PoiRecord> getInSquare(Predicate<Holder<PoiType>> typePredicate, BlockPos pos, int distance,
            PoiManager.Occupancy status) {
        int i = Math.floorDiv(distance, 16) + 1;
        int posMin = SectionPos.blockToSectionCoord(pos.getY()) - i;
        int posMax = SectionPos.blockToSectionCoord(pos.getY()) + i + 1;
        return ChunkPos.rangeClosed(new ChunkPos(pos), i).flatMap(posChunk ->
                IntStream.concat(IntStream.range(-4, 20), IntStream.range(posMin, posMax))
                        .distinct()
                        .mapToObj(sy -> reflectGet((PoiManager) (Object) this, SectionPos.of(posChunk, sy).asLong()))
                        .filter(java.util.Objects::nonNull)
                        .filter(Optional::isPresent)
                        .flatMap(o -> o.get().getRecords(typePredicate, status)))
                .filter(p_217971_ -> {
                    BlockPos blockpos = p_217971_.getPos();
                    return Math.abs(blockpos.getX() - pos.getX()) <= distance
                            && Math.abs(blockpos.getZ() - pos.getZ()) <= distance;
                });
    }
}
