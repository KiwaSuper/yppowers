package com.yppowers;

import com.yppowers.keybind.YPKeybinds;
import com.yppowers.client.YPClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

@Environment(EnvType.CLIENT)
public class YPPowersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        YPKeybinds.register();
        YPClientNetworking.registerClientReceivers();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            YPKeybinds.tick();
        });
    }
}
