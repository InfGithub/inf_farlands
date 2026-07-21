package com.inf.farlands;

import com.inf.farlands.mixin.CompiledSectionAccessor;
import com.inf.farlands.mixin.LevelRendererAccessor;
import com.inf.farlands.mixin.ViewAreaAccessor;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = InfFarlands.MODID, value = Dist.CLIENT)
public class ClientCommandSetup {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("farlands")
                .then(Commands.literal("checkrender").executes(ctx -> {
                    doCheckRender();
                    return 1;
                }))
        );
    }

    private static void doCheckRender() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.levelRenderer == null || mc.player == null) {
            send("Renderer or player null");
            return;
        }
        LevelRendererAccessor accessor = (LevelRendererAccessor) mc.levelRenderer;
        ViewArea viewArea = accessor.getViewArea();
        if (viewArea == null) {
            send("ViewArea is null");
            return;
        }
        ViewAreaAccessor va = (ViewAreaAccessor) viewArea;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("viewDist=%d  gridSizeY=%d  sectionsLen=%d\n",
            viewArea.getViewDistance(), va.getSectionGridSizeY(), va.getSections().length));

        int px = mc.player.blockPosition().getX();
        int pz = mc.player.blockPosition().getZ();
        int minY = va.getLevel().getMinBuildHeight();

        for (int gy = 0; gy <= 15; gy++) {
            int originY = minY + gy * 16;
            BlockPos pos = new BlockPos(px, originY + 8, pz);
            SectionRenderDispatcher.RenderSection sec = va.callGetRenderSectionAt(pos);
            if (sec != null) {
                BlockPos o = sec.getOrigin();
                boolean compiled = sec.getCompiled() != null;
                int blockTypes = 0;
                if (compiled) blockTypes = ((CompiledSectionAccessor)(Object)sec.getCompiled()).getHasBlocks().size();
                sb.append(String.format("  gy=%d  origin=(%d,%d,%d)  compiled=%s  dirty=%s  hasNeighbors=%s  blockTypes=%d\n",
                    gy, o.getX(), o.getY(), o.getZ(),
                    compiled, sec.isDirty(), sec.hasAllNeighbors(), blockTypes));
            } else {
                sb.append(String.format("  gy=%d  -> NULL\n", gy));
            }
        }

        send(sb.toString());
    }

    private static void send(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(msg));
        }
    }
}
