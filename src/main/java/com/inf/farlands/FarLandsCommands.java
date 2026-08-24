package com.inf.farlands;

import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.lighting.LevelLightEngine;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * 诊断命令：/farlands section light dump —— 服务端 dump 玩家当前 section 的
 * sky/block 光照层全量矩阵 + 方块矩阵。op 权限。
 * 注意：本类不引用任何客户端类（dedicated server 加载）；客户端命令在
 * ClientFarLandsCommands。
 */
public class FarLandsCommands {

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("farlands")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("section")
                                .then(Commands.literal("light")
                                        .then(Commands.literal("dump")
                                                .executes(ctx -> dump(ctx.getSource()))))
                                .then(Commands.literal("biome")
                                        .then(Commands.literal("dump")
                                                .executes(ctx -> dumpBiomeCmd(ctx.getSource()))))));
    }

    @SuppressWarnings("null")
    private static int dump(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            SectionPos sec = SectionPos.of(pos);
            LevelLightEngine le = level.getChunkSource().getLightEngine();

            InfFarlands.LOGGER.info("FLDUMP server pos={},{},{} sec={},{},{}",
                    pos.getX(), pos.getY(), pos.getZ(), sec.x(), sec.y(), sec.z());
            dumpLayer(le, LightLayer.SKY, sec);
            dumpLayer(le, LightLayer.BLOCK, sec);
            dumpBlocks(level, sec);
            source.sendSuccess(() -> Component.translatable("commands.inf_farlands.section.light.dump"), false);
        } catch (Exception e) {
            InfFarlands.LOGGER.error("FLDUMP server err", e);
        }
        return 1;
    }

    @SuppressWarnings("null")
    private static int dumpBiomeCmd(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            BlockPos pos = player.blockPosition();
            SectionPos sec = SectionPos.of(pos);
            InfFarlands.LOGGER.info("BIODUMP server pos={},{},{} sec={},{},{}",
                    pos.getX(), pos.getY(), pos.getZ(), sec.x(), sec.y(), sec.z());
            dumpBiomes(level, sec);
            source.sendSuccess(() -> Component.translatable("commands.inf_farlands.section.biome.dump"), false);
        } catch (Exception e) {
            InfFarlands.LOGGER.error("BIODUMP server err", e);
        }
        return 1;
    }

    /** 当前 section 的 4x4x4 biome 网格（服务端/客户端共用）。 */
    @SuppressWarnings("null")
    public static void dumpBiomes(Level level, SectionPos sec) {
        try {
            ChunkAccess ca = level.getChunk(sec.x(), sec.z(), ChunkStatus.FULL, false);
            if (!(ca instanceof LevelChunk lc)) {
                InfFarlands.LOGGER.info("BIODUMP secY={} chunk null", sec.y());
                return;
            }
            LevelChunkSection s = ((WindowedChunk) lc).windowedAllSections().get(sec.y());
            if (s == null) {
                InfFarlands.LOGGER.info("BIODUMP secY={} section null", sec.y());
                return;
            }
            for (int y = 0; y < 4; y++) {
                StringBuilder sb = new StringBuilder();
                sb.append("BIODUMP secY=").append(sec.y()).append(" y=").append(y);
                for (int z = 0; z < 4; z++) {
                    sb.append("\nBIODUMP   z=").append(z).append(' ');
                    for (int x = 0; x < 4; x++) {
                        Holder<Biome> b = s.getBiomes().get(x, y, z);
                        sb.append(b.unwrapKey().map(k -> k.location().getPath()).orElse("?")).append(' ');
                    }
                }
                InfFarlands.LOGGER.info("{}", sb);
            }
        } catch (Exception e) {
            InfFarlands.LOGGER.error("BIODUMP err", e);
        }
    }

    @SuppressWarnings("null")
    public static void dumpLayer(LevelLightEngine le, LightLayer layer, SectionPos sec) {
        DataLayer dl = le.getLayerListener(layer).getDataLayerData(sec);
        String tag = layer == LightLayer.SKY ? "SKY" : "BLK";
        if (dl == null) {
            InfFarlands.LOGGER.info("FLDUMP {} secY={} null", tag, sec.y());
            return;
        }
        if (dl.isEmpty()) {
            InfFarlands.LOGGER.info("FLDUMP {} secY={} empty", tag, sec.y());
            return;
        }
        for (int y = 0; y < 16; y++) {
            StringBuilder sb = new StringBuilder();
            sb.append("FLDUMP ").append(tag).append(" secY=").append(sec.y()).append(" y=").append(y);
            for (int z = 0; z < 16; z++) {
                sb.append("\nFLDUMP   z=").append(z).append(' ');
                for (int x = 0; x < 16; x++) {
                    sb.append(Character.forDigit(dl.get(x, y, z) & 0xFF, 16));
                }
            }
            InfFarlands.LOGGER.info("{}", sb);
        }
    }

    /** 方块矩阵（. = 空气，# = 非空气）——与光照矩阵对照，区分方块格 0（正常）与空气格 0（异常） */
    @SuppressWarnings("null")
    public static void dumpBlocks(Level level, SectionPos sec) {
        try {
            ChunkAccess ca = level.getChunk(sec.x(), sec.z(), ChunkStatus.FULL, false);
            if (!(ca instanceof LevelChunk lc)) {
                InfFarlands.LOGGER.info("FLDUMP BLOCKS secY={} null", sec.y());
                return;
            }
            LevelChunkSection s = ((WindowedChunk) lc).windowedAllSections().get(sec.y());
            if (s == null) {
                InfFarlands.LOGGER.info("FLDUMP BLOCKS secY={} empty", sec.y());
                return;
            }
            for (int y = 0; y < 16; y++) {
                StringBuilder sb = new StringBuilder();
                sb.append("FLDUMP BLOCKS secY=").append(sec.y()).append(" y=").append(y);
                for (int z = 0; z < 16; z++) {
                    sb.append("\nFLDUMP   z=").append(z).append(' ');
                    for (int x = 0; x < 16; x++) {
                        sb.append(s.getBlockState(x, y, z).isAir() ? '.' : '#');
                    }
                }
                InfFarlands.LOGGER.info("{}", sb);
            }
        } catch (Exception e) {
            InfFarlands.LOGGER.error("FLDUMP BLOCKS err", e);
        }
    }
}
