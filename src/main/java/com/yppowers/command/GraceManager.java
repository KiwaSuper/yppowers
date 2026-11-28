package com.yppowers.command;

import com.yppowers.power.PowerManager;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class GraceManager {
   private static final long MESSAGE_INTERVAL_TICKS = 20L;
   private static final Map<UUID, Long> LAST_ABILITY_NOTICE = new HashMap<>();
   private static final Map<UUID, Long> LAST_PVP_NOTICE = new HashMap<>();

   private GraceManager() {
   }

   public static boolean isActive(MinecraftServer server) {
      return server != null && PowerManager.get(server).isGracePeriodActive();
   }

   public static boolean blockAbility(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (!isActive(server)) {
         return false;
      } else {
         sendThrottled(player, LAST_ABILITY_NOTICE, Text.literal("Abilities are disabled during the grace period.").formatted(Formatting.RED));
         return true;
      }
   }

   public static void notifyPvpBlocked(ServerPlayerEntity player) {
      if (player != null && isActive(player.getEntityWorld().getServer())) {
         sendThrottled(player, LAST_PVP_NOTICE, Text.literal("PvP is disabled during the grace period.").formatted(Formatting.RED));
      }
   }

   private static void sendThrottled(ServerPlayerEntity player, Map<UUID, Long> lastNotices, Text message) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         long now = server.getTicks();
         UUID id = player.getUuid();
         Long previous = lastNotices.get(id);
         if (previous == null || now - previous >= 20L) {
            player.sendMessage(message, true);
            lastNotices.put(id, now);
         }

         if (lastNotices.size() > 1024) {
            Iterator<Entry<UUID, Long>> iterator = lastNotices.entrySet().iterator();

            while (iterator.hasNext()) {
               if (now - iterator.next().getValue() > 1200L) {
                  iterator.remove();
               }
            }
         }
      }
   }

   public static void clearRuntimeState() {
      LAST_ABILITY_NOTICE.clear();
      LAST_PVP_NOTICE.clear();
   }

   public static ServerPlayerEntity findPlayerAttacker(DamageSource source) {
      ServerPlayerEntity player = findPlayerAttacker(source.getAttacker());
      return player != null ? player : findPlayerAttacker(source.getSource());
   }

   public static boolean isPlayerOwnedSummon(DamageSource source) {
      return isPlayerOwnedSummonEntity(source.getAttacker()) || isPlayerOwnedSummonEntity(source.getSource());
   }

   public static UUID findSummonOwnerId(Entity entity) {
      if (entity == null) {
         return null;
      }
      if (entity instanceof ProjectileEntity projectile && projectile.getOwner() != null && projectile.getOwner() != entity) {
         UUID ownerId = findSummonOwnerId(projectile.getOwner());
         if (ownerId != null) {
            return ownerId;
         }
      }
      for (String tag : entity.getCommandTags()) {
         if (tag.startsWith("yppowers_owner_")) {
            try {
               return UUID.fromString(tag.substring("yppowers_owner_".length()));
            } catch (IllegalArgumentException ignored) {
            }
         }
      }
      return null;
   }

   private static boolean isPlayerOwnedSummonEntity(Entity entity) {
      if (entity == null) {
         return false;
      }
      if (entity.getCommandTags().contains("yppowers_ice_guard_rogue")) {
         return true;
      }
      return findSummonOwnerId(entity) != null;
   }

   private static ServerPlayerEntity findPlayerAttacker(Entity entity) {
      if (entity instanceof ServerPlayerEntity player) {
         return player;
      } else if (entity instanceof ProjectileEntity projectile) {
         if (projectile.getOwner() instanceof ServerPlayerEntity player) {
            return player;
         }
         UUID summonOwner = findSummonOwnerId(projectile);
         if (summonOwner != null && entity.getEntityWorld().getServer() != null) {
            return entity.getEntityWorld().getServer().getPlayerManager().getPlayer(summonOwner);
         }
         return null;
      } else if (entity instanceof LightningEntity lightning) {
         return lightning.getChanneler();
      } else if (entity != null && entity.getEntityWorld().getServer() != null) {
         UUID summonOwner = findSummonOwnerId(entity);
         return summonOwner != null ? entity.getEntityWorld().getServer().getPlayerManager().getPlayer(summonOwner) : null;
      } else {
         return null;
      }
   }
}
