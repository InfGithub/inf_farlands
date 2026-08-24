package com.inf.farlands.client;

import com.inf.farlands.FarLandsCommands;
import com.inf.farlands.InfFarlands;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;

import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.network.chat.Component;

/**
 * 客户端（渲染端）诊断命令：/farlands client section light dump —— 与服务端
 * FLDUMP 同格式输出客户端光照层 + 方块矩阵，用于对比定位同步断链。
 * 本类引用客户端类（Minecraft），只在客户端加载（服务端不注册、不加载）。
 */
public class ClientFarLandsCommands {

    public static void registerClient(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("farlands")
                        .then(Commands.literal("client")
                                .then(Commands.literal("section")
                                        .then(Commands.literal("light")
                                                .then(Commands.literal("dump")
                                                        .executes(ctx -> dumpClient(ctx.getSource()))))
                                        .then(Commands.literal("biome")
                                                .then(Commands.literal("dump")
                                                        .executes(ctx -> dumpBiomeClient(ctx.getSource())))))));
    }

    @SuppressWarnings("null")
    private static int dumpBiomeClient(CommandSourceStack source) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return 1;
            }
            BlockPos pos = mc.player.blockPosition();
            SectionPos sec = SectionPos.of(pos);
            InfFarlands.LOGGER.info("BIODUMP client pos={},{},{} sec={},{},{}",
                    pos.getX(), pos.getY(), pos.getZ(), sec.x(), sec.y(), sec.z());
            FarLandsCommands.dumpBiomes(mc.level, sec);
            source.sendSuccess(() -> Component.translatable("commands.inf_farlands.client.section.biome.dump"), false);
        } catch (Exception e) {
            InfFarlands.LOGGER.error("BIODUMP client err", e);
        }
        return 1;
    }

    @SuppressWarnings("null")
    private static int dumpClient(CommandSourceStack source) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                return 1;
            }
            BlockPos pos = mc.player.blockPosition();
            SectionPos sec = SectionPos.of(pos);
            LevelLightEngine le = mc.level.getLightEngine();

            InfFarlands.LOGGER.info("FLDUMP client pos={},{},{} sec={},{},{}",
                    pos.getX(), pos.getY(), pos.getZ(), sec.x(), sec.y(), sec.z());
            FarLandsCommands.dumpLayer(le, LightLayer.SKY, sec);
            FarLandsCommands.dumpLayer(le, LightLayer.BLOCK, sec);
            FarLandsCommands.dumpBlocks(mc.level, sec);
            source.sendSuccess(() -> Component.translatable("commands.inf_farlands.client.section.light.dump"), false);
        } catch (Exception e) {
            InfFarlands.LOGGER.error("FLDUMP client err", e);
        }
        return 1;
    }
}
