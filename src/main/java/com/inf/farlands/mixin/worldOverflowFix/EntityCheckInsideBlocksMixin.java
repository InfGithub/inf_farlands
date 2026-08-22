package com.inf.farlands.mixin.worldOverflowFix;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * #36 极端 XZ 投掷物（WitherSkull 等）冻结修复。
 *
 * checkInsideBlocks 三层 for 循环边界 = BlockPos.containing(包围盒)（int 值域），
 * 实体在 ±2.14B 合法坐标时上界可达 Integer.MAX_VALUE（2147483647）→ int 递增
 * 溢出成 MIN_VALUE ≤ 上界 → 无限循环（与 #24 renderSnowAndRain 同构）。
 * Y 轴被 hasChunksAt 门保护（门开时上界 < MAX_VALUE，上界 = MAX_VALUE 时门关），
 * 冻结只发生 X/Z 轴。
 *
 * 修复：循环变量 long 化——起止点都是 BlockPos（int 值域），long 边界不溢出，
 * 循环按区间差值正常终止（正常包围盒几格，迭代次数不变；极端坐标 2-3 次）。
 * 体内 (int) 转换无损（i 值域 = [blockpos, blockpos1]，恒在 int 内）。
 * 逐行对照 vanilla（Entity.java:1027-1057），仅循环变量类型改变。
 */
@Mixin(Entity.class)
public abstract class EntityCheckInsideBlocksMixin {

    @Shadow
    public abstract AABB getBoundingBox();

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract boolean isAlive();

    @Shadow
    protected abstract void onInsideBlock(BlockState state);

    @SuppressWarnings({ "null", "deprecation" })
    @Overwrite
    protected void checkInsideBlocks() {
        AABB aabb = this.getBoundingBox();
        BlockPos blockpos = BlockPos.containing(aabb.minX + 1.0E-7, aabb.minY + 1.0E-7, aabb.minZ + 1.0E-7);
        BlockPos blockpos1 = BlockPos.containing(aabb.maxX - 1.0E-7, aabb.maxY - 1.0E-7, aabb.maxZ - 1.0E-7);
        if (this.level().hasChunksAt(blockpos, blockpos1)) {
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for (long i = blockpos.getX(); i <= blockpos1.getX(); i++) {
                for (long j = blockpos.getY(); j <= blockpos1.getY(); j++) {
                    for (long k = blockpos.getZ(); k <= blockpos1.getZ(); k++) {
                        if (!this.isAlive()) {
                            return;
                        }

                        blockpos$mutableblockpos.set((int) i, (int) j, (int) k);
                        BlockState blockstate = this.level().getBlockState(blockpos$mutableblockpos);

                        try {
                            blockstate.entityInside(this.level(), blockpos$mutableblockpos, (Entity) (Object) this);
                            this.onInsideBlock(blockstate);
                        } catch (Throwable throwable) {
                            CrashReport crashreport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                            CrashReportCategory crashreportcategory = crashreport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashreportcategory, this.level(), blockpos$mutableblockpos, blockstate);
                            throw new ReportedException(crashreport);
                        }
                    }
                }
            }
        }
    }
}
