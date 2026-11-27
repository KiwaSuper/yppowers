package com.yppowers.client;

import com.yppowers.client.render.GrimoireBookVisual;
import com.yppowers.client.render.VfxManager;
import com.yppowers.client.render.WitherAscendanceVisual;
import com.yppowers.network.YPNetworking;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public final class YPClientNetworking {
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers/Client Networking");

   private YPClientNetworking() {
   }

   public static void sendKeybindPacket(int keyType) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null && client.getNetworkHandler() != null && ClientPlayNetworking.canSend(YPNetworking.KeybindPayload.ID)) {
         ClientPlayNetworking.send(new YPNetworking.KeybindPayload(keyType));
      }
   }

   public static boolean sendFireGodBeamState(boolean active) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null
         && client.world != null
         && client.getNetworkHandler() != null
         && ClientPlayNetworking.canSend(YPNetworking.FireGodBeamPayload.ID)) {
         ClientPlayNetworking.send(new YPNetworking.FireGodBeamPayload(active));
         return true;
      } else {
         return false;
      }
   }

   public static boolean sendGrimoireBeamState(boolean active) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null
         && client.world != null
         && client.getNetworkHandler() != null
         && ClientPlayNetworking.canSend(YPNetworking.GrimoireBeamPayload.ID)) {
         ClientPlayNetworking.send(new YPNetworking.GrimoireBeamPayload(active));
         return true;
      } else {
         return false;
      }
   }

   public static boolean sendTimeWarperBlackHoleHoldState(boolean held) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null
         && client.world != null
         && client.getNetworkHandler() != null
         && ClientPlayNetworking.canSend(YPNetworking.TimeWarperBlackHoleHoldPayload.ID)) {
         ClientPlayNetworking.send(new YPNetworking.TimeWarperBlackHoleHoldPayload(held));
         return true;
      } else {
         return false;
      }
   }

   public static void openMorphMenu() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null && client.getNetworkHandler() != null) {
         if (!(client.currentScreen instanceof ShadowArcherMorphScreen)) {
            try {
               client.setScreen(new ShadowArcherMorphScreen());
               if (!(client.currentScreen instanceof ShadowArcherMorphScreen)) {
                  LOGGER.error("Shadow Archer menu was requested, but Minecraft did not keep the screen open");
                  if (client.player != null) {
                     client.player.sendMessage(Text.literal("Shadow Archer menu could not be opened. Check latest.log."), false);
                  }
               } else {
                  LOGGER.info("Opened Shadow Archer morph menu");
               }
            } catch (LinkageError | RuntimeException var2) {
               LOGGER.error("Could not initialize the Shadow Archer morph menu", var2);
               if (client.currentScreen instanceof ShadowArcherMorphScreen) {
                  client.setScreen(null);
               }

               if (client.player != null) {
                  client.player.sendMessage(Text.literal("The Shadow Archer menu failed to initialize. See latest.log for details."), false);
               }
            }
         }
      }
   }

   public static void sendMorphRequest(String name) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null && client.getNetworkHandler() != null) {
         ClientPlayNetworking.send(new YPNetworking.MorphRequestPayload(name));
      } else {
         if (client.player != null) {
            client.player.sendMessage(Text.literal("Cannot send the Shadow Archer request: no compatible server connection."), true);
         }
      }
   }

   public static void openPowerMenu() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null && client.getNetworkHandler() != null) {
         clearPowerMenuStatus();
         client.setScreen(new PowerMenuScreen());
         requestPowerMenuStatus();
      }
   }

   public static void requestPowerMenuStatus() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null
         && client.world != null
         && client.getNetworkHandler() != null
         && ClientPlayNetworking.canSend(YPNetworking.PowerMenuRequestPayload.ID)) {
         clearPowerMenuStatus();
         if (client.currentScreen instanceof PowerMenuScreen screen) {
            screen.refreshStatus();
         }

         if (client.currentScreen instanceof ShadowArcherMorphScreen screen) {
            screen.refreshArrowStatus();
         }

         ClientPlayNetworking.send(new YPNetworking.PowerMenuRequestPayload());
      }
   }

   private static void clearPowerMenuStatus() {
      ClientPowerData.powerMenuDataReady = false;
      ClientPowerData.powerMenuPowerId = "";
      ClientPowerData.powerMenuRitualStatus = "";
      ClientPowerData.powerMenuRitualComplete = false;
      ClientPowerData.powerMenuAwakeningUnlocked = false;
      ClientPowerData.powerMenuCarriesGrimoire = false;
      ClientPowerData.powerMenuAwakeningCooldownTicks = 0;
      ClientPowerData.powerMenuSelectedArrow = "";
      ClientPowerData.powerMenuAvailableArrows = List.of();
      ClientPowerData.powerMenuSelectedIceForm = "";
      ClientPowerData.powerMenuAvailableIceForms = List.of();
      ClientPowerData.powerMenuSelectedSpirit = "";
      ClientPowerData.powerMenuAvailableSpirits = List.of();
   }

   public static void sendPowerMenuAction(String action) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (action != null
         && action.length() <= 64
         && client.player != null
         && client.world != null
         && client.getNetworkHandler() != null
         && ClientPlayNetworking.canSend(YPNetworking.PowerMenuActionPayload.ID)) {
         ClientPlayNetworking.send(new YPNetworking.PowerMenuActionPayload(action));
      }
   }

   public static void confirmPoseidonSink() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null
         && client.world != null
         && client.getNetworkHandler() != null
         && ClientPlayNetworking.canSend(YPNetworking.PoseidonSinkConfirmPayload.ID)) {
         ClientPlayNetworking.send(new YPNetworking.PoseidonSinkConfirmPayload());
      }
   }

   public static boolean sendTeleportGambitStructure(String structureId) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (structureId != null
         && structureId.length() <= 128
         && client.player != null
         && client.world != null
         && client.getNetworkHandler() != null
         && ClientPlayNetworking.canSend(YPNetworking.TeleportGambitStructureRequestPayload.ID)) {
         ClientPlayNetworking.send(new YPNetworking.TeleportGambitStructureRequestPayload(structureId));
         return true;
      } else {
         return false;
      }
   }

   private static void openTeleportGambitStructureMenu(String structureIds, int cooldownTicks) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null) {
         client.setScreen(new TeleportGambitStructureScreen(structureIds, cooldownTicks));
      }
   }

   public static void registerClientReceivers() {
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> {
         ClientPowerData.hasFireGodPower = false;
         ClientPowerData.hasShadowArcherPower = false;
         ClientPowerData.grimoireSkillsetEnabled = false;
         ClientPowerData.timeDomainActive = false;
         ClientPowerData.timeDomainOwner = false;
         ClientPowerData.shadowArcherMorphed = false;
         ClientPowerData.witherAscendantEntityIds.clear();
         WitherAscendanceVisual.clear();
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
         ClientPowerData.assignedPowerId = "";
         ClientPowerData.graceActive = false;
         ClientPowerData.combatTicks = 0;
         ClientPowerData.maxCombatTicks = 600;
         ClientPowerData.iceEmperorForms.clear();
         ClientPowerData.powerMenuDataReady = false;
         ClientPowerData.powerMenuPowerId = "";
         ClientPowerData.powerMenuRitualStatus = "";
         ClientPowerData.powerMenuRitualComplete = false;
         ClientPowerData.powerMenuAwakeningUnlocked = false;
         ClientPowerData.powerMenuCarriesGrimoire = false;
         ClientPowerData.powerMenuAwakeningCooldownTicks = 0;
         ClientPowerData.powerMenuSelectedArrow = "";
         ClientPowerData.powerMenuAvailableArrows = List.of();
         ClientPowerData.powerMenuSelectedIceForm = "";
         ClientPowerData.powerMenuAvailableIceForms = List.of();
         ClientPowerData.powerMenuSelectedSpirit = "";
         ClientPowerData.powerMenuAvailableSpirits = List.of();
      });
      ClientPlayNetworking.registerGlobalReceiver(
         YPNetworking.SyncFireGodPayload.ID, (payload, context) -> context.client().execute(() -> ClientPowerData.hasFireGodPower = payload.enabled())
      );
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncWitherAscendancePayload.ID, (payload, context) -> context.client().execute(() -> {
         if (payload.active()) {
            ClientPowerData.witherAscendantEntityIds.add(payload.entityId());
         } else {
            ClientPowerData.witherAscendantEntityIds.remove(payload.entityId());
            WitherAscendanceVisual.clear(payload.entityId());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         YPNetworking.SyncGrimoireModePayload.ID,
         (payload, context) -> context.client().execute(() -> ClientPowerData.grimoireSkillsetEnabled = payload.active())
      );
      ClientPlayNetworking.registerGlobalReceiver(
         YPNetworking.SyncGrimoireBookPayload.ID,
         (payload, context) -> context.client()
            .execute(() -> GrimoireBookVisual.setActive(context.client(), payload.entityId(), payload.active(), payload.durationTicks()))
      );
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncShadowArcherPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.hasShadowArcherPower = payload.enabled();
         if (!payload.enabled()) {
            ClientPowerData.unlockedShadowArcherMorphs.clear();
         }

         LOGGER.info("Shadow Archer ownership sync: {}", payload.enabled());
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncShadowArcherMorphCatalogPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.unlockedShadowArcherMorphs.clear();

         for (String raw : payload.encodedIds().split("\\n")) {
            if (!raw.isBlank()) {
               ClientPowerData.unlockedShadowArcherMorphs.add(raw.trim());
            }
         }

         if (context.client().currentScreen instanceof ShadowArcherMorphScreen screen) {
            screen.refreshUnlockStatus();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncShadowArcherMorphViewPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.shadowArcherMorphed = payload.morphed();
         ClientPowerData.shadowArcherDisguiseEntityId = payload.morphed() ? payload.disguiseEntityId() : -1;
         ClientPowerData.shadowArcherMorphEyeHeight = Math.max(0.1F, Math.min(8.0F, payload.eyeHeight()));
         ClientPowerData.shadowArcherMorphHeight = Math.max(0.1F, Math.min(16.0F, payload.entityHeight()));
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.OpenMorphMenuPayload.ID, (payload, context) -> {
         MinecraftClient client = context.client();
         client.execute(() -> openMorphMenu());
      });
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncTimeDomainViewPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.timeDomainActive = payload.active();
         ClientPowerData.timeDomainOwner = payload.active() && payload.owner();
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncTimeStopVisualPayload.ID, (payload, context) -> context.client().execute(() -> {
         if (payload.frozen()) {
            ClientPowerData.timeStoppedPlayerEntityIds.add(payload.entityId());
         } else {
            ClientPowerData.timeStoppedPlayerEntityIds.remove(payload.entityId());
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncIceEmperorFormPayload.ID, (payload, context) -> context.client().execute(() -> {
         try {
            UUID owner = UUID.fromString(payload.playerUuid());
            if (payload.active() && Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(payload.form())) {
               ClientPowerData.iceEmperorForms.put(owner, payload.form());
            } else {
               ClientPowerData.iceEmperorForms.remove(owner);
            }
         } catch (IllegalArgumentException var2) {
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncEnergyPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.energy = Math.max(0.0, payload.energy());
         ClientPowerData.maxEnergy = Math.max(1.0, payload.maxEnergy());
         ClientPowerData.bleedingTicks = Math.max(0, payload.bleedingTicks());
         ClientPowerData.globalHealthEnabled = payload.globalHealthEnabled();
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncHudWarningPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.hudWarningText = payload.text();
         ClientPowerData.hudWarningTicks = Math.max(0, Math.min(200, payload.ticks()));
      }));
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncHudStatePayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.assignedPowerId = payload.powerId();
         ClientPowerData.graceActive = payload.graceActive();
         ClientPowerData.combatTicks = Math.max(0, payload.combatTicks());
         ClientPowerData.maxCombatTicks = Math.max(1, payload.maxCombatTicks());
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         YPNetworking.OpenPowerMenuPayload.ID, (payload, context) -> context.client().execute(YPClientNetworking::openPowerMenu)
      );
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.SyncPowerMenuPayload.ID, (payload, context) -> context.client().execute(() -> {
         ClientPowerData.powerMenuDataReady = true;
         ClientPowerData.powerMenuPowerId = payload.powerId();
         ClientPowerData.powerMenuRitualStatus = payload.ritualStatus();
         ClientPowerData.powerMenuRitualComplete = payload.ritualComplete();
         ClientPowerData.powerMenuAwakeningUnlocked = payload.awakeningUnlocked();
         ClientPowerData.powerMenuCarriesGrimoire = payload.carriesGrimoire();
         ClientPowerData.powerMenuAwakeningCooldownTicks = Math.max(0, Math.min(63072000, payload.awakeningCooldownTicks()));
         ClientPowerData.powerMenuSelectedArrow = payload.selectedArrow();
         ClientPowerData.powerMenuAvailableArrows = decodeOptions(payload.availableArrows());
         ClientPowerData.powerMenuSelectedIceForm = payload.selectedIceForm();
         ClientPowerData.powerMenuAvailableIceForms = decodeOptions(payload.availableIceForms());
         ClientPowerData.powerMenuSelectedSpirit = payload.selectedSpirit();
         ClientPowerData.powerMenuAvailableSpirits = decodeOptions(payload.availableSpirits());
         if (context.client().currentScreen instanceof PowerMenuScreen screen) {
            screen.refreshStatus();
         }

         if (context.client().currentScreen instanceof ShadowArcherMorphScreen screen) {
            screen.refreshArrowStatus();
         }
      }));
      ClientPlayNetworking.registerGlobalReceiver(
         YPNetworking.OpenPoseidonConfirmPayload.ID,
         (payload, context) -> context.client()
            .execute(
               () -> context.client()
                  .setScreen(new PoseidonConfirmScreen(Math.max(64, Math.min(512, payload.radius())), Math.max(0, Math.min(4096, payload.loadedChunks()))))
            )
      );
      ClientPlayNetworking.registerGlobalReceiver(
         YPNetworking.OpenTeleportGambitStructuresPayload.ID,
         (payload, context) -> context.client().execute(() -> openTeleportGambitStructureMenu(payload.structureIds(), Math.max(0, payload.cooldownTicks())))
      );
      ClientPlayNetworking.registerGlobalReceiver(YPNetworking.VfxEvent.ID, (payload, context) -> context.client().execute(() -> VfxManager.accept(payload)));
      ClientPlayNetworking.registerGlobalReceiver(
         YPNetworking.VfxClearPayload.ID, (payload, context) -> context.client().execute(() -> VfxManager.clearSource(payload.sourceEntityId()))
      );
   }

   private static List<String> decodeOptions(String encoded) {
      return encoded != null && !encoded.isBlank()
         ? Arrays.stream(encoded.split("\\n")).map(String::trim).filter(value -> !value.isEmpty() && value.length() <= 24).distinct().limit(16L).toList()
         : List.of();
   }
}
