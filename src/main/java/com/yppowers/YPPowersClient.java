package com.yppowers;

import com.yppowers.client.ClientPowerData;
import com.yppowers.client.ShadowArcherClientInput;
import com.yppowers.client.YPClientNetworking;
import com.yppowers.client.render.GrimoireBookVisual;
import com.yppowers.client.render.TimeCloneSkinManager;
import com.yppowers.client.render.VfxManager;
import com.yppowers.client.render.VfxParticleFactories;
import com.yppowers.keybind.YPKeybinds;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;

@Environment(EnvType.CLIENT)
public class YPPowersClient implements ClientModInitializer {
   @Override
   public void onInitializeClient() {
      YPKeybinds.register();
      YPClientNetworking.registerClientReceivers();
      VfxParticleFactories.register();
      GrimoireBookVisual.register();
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         TimeCloneSkinManager.clear();
         GrimoireBookVisual.clear();
         VfxManager.clear();
      });
      ClientTickEvents.START_CLIENT_TICK.register(ShadowArcherClientInput::tick);
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         VfxManager.tick(client);
         YPKeybinds.tick();
         if (ClientPowerData.hudWarningTicks > 0) {
            ClientPowerData.hudWarningTicks--;
         } else {
            ClientPowerData.hudWarningText = "";
         }
      });
   }
}
