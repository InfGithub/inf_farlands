package com.inf.farlands.mixin.axisY;

import com.inf.farlands.window.WindowedChunk;

import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    /**
     * fsa：禁用 spawn chunk 常驻，不添加 START ticket。
     * setDefaultSpawnPos 的 addRegionTicket(TicketType.START, ...) 是出生点常驻 ticket，
     * 使 spawn chunk 永不卸载。配合 prepareLevels 跳过预生成，出生点区域按需生成/卸载：
     * 玩家在时由视距 ticket 保持，离开后卸载落盘。
     */
    @Redirect(method = "setDefaultSpawnPos",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
    private void noSpawnTicket(ServerChunkCache cache, TicketType<?> type, ChunkPos pos, int distance, Object value) {
        // fsa：不添加 START ticket，spawn chunk 按需生成/卸载
    }

    // @Shadow 只解析目标类自身声明的成员，不解析继承成员。
    // Level 的成员 random/isRaining/getProfiler/getBlockRandomPos 等全部
    // public，用 ((Level)(Object)this) cast 直接访问。
    @Shadow
    private List<ServerPlayer> players;

    @Shadow
    protected abstract BlockPos findLightningTargetAround(BlockPos pos);

    @Shadow
    public abstract void tickPrecipitation(BlockPos blockPos);

    /**
     * tickChunk 改造见 window.md §4.5：thunder/iceandsnow 段原样保留，
     * 含 NeoForge LightningRodBlock 补丁；tickBlocks 段遍历源从 getSections()
     * 改为覆盖该 chunk 的玩家窗口并集，因为 getSections() 在服务端窗口死状态后
     * 塌缩为构造默认 1 section；窗口并集按 XZ 视距过滤，Y 窗口限定为
     * [center-17, center+16]。
     *
     * section 直查 allSections 而不 getSection 懒创建，防空 section 污染仓库。
     * 极端 Y 下窗口边界 section 的 minBlockY 可能溢出 int，但那些坐标不可表示，
     * 对应 section 必然无数据，先经 null 检查跳过，k 不会对它们计算。
     */
    @SuppressWarnings({ "null", "resource" })
    @Overwrite
    public void tickChunk(LevelChunk chunk, int randomTickSpeed) {
        Level level = (Level) (Object) this;
        ChunkPos chunkpos = chunk.getPos();
        boolean flag = level.isRaining();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        ProfilerFiller profilerfiller = level.getProfiler();
        profilerfiller.push("thunder");
        if (flag && level.isThundering() && level.random.nextInt(100000) == 0) {
            BlockPos blockpos = this.findLightningTargetAround(level.getBlockRandomPos(i, 0, j, 15));
            if (level.isRainingAt(blockpos)) {
                DifficultyInstance difficultyinstance = level.getCurrentDifficultyAt(blockpos);
                boolean flag1 = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                        && level.random.nextDouble() < (double) difficultyinstance.getEffectiveDifficulty() * 0.01
                        && !(level.getBlockState(blockpos.below()).getBlock() instanceof LightningRodBlock);
                if (flag1) {
                    SkeletonHorse skeletonhorse = EntityType.SKELETON_HORSE
                            .create((ServerLevel) (Object) this);
                    if (skeletonhorse != null) {
                        skeletonhorse.setTrap(true);
                        skeletonhorse.setAge(0);
                        skeletonhorse.setPos((double) blockpos.getX(), (double) blockpos.getY(),
                                (double) blockpos.getZ());
                        level.addFreshEntity(skeletonhorse);
                    }
                }

                LightningBolt lightningbolt = EntityType.LIGHTNING_BOLT
                        .create((ServerLevel) (Object) this);
                if (lightningbolt != null) {
                    lightningbolt.moveTo(Vec3.atBottomCenterOf(blockpos));
                    lightningbolt.setVisualOnly(flag1);
                    level.addFreshEntity(lightningbolt);
                }
            }
        }

        profilerfiller.popPush("iceandsnow");

        for (int i1 = 0; i1 < randomTickSpeed; i1++) {
            if (level.random.nextInt(48) == 0) {
                this.tickPrecipitation(level.getBlockRandomPos(i, 0, j, 15));
            }
        }

        profilerfiller.popPush("tickBlocks");
        if (randomTickSpeed > 0) {
            IntSet sectionYs = new IntArraySet();
            for (ServerPlayer player : this.players) {
                if (!player.getChunkTrackingView().contains(chunkpos)) {
                    continue;
                }

                int centerY = Mth.floorDiv(player.getBlockY(), 16);
                for (int sy = centerY - ((WindowedChunk) chunk).windowHalfBelow(); sy <= centerY
                        + ((WindowedChunk) chunk).windowHalfAbove(); sy++) {
                    sectionYs.add(sy);
                }
            }
            for (int sy : sectionYs) {
                LevelChunkSection section = ((WindowedChunk) chunk).windowedAllSections().get(sy);
                if (section == null || !section.isRandomlyTicking()) {
                    continue;
                }

                int k = SectionPos.sectionToBlockCoord(sy);
                for (int l = 0; l < randomTickSpeed; l++) {
                    BlockPos blockpos1 = level.getBlockRandomPos(i, k, j, 15);
                    profilerfiller.push("randomTick");
                    BlockState blockstate = section.getBlockState(
                            blockpos1.getX() - i, blockpos1.getY() - k, blockpos1.getZ() - j);
                    if (blockstate.isRandomlyTicking()) {
                        blockstate.randomTick((ServerLevel) (Object) this, blockpos1, level.random);
                    }

                    FluidState fluidstate = blockstate.getFluidState();
                    if (fluidstate.isRandomlyTicking()) {
                        fluidstate.randomTick((ServerLevel) (Object) this, blockpos1, level.random);
                    }

                    profilerfiller.pop();
                }
            }
        }
        profilerfiller.pop();
    }
}
