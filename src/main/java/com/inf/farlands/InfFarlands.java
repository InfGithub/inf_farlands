package com.inf.farlands;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod("inf_farlands")
public class InfFarlands {
    public static final String MODID = "inf_farlands";

    public static final Logger LOGGER = LoggerFactory.getLogger(InfFarlands.class);

    public InfFarlands(IEventBus modBus, ModContainer container) {
        LOGGER.info("Welcome to Inf's Farlands.");
    }
}