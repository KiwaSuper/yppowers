package com.yppowers.client;

import com.yppowers.network.YPNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class YPClientNetworking {
    private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Client Networking");

    private YPClientNetworking() {}

    public static void sendKeybindPacket(int keyType) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null
                || !ClientPlayNetworking.canSend(YPNetworking.KeybindPayload.ID)) return;
        ClientPlayNetworking.send(new YPNetworking.KeybindPayload(keyType));
    }

    public static void sendMorphRequest(String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        // A menu may remain open for a moment while disconnecting or changing servers.
        // Avoid sending custom payloads without a live play connection.
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) {
            if (client.player != null) {
                client.player.sendMessage(Text.literal("Cannot send the Shadow Archer request: no compatible server connection."), true);
            }
            return;
        }
        ClientPlayNetworking.send(new YPNetworking.MorphRequestPayload(name));
    }

    public static void registerClientReceivers() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientPowerData.hasFireGodPower = false;
            ClientPowerData.hasShadowArcherPower = false;
            ClientPowerData.unlockedShadowArcherMorphs.clear();
        });
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncFireGodPayload.ID,
                (payload, context) -> context.client().execute(() -> ClientPowerData.hasFireGodPower = payload.enabled()));
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncShadowArcherPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    ClientPowerData.hasShadowArcherPower = payload.enabled();
                    if (!payload.enabled()) ClientPowerData.unlockedShadowArcherMorphs.clear();
                    LOGGER.info("Shadow Archer ownership sync: {}", payload.enabled());
                }));
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncShadowArcherMorphCatalogPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientPowerData.unlockedShadowArcherMorphs.clear();
                    for (String raw : payload.encodedIds().split("\\n")) {
                        if (!raw.isBlank()) ClientPowerData.unlockedShadowArcherMorphs.add(raw.trim());
                    }
                    if (context.client().currentScreen instanceof ShadowArcherMorphScreen screen) {
                        screen.refreshUnlockStatus();
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.OpenMorphMenuPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> {
                // Ignore late packets during disconnects and duplicate open requests.
                if (client.player == null || client.world == null || client.getNetworkHandler() == null) return;
                if (client.currentScreen instanceof ShadowArcherMorphScreen) return;
                try {
                    client.setScreen(new ShadowArcherMorphScreen());
                    if (!(client.currentScreen instanceof ShadowArcherMorphScreen)) {
                        LOGGER.error("Shadow Archer menu payload arrived but the screen was not installed");
                        if (client.player != null) client.player.sendMessage(
                                Text.literal("Shadow Archer menu could not be opened. Check latest.log."), false);
                    } else {
                        LOGGER.info("Opened Shadow Archer morph menu");
                    }
                } catch (RuntimeException exception) {
                    // Keep a malformed/failed menu initialization from taking down the whole client.
                    LOGGER.error("Could not initialize the Shadow Archer morph menu", exception);
                    client.setScreen(null);
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("The Shadow Archer menu failed to open. See latest.log for details."), false);
                    }
                }
            });
        });
    }
}
