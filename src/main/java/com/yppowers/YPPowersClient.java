package com.yppowers;

import com.yppowers.client.ClientState;
import com.yppowers.client.YPClientNetworking;
import com.yppowers.client.YPKeybinds;
import com.yppowers.client.input.ShadowArcherClientInput;
import com.yppowers.client.render.TimeCloneSkinManager;
import com.yppowers.core.PowerId;
import com.yppowers.item.ThorMaceHook;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

@Environment(EnvType.CLIENT)
public final class YPPowersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        YPKeybinds.register();
        YPClientNetworking.registerClientReceivers();

        ThorMaceHook.setClientCanUsePredicate(() -> ClientState.hasPower(PowerId.THOR) && !ClientState.hud.graceActive());

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.reset();
            TimeCloneSkinManager.clear();
        });

        ClientTickEvents.START_CLIENT_TICK.register(ShadowArcherClientInput::tick);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            YPKeybinds.tick();
            ClientState.tickClient();
        });
    }
}
