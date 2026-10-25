package com.yppowers;

import com.yppowers.keybind.YPKeybinds;
import com.yppowers.client.YPClientNetworking;
import com.yppowers.entity.YPEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;

@Environment(EnvType.CLIENT)
public class YPPowersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        YPKeybinds.register();
        YPClientNetworking.registerClientReceivers();

        EntityRendererRegistry.register(YPEntities.THOR_MACE, FlyingItemEntityRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            YPKeybinds.tick();
        });
    }
}
