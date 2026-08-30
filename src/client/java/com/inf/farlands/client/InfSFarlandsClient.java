package com.inf.farlands.client;

import net.fabricmc.api.ClientModInitializer;
import com.inf.farlands.client.register.FarlandsRegister;

public class InfSFarlandsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		FarlandsRegister.register();
	}
}