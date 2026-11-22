package com.yppowers.client;

import com.yppowers.client.screen.ShadowArcherMorphScreen;
import com.yppowers.client.screen.YPPowersGuideScreen;
import com.yppowers.core.AbilitySlot;
import com.yppowers.network.YPNetworking;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/** Client-side CustomPayload receivers and senders. */
@Environment(EnvType.CLIENT)
public final class YPClientNetworking {
    private YPClientNetworking() {}

    public static void registerClientReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncHudPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> ClientState.hud = payload.snapshot()));

        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncHudWarningPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    ClientState.hudWarningText = payload.text();
                    ClientState.hudWarningTicks = payload.ticks();
                }));

        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncTimeStopVisualPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    if (payload.frozen()) ClientState.timeStoppedPlayerIds.add(payload.entityId());
                    else ClientState.timeStoppedPlayerIds.remove(payload.entityId());
                }));

        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncMorphCatalogPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    ClientState.unlockedMorphs.clear();
                    String raw = payload.encodedIds();
                    if (raw != null && !raw.isBlank()) {
                        for (String part : raw.split(",")) {
                            if (!part.isBlank()) ClientState.unlockedMorphs.add(part.trim());
                        }
                    }
                }));

        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncMorphViewPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    ClientState.shadowArcherMorphed = payload.morphed();
                    ClientState.shadowArcherDisguiseEntityId = payload.disguiseEntityId();
                    ClientState.shadowArcherMorphEyeHeight = payload.eyeHeight();
                    ClientState.shadowArcherMorphHeight = payload.entityHeight();
                    ClientState.shadowArcherMorphMobId = payload.mobId();
                }));

        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncIceEmperorFormPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    try {
                        UUID id = UUID.fromString(payload.playerUuid());
                        if (payload.active() && !payload.form().isBlank()) ClientState.iceEmperorForms.put(id, payload.form());
                        else ClientState.iceEmperorForms.remove(id);
                    } catch (IllegalArgumentException ignored) {}
                }));

        ClientPlayNetworking.registerGlobalReceiver(YPNetworking.OpenGuideScreenPayload.ID, (payload, ctx) ->
                ctx.client().execute(YPClientNetworking::openGuideScreen));
    }

    public static void sendTrigger(AbilitySlot slot) {
        if (slot != null && ClientPlayNetworking.canSend(YPNetworking.AbilityTriggerPayload.ID)) {
            ClientPlayNetworking.send(new YPNetworking.AbilityTriggerPayload(slot.wireId()));
        }
    }

    public static void sendMorphRequest(String mobId) {
        if (ClientPlayNetworking.canSend(YPNetworking.MorphRequestPayload.ID)) {
            ClientPlayNetworking.send(new YPNetworking.MorphRequestPayload(mobId == null ? "" : mobId));
        }
    }

    public static void openMorphScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new ShadowArcherMorphScreen());
    }

    public static void openGuideScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.setScreen(new YPPowersGuideScreen());
    }
}
