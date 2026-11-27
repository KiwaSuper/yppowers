package com.yppowers.combat;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.network.YPNetworking;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Server-authoritative PvP combat timer preventing combat logging.
 * Tags both attacker and victim on PvP damage (direct hits, projectiles, lightning,
 * and summoned minions) while ignoring allies, PvE mobs, Creative/Spectator, and Grace.
 */
public final class CombatTagManager {
   private static final Map<UUID, Integer> TAGS = new HashMap<>();

   private CombatTagManager() {
   }

   public static boolean isTagged(UUID playerId) {
      return getRemainingTicks(playerId) > 0;
   }

   public static int getRemainingTicks(UUID playerId) {
      return playerId == null ? 0 : Math.max(0, TAGS.getOrDefault(playerId, 0));
   }

   public static int maxDurationTicks() {
      return Math.max(100, YPConfig.getInstance().combat_tag_duration_seconds * 20);
   }

   /** Pure helper for unit tests and internal tag refresh. Returns true if newly entered combat. */
   public static boolean tagPure(UUID playerId, int durationTicks) {
      if (playerId == null || durationTicks <= 0) {
         return false;
      }
      boolean newlyTagged = !isTagged(playerId);
      TAGS.put(playerId, durationTicks);
      return newlyTagged;
   }

   /** Pure tick helper for unit tests; returns true if the player just exited combat. */
   public static boolean tickPure(UUID playerId) {
      Integer cur = TAGS.get(playerId);
      if (cur == null) {
         return false;
      }
      if (cur <= 1) {
         TAGS.remove(playerId);
         return true;
      }
      TAGS.put(playerId, cur - 1);
      return false;
   }

   public static void clearTag(UUID playerId) {
      if (playerId != null) {
         TAGS.remove(playerId);
      }
   }

   public static void clearAll() {
      TAGS.clear();
   }

   public static void clearAllAndSync(MinecraftServer server) {
      if (TAGS.isEmpty()) {
         return;
      }
      for (UUID id : new ArrayList<>(TAGS.keySet())) {
         TAGS.remove(id);
         if (server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
               YPNetworking.syncHudState(player);
            }
         }
      }
   }

   /** Formats remaining ticks into a compact countdown string like "29s" or "1.4s". */
   public static String formatSeconds(int ticks) {
      if (ticks <= 0) {
         return "0s";
      }
      if (ticks < 200) {
         return String.format(java.util.Locale.ROOT, "%.1fs", ticks / 20.0);
      }
      return ((ticks + 19) / 20) + "s";
   }

   /** Resolves a player attacker from direct hits, projectiles, lightning, or owned minions. */
   public static ServerPlayerEntity resolvePlayerAttacker(MinecraftServer server, DamageSource source) {
      if (server == null || source == null) {
         return null;
      }
      ServerPlayerEntity direct = resolveEntity(server, source.getAttacker());
      return direct != null ? direct : resolveEntity(server, source.getSource());
   }

   private static ServerPlayerEntity resolveEntity(MinecraftServer server, Entity entity) {
      if (entity == null) {
         return null;
      }
      if (entity instanceof ServerPlayerEntity sp) {
         return sp;
      }
      if (entity instanceof ProjectileEntity proj) {
         ServerPlayerEntity resolved = resolveEntity(server, proj.getOwner());
         if (resolved != null) {
            return resolved;
         }
         String prefix = "yppowers_player_projectile_owner:";
         for (String tag : proj.getCommandTags()) {
            if (tag.startsWith(prefix)) {
               try {
                  return server.getPlayerManager().getPlayer(UUID.fromString(tag.substring(prefix.length())));
               } catch (IllegalArgumentException ignored) {
               }
            }
         }
         return null;
      }
      if (entity instanceof LightningEntity lightning) {
         return lightning.getChanneler();
      }
      if (entity instanceof MobEntity mob) {
         if (mob.getCommandTags().contains("yppowers_rogue")) {
            return null;
         }
         for (String tag : mob.getCommandTags()) {
            if (tag.startsWith("yppowers_owner_")) {
               try {
                  UUID ownerId = UUID.fromString(tag.substring("yppowers_owner_".length()));
                  return server.getPlayerManager().getPlayer(ownerId);
               } catch (IllegalArgumentException ignored) {
               }
            }
         }
      }
      return null;
   }

   /** Tags both attacker and victim for PvP combat when eligible. */
   public static void tagPvp(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
      YPConfig cfg = YPConfig.getInstance();
      if (!cfg.combat_tag_enabled || attacker == null || victim == null) {
         return;
      }
      if (attacker.getUuid().equals(victim.getUuid())) {
         return;
      }
      MinecraftServer server = victim.getEntityWorld().getServer();
      if (server == null || GraceManager.isActive(server)) {
         return;
      }
      if (AllianceManager.get(server).isAlly(attacker, victim)) {
         return;
      }
      if (victim.isCreative() || victim.isSpectator()) {
         return;
      }

      int ticks = maxDurationTicks();
      int seconds = cfg.combat_tag_duration_seconds;
      if (victim.isAlive()) {
         applyTag(victim, ticks, seconds);
      } else {
         TAGS.remove(victim.getUuid());
         YPNetworking.syncHudState(victim);
      }
      if (attacker.isAlive() && !attacker.isCreative() && !attacker.isSpectator()) {
         applyTag(attacker, ticks, seconds);
      }
   }

   private static void applyTag(ServerPlayerEntity player, int ticks, int seconds) {
      boolean newlyTagged = tagPure(player.getUuid(), ticks);
      if (newlyTagged) {
         player.sendMessage(
            Text.literal("In PvP combat (" + seconds + "s) — do not log out!").formatted(Formatting.RED, Formatting.BOLD),
            true
         );
      }
      YPNetworking.syncHudState(player);
   }

   /** Punishes combat logging on disconnect if the player is currently tagged. */
   public static void onPlayerDisconnect(ServerPlayerEntity player) {
      if (player == null) {
         return;
      }
      Integer remaining = TAGS.remove(player.getUuid());
      if (remaining == null || remaining <= 0) {
         return;
      }
      YPConfig cfg = YPConfig.getInstance();
      MinecraftServer server = player.getEntityWorld().getServer();
      if (!cfg.combat_tag_enabled || !cfg.combat_tag_kill_on_logout || server == null || GraceManager.isActive(server)) {
         return;
      }
      if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
         return;
      }
      if (player.getEntityWorld() instanceof ServerWorld world) {
         if (cfg.combat_tag_broadcast_logout) {
            server.getPlayerManager().broadcast(
               Text.literal(player.getName().getString() + " combat-logged and was slain!").formatted(Formatting.RED, Formatting.BOLD),
               false
            );
         }
         player.kill(world);
      }
   }

   public static void onPlayerDeath(ServerPlayerEntity player) {
      if (player != null && TAGS.remove(player.getUuid()) != null) {
         YPNetworking.syncHudState(player);
      }
   }

   public static void serverTick(MinecraftServer server) {
      if (TAGS.isEmpty()) {
         return;
      }
      if (GraceManager.isActive(server)) {
         clearAllAndSync(server);
         return;
      }
      boolean periodicSync = server.getTicks() % 5 == 0;
      Iterator<Map.Entry<UUID, Integer>> it = TAGS.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<UUID, Integer> entry = it.next();
         int next = entry.getValue() - 1;
         UUID id = entry.getKey();
         if (next <= 0) {
            it.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
               player.sendMessage(Text.literal("You are no longer in combat.").formatted(Formatting.GREEN), true);
               YPNetworking.syncHudState(player);
            }
         } else {
            entry.setValue(next);
            if (periodicSync) {
               ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
               if (player != null) {
                  YPNetworking.syncHudState(player);
               }
            }
         }
      }
   }
}
