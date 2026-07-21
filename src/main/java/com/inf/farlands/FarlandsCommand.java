package com.inf.farlands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.world.level.border.WorldBorder;


@EventBusSubscriber(modid = InfFarlands.MODID)
public class FarlandsCommand {

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("farlands")
                .executes(ctx -> { showInfo(ctx.getSource()); return 1; })
                .then(Commands.literal("border")
                    .then(Commands.literal("set").then(
                        Commands.argument("size", DoubleArgumentType.doubleArg(1.0))
                            .executes(ctx -> { setBorder(ctx.getSource(), DoubleArgumentType.getDouble(ctx, "size")); return 1; })
                    ))
                    .then(Commands.literal("get").executes(ctx -> { getBorder(ctx.getSource()); return 1; }))
                )
                .then(Commands.literal("tp").then(
                    Commands.argument("x", DoubleArgumentType.doubleArg()).then(
                        Commands.argument("y", DoubleArgumentType.doubleArg()).then(
                            Commands.argument("z", DoubleArgumentType.doubleArg())
                                .executes(ctx -> {
                                    teleport(ctx.getSource(),
                                        DoubleArgumentType.getDouble(ctx, "x"),
                                        DoubleArgumentType.getDouble(ctx, "y"),
                                        DoubleArgumentType.getDouble(ctx, "z"));
                                    return 1;
                                })
                        )
                    )
                ))
                .then(Commands.literal("dump").executes(ctx -> { dump(ctx.getSource()); return 1; }))
        );
    }

    private static void showInfo(CommandSourceStack src) {
        WorldBorder border = src.getLevel().getWorldBorder();
        src.sendSuccess(() -> Component.literal(
            "Config: betaTerrain=" + Config.betaTerrain +
            " borderAbsoluteMax=" + Config.borderAbsoluteMax +
            "\nWorldBorder: size=" + border.getSize() +
            " absMax=" + border.getAbsoluteMaxSize() +
            " minX=" + border.getMinX() + " maxX=" + border.getMaxX()
        ), false);
    }

    private static void setBorder(CommandSourceStack src, double size) {
        src.getLevel().getWorldBorder().setSize(size);
        src.sendSuccess(() -> Component.translatable("commands.worldborder.set", String.format("%.0f", size)), true);
    }

    private static void getBorder(CommandSourceStack src) {
        WorldBorder border = src.getLevel().getWorldBorder();
        src.sendSuccess(() -> Component.translatable("commands.worldborder.get", String.format("%.0f", border.getSize())), false);
    }

    private static void teleport(CommandSourceStack src, double x, double y, double z) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        src.sendSuccess(() -> Component.translatable("commands.teleport.success.location.single",
            player.getDisplayName(), String.format("%.2f", x), String.format("%.2f", y), String.format("%.2f", z)), true);
    }

    private static void dump(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        WorldBorder border = player.serverLevel().getWorldBorder();
        src.sendSuccess(() -> Component.literal(
            "Player: x=" + player.getX() + " y=" + player.getY() + " z=" + player.getZ() +
            "\nWorldBorder: size=" + border.getSize() + " absMax=" + border.getAbsoluteMaxSize() +
            "\n  minX=" + border.getMinX() + " maxX=" + border.getMaxX() +
            "\n  minZ=" + border.getMinZ() + " maxZ=" + border.getMaxZ()
        ), false);
    }


}
