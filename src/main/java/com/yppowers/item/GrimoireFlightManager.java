package com.yppowers.item;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.BootsOfTheSkyPower;
import com.yppowers.power.impl.IllusionistPower;
import com.yppowers.power.impl.ShadowArcherPower;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class GrimoireFlightManager {
   private static final Map<UUID, Float> BOOK_GRANTED_FLIGHT = new HashMap<>();

   private GrimoireFlightManager() {
   }

   public static void serverTick(MinecraftServer server) {
      PowerManager data = PowerManager.get(server);
      YPConfig config = YPConfig.getInstance();

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         updateFlight(player, data, config, GrimoireOfLightManager.hasGrimoire(player) && config.grimoire_flight_enabled);
      }
   }

   private static void updateFlight(ServerPlayerEntity player, PowerManager data, YPConfig config, boolean shouldFly) {
      if (!player.isCreative() && !player.isSpectator()) {
         UUID playerId = player.getUuid();
         Float previousFlySpeed = BOOK_GRANTED_FLIGHT.get(playerId);
         if (shouldFly) {
            if (previousFlySpeed == null && !player.getAbilities().allowFlying) {
               previousFlySpeed = player.getAbilities().getFlySpeed();
               BOOK_GRANTED_FLIGHT.put(playerId, previousFlySpeed);
            }

            if (previousFlySpeed != null) {
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

   private static boolean hasOtherActiveFlight(ServerPlayerEntity player, PowerManager data) {
      PowerType type = data.getAssignedPowerType(player.getUuid());
      Power power = type == null ? null : data.getPowerInstance(type);
      if (type == PowerType.BOOTS_SKY && power instanceof BootsOfTheSkyPower boots) {
         return boots.isFlightActive(player.getUuid());
      } else {
         return type == PowerType.ILLUSIONIST && power instanceof IllusionistPower illusionist
            ? illusionist.isCloudFlightActive(player.getUuid())
            : type == PowerType.SHADOW_ARCHER && power instanceof ShadowArcherPower archer && archer.isInFlyingMorph(player.getUuid());
      }
   }

   public static void onPlayerDisconnect(ServerPlayerEntity player, MinecraftServer server) {
      updateFlight(player, PowerManager.get(server), YPConfig.getInstance(), false);
      BOOK_GRANTED_FLIGHT.remove(player.getUuid());
   }

   public static void clearRuntimeState() {
      BOOK_GRANTED_FLIGHT.clear();
   }
}
