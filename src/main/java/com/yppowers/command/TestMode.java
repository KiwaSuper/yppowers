package com.yppowers.command;

import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.AwakeningManager;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public final class TestMode {
   private static final Set<UUID> TEST_PLAYERS = ConcurrentHashMap.newKeySet();

   private TestMode() {
   }

   public static boolean isEnabled(UUID playerId) {
      return playerId != null && TEST_PLAYERS.contains(playerId);
   }

   public static boolean isEnabled(ServerPlayerEntity player) {
      return player != null && isEnabled(player.getUuid());
   }

   public static boolean toggle(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (TEST_PLAYERS.remove(id)) {
         return false;
      } else {
         TEST_PLAYERS.add(id);
         return true;
      }
   }

   public static void set(ServerPlayerEntity player, boolean enabled) {
      if (player != null) {
         if (enabled) {
            TEST_PLAYERS.add(player.getUuid());
         } else {
            TEST_PLAYERS.remove(player.getUuid());
         }
      }
   }

   public static void clearAll() {
      TEST_PLAYERS.clear();
   }

   public static void serverTick(MinecraftServer server) {
      if (server != null && !TEST_PLAYERS.isEmpty()) {
         EnergyManager energy = EnergyManager.get(server);
         PowerManager powers = PowerManager.get(server);
         YPConfig config = YPConfig.getInstance();
         long tick = server.getTicks();

         for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID id = player.getUuid();
            if (TEST_PLAYERS.contains(id)) {
               PowerType type = powers.getPowerType(id);
               if (type != null) {
                  Power power = powers.getPowerInstance(type);
                  if (power != null) {
                     power.clearCooldowns(id);
                  }
               }

               powers.clearCooldownsFor(id);
               energy.clearCooldownsFor(id);
               AwakeningManager.clearCooldowns(id);
               if (energy.getEnergy(id) + 1.0E-6 < config.energy_max) {
                  energy.setEnergy(id, config.energy_max);
               }

               if (!player.getMainHandStack().isEmpty()) {
                  player.getItemCooldownManager().set(player.getMainHandStack(), 0);
               }

               if (!player.getOffHandStack().isEmpty()) {
                  player.getItemCooldownManager().set(player.getOffHandStack(), 0);
               }

               if (tick % 5L == 0L) {
                  energy.sync(player);
               }
            }
         }
      }
   }
}
