package com.inf.farlands;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("inf_farlands")
public class InfFarlands {
    public static final String MODID = "inf_farlands";

    public static final Logger LOGGER = LoggerFactory.getLogger(InfFarlands.class);

    // --------------------------------

    private static volatile int tickCounter = 0;
    private static final int TRIM_INTERVAL = 200;

    public static long getServerTickCount() {
        return tickCounter;
    }

    public InfFarlands(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % TRIM_INTERVAL == 0)
            HashUtil.trimLookups(tickCounter);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        if (++tickCounter % TRIM_INTERVAL == 0)
            HashUtil.trimLookups(tickCounter);
    }
}