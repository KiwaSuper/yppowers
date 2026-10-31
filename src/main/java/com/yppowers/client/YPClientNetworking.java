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

    public static void openMorphMenu() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return;
        if (client.currentScreen instanceof ShadowArcherMorphScreen) return;
        try {
            client.setScreen(new ShadowArcherMorphScreen());
            if (!(client.currentScreen instanceof ShadowArcherMorphScreen)) {
                LOGGER.error("Shadow Archer menu was requested, but Minecraft did not keep the screen open");
                if (client.player != null) client.player.sendMessage(
                        Text.literal("Shadow Archer menu could not be opened. Check latest.log."), false);
            } else {
                LOGGER.info("Opened Shadow Archer morph menu");
            }
        } catch (RuntimeException | LinkageError exception) {
            LOGGER.error("Could not initialize the Shadow Archer morph menu", exception);
            if (client.currentScreen instanceof ShadowArcherMorphScreen) client.setScreen(null);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("The Shadow Archer menu failed to initialize. See latest.log for details."), false);
            }
        }
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
            ClientPowerData.shadowArcherMorphed = false;
            ClientPowerData.shadowArcherDisguiseEntityId = -1;
            ClientPowerData.shadowArcherMorphEyeHeight = 1.62F;
            ClientPowerData.shadowArcherMorphHeight = 1.8F;
            ClientPowerData.unlockedShadowArcherMorphs.clear();
            ClientPowerData.timeStoppedPlayerEntityIds.clear();
            ClientPowerData.energy = 100.0;
            ClientPowerData.maxEnergy = 100.0;
            ClientPowerData.bleedingTicks = 0;
            ClientPowerData.globalHealthEnabled = true;
            ClientPowerData.hudWarningText = "";
            ClientPowerData.hudWarningTicks = 0;
            ClientPowerData.iceEmperorForms.clear();
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
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncShadowArcherMorphViewPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientPowerData.shadowArcherMorphed = payload.morphed();
                    ClientPowerData.shadowArcherDisguiseEntityId = payload.morphed() ? payload.disguiseEntityId() : -1;
                    ClientPowerData.shadowArcherMorphEyeHeight = Math.max(0.1F, Math.min(8.0F, payload.eyeHeight()));
                    ClientPowerData.shadowArcherMorphHeight = Math.max(0.1F, Math.min(16.0F, payload.entityHeight()));
                }));
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.OpenMorphMenuPayload.ID, (payload, context) -> {
            MinecraftClient client = context.client();
            client.execute(() -> openMorphMenu());
        });
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncTimeStopVisualPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    if (payload.frozen()) ClientPowerData.timeStoppedPlayerEntityIds.add(payload.entityId());
                    else ClientPowerData.timeStoppedPlayerEntityIds.remove(payload.entityId());
                }));
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncIceEmperorFormPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    try {
                        java.util.UUID owner = java.util.UUID.fromString(payload.playerUuid());
                        if (payload.active() && java.util.Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(payload.form())) {
                            ClientPowerData.iceEmperorForms.put(owner, payload.form());
                        } else {
                            ClientPowerData.iceEmperorForms.remove(owner);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Ignore malformed cosmetic state packets.
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncEnergyPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientPowerData.energy = Math.max(0.0, payload.energy());
                    ClientPowerData.maxEnergy = Math.max(1.0, payload.maxEnergy());
                    ClientPowerData.bleedingTicks = Math.max(0, payload.bleedingTicks());
                    ClientPowerData.globalHealthEnabled = payload.globalHealthEnabled();
                }));
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncHudWarningPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientPowerData.hudWarningText = payload.text();
                    ClientPowerData.hudWarningTicks = Math.max(0, Math.min(200, payload.ticks()));
                }));
    }
}
