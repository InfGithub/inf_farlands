package com.inf.farlands.client;

import com.inf.farlands.HashUtil;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "inf_farlands", value = Dist.CLIENT)
public class ClientLightTrimmer {

    private static int tickCounter = 0;
    private static final int INTERVAL = 200;

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter % INTERVAL != 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        int px = mc.player.blockPosition().getX() >> 4;
        int pz = mc.player.blockPosition().getZ() >> 4;
        int range = mc.options.renderDistance().get() + 2;

        HashUtil.sectionLookup.values().removeIf(sp -> Math.abs(sp.x - px) > range || Math.abs(sp.z - pz) > range);
    }
}
