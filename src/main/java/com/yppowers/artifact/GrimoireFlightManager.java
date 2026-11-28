package com.yppowers.artifact;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.bootssky.BootsOfTheSkyPower;
import com.yppowers.power.illusionist.IllusionistPower;
import com.yppowers.power.shadowarcher.ShadowArcherPower;
import com.yppowers.progression.AwakeningManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class GrimoireFlightManager {
   private static final Map<UUID, Float> BOOK_GRANTED_FLIGHT = new HashMap<>();

   private GrimoireFlightManager() {
   }

   public static boolean isFlightGranted(UUID playerId) {
      return playerId != null && BOOK_GRANTED_FLIGHT.containsKey(playerId);
   }

   public static boolean hasAnyActiveModFlight(ServerPlayerEntity player, PowerManager data) {
      return isFlightGranted(player.getUuid()) || hasOtherActiveFlight(player, data);
   }

   public static void serverTick(MinecraftServer server) {
      PowerManager data = PowerManager.get(server);
      YPConfig config = YPConfig.getInstance();
      boolean grace = GraceManager.isActive(server);

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         updateFlight(player, data, config, !grace && GrimoireOfLightManager.hasGrimoire(player) && config.grimoire_flight_enabled);
      }
   }

   private static void updateFlight(ServerPlayerEntity player, PowerManager data, YPConfig config, boolean shouldFly) {
      if (!player.isCreative() && !player.isSpectator()) {
         UUID playerId = player.getUuid();
         Float previousFlySpeed = BOOK_GRANTED_FLIGHT.get(playerId);
         if (shouldFly) {
            if (previousFlySpeed == null) {
               previousFlySpeed = player.getAbilities().getFlySpeed();
               BOOK_GRANTED_FLIGHT.put(playerId, previousFlySpeed);
            }

            boolean changed = false;
            if (!player.getAbilities().allowFlying) {
               player.getAbilities().allowFlying = true;
               changed = true;
            }

            if (Math.abs(player.getAbilities().getFlySpeed() - config.grimoire_flight_speed) > 1.0E-4F) {
               player.getAbilities().setFlySpeed(config.grimoire_flight_speed);
               changed = true;
            }

            if (changed) {
               player.sendAbilitiesUpdate();
            }
         } else if (previousFlySpeed != null) {
            BOOK_GRANTED_FLIGHT.remove(playerId);
            if (hasOtherActiveFlight(player, data)) {
               if (Math.abs(player.getAbilities().getFlySpeed() - config.grimoire_flight_speed) < 1.0E-4F) {
                  player.getAbilities().setFlySpeed(previousFlySpeed);
                  player.sendAbilitiesUpdate();
               }
            } else {
               boolean changedx = false;
               if (player.getAbilities().allowFlying) {
                  player.getAbilities().allowFlying = false;
                  player.getAbilities().flying = false;
                  changedx = true;
               }

               if (Math.abs(player.getAbilities().getFlySpeed() - config.grimoire_flight_speed) < 1.0E-4F) {
                  player.getAbilities().setFlySpeed(previousFlySpeed);
                  changedx = true;
               }

               if (changedx) {
                  player.sendAbilitiesUpdate();
               }
            }
         }
      }
   }

   public static boolean hasOtherActiveFlight(ServerPlayerEntity player, PowerManager data) {
      UUID id = player.getUuid();
      if (AwakeningManager.isWitherForm(id)) {
         return true;
      }
      PowerType type = data.getAssignedPowerType(id);
      Power power = type == null ? null : data.getPowerInstance(type);
      if (type == PowerType.BOOTS_SKY && power instanceof BootsOfTheSkyPower boots) {
         return boots.isFlightActive(id);
      } else if (type == PowerType.ILLUSIONIST && power instanceof IllusionistPower illusionist) {
         return illusionist.isCloudFlightActive(id);
      } else {
         return type == PowerType.SHADOW_ARCHER && power instanceof ShadowArcherPower archer && archer.isInFlyingMorph(id);
      }
   }

   public static void onPlayerDisconnect(ServerPlayerEntity player, MinecraftServer server) {
      updateFlight(player, PowerManager.get(server), YPConfig.getInstance(), false);
      BOOK_GRANTED_FLIGHT.remove(player.getUuid());
   }

   public static void shutdown(MinecraftServer server) {
      if (server != null) {
         PowerManager data = PowerManager.get(server);
         YPConfig config = YPConfig.getInstance();
         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            updateFlight(player, data, config, false);
         }
      }
      BOOK_GRANTED_FLIGHT.clear();
   }

   public static void clearRuntimeState() {
      BOOK_GRANTED_FLIGHT.clear();
   }
}
