package com.yppowers;

import com.yppowers.keybind.YPKeybinds;
import com.yppowers.client.YPClientNetworking;
import com.yppowers.client.ClientPowerData;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import com.yppowers.client.render.TimeCloneSkinManager;

@Environment(EnvType.CLIENT)
public class YPPowersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        YPKeybinds.register();
        YPClientNetworking.registerClientReceivers();
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TimeCloneSkinManager.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            YPKeybinds.tick();
            if (ClientPowerData.hudWarningTicks > 0) ClientPowerData.hudWarningTicks--;
            else ClientPowerData.hudWarningText = "";
        });
    }
}
