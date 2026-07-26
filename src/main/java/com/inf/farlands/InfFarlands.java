package com.inf.farlands;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
// import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import com.inf.farlands.network.FarLandsSectionBlocksUpdatePacket;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

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
        modBus.addListener(this::registerPayloads);
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        // NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter % TRIM_INTERVAL == 0)
            HashUtil.trimLookups(tickCounter);
    }

    // private void onClientTick(ClientTickEvent.Post event) {
    //     if (++tickCounter % TRIM_INTERVAL == 0)
    //         HashUtil.trimLookups(tickCounter);
    // }

    @SuppressWarnings("null")
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                FarLandsSectionBlocksUpdatePacket.TYPE,
                FarLandsSectionBlocksUpdatePacket.STREAM_CODEC,
                (payload, context) -> {
                    ClientLevel level = Minecraft.getInstance().level;
                    if (level != null) {
                        payload.runUpdates((pos, state) -> level.setServerVerifiedBlockState(pos, state, 19));
                    }
                });
    }

}