package com.inf.farlands.mixin.axisY;

import com.inf.farlands.window.EntitySectionWindow;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * BE tick 窗口化（Create 兼容 B2）：窗口外 section 的方块实体不 tick。
 *
 * <p>fsa 清理把玩家 Y 窗口并集 + margin 外的 section 从内存删除（方块变 AIR），
 * 但 BE 是 chunk 级持有、照常 tick（Level.tickBlockEntities 的门
 * shouldTickBlocksAt 只查 XZ 视距）——窗口外 Create 机械 BE 读自身方块 = AIR →
 * tick 早退停转（Belt 有防护）或触发无防护路径（isLastBelt 的 getValue）。
 *
 * <p>本注入：ServerLevel 上 shouldTickBlocksAt(BlockPos) 增加 Y 窗口判断——
 * 窗口外 BE 不 tick（冻结），与实体窗口（方案 D）/随机刻窗口（tickChunk）
 * 的"窗口外冻结"语义完整一致；窗口外不再读 AIR、不再有 tick 浪费。
 * 客户端（非 ServerLevel）原样走 XZ 判断，行为不变（note.md #10 双端
 * 共享类教训：实例级判断区分逻辑端）。
 *
 * <p>影响面：Level.tickBlockEntities（BE tick）、ServerLevel 方块事件、
 * SculkSpreader 传播——均与"窗口外冻结"一致；ServerChunkCache/VibrationSystem
 * 走 long 版不受影响。
 */
@Mixin(Level.class)
public abstract class LevelShouldTickBlocksAtMixin {

    @Overwrite
    public boolean shouldTickBlocksAt(BlockPos pos) {
        if ((Object) this instanceof ServerLevel && !EntitySectionWindow.inAnyWindow(pos.getY() >> 4)) {
            return false;
        }
        return ((Level) (Object) this).shouldTickBlocksAt(ChunkPos.asLong(pos));
    }
}
