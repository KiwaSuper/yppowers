package com.yppowers.system;

import com.yppowers.config.CombatConfig;
import com.yppowers.config.YPConfig;
import com.yppowers.network.YPNetworking;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative PvP combat timer preventing combat logging.
 * Tags both attacker and victim on player-vs-player damage (including projectiles,
 * lightning, and summoned minions) while ignoring allies, PvE mobs, and Grace.
 */
public final class CombatTagSystem {
    private static final Map<UUID, Integer> TAGS = new HashMap<>();

    private CombatTagSystem() {}

    public static boolean isTagged(UUID playerId) {
        return getRemainingTicks(playerId) > 0;
    }

    public static int getRemainingTicks(UUID playerId) {
        return playerId == null ? 0 : Math.max(0, TAGS.getOrDefault(playerId, 0));
    }

    /** Pure helper for unit tests and internal tag refresh. Returns true if newly entered combat. */
    public static boolean tagPure(UUID playerId, int durationTicks) {
        if (playerId == null || durationTicks <= 0) return false;
        boolean newlyTagged = !isTagged(playerId);
        TAGS.put(playerId, durationTicks);
        return newlyTagged;
    }

    /** Pure tick helper for unit tests; returns true if the player just exited combat. */
    public static boolean tickPure(UUID playerId) {
        Integer cur = TAGS.get(playerId);
        if (cur == null) return false;
        if (cur <= 1) {
            TAGS.remove(playerId);
            return true;
        }
        TAGS.put(playerId, cur - 1);
        return false;
    }

    public static void clearTag(UUID playerId) {
        if (playerId != null) TAGS.remove(playerId);
    }

    public static void clearAll() {
        TAGS.clear();
    }

    /** Resolves a player attacker from direct hits, projectiles, lightning, or non-rogue summoned minions. */
    public static ServerPlayerEntity resolvePlayerAttacker(MinecraftServer server, DamageSource source) {
        if (server == null || source == null) return null;
        ServerPlayerEntity direct = resolveEntity(server, source.getAttacker());
        return direct != null ? direct : resolveEntity(server, source.getSource());
    }

    private static ServerPlayerEntity resolveEntity(MinecraftServer server, Entity entity) {
        if (entity == null) return null;
        if (entity instanceof ServerPlayerEntity sp) return sp;
        if (entity instanceof ProjectileEntity proj) {
            return resolveEntity(server, proj.getOwner());
        }
        if (entity instanceof LightningEntity lightning) {
            return lightning.getChanneler();
        }
        if (entity instanceof MobEntity mob && !SummonSystem.isRogue(mob)) {
            UUID ownerId = SummonSystem.getOwnerUuid(mob);
            if (ownerId != null) return server.getPlayerManager().getPlayer(ownerId);
        }
        return null;
    }

    /** Tags both attacker and victim for PvP combat when eligible. */
    public static void tagPvp(ServerPlayerEntity attacker, ServerPlayerEntity victim) {
        CombatConfig cfg = YPConfig.get().combat;
        if (!cfg.enabled || attacker == null || victim == null) return;
        if (attacker.getUuid().equals(victim.getUuid())) return;
        MinecraftServer server = victim.getEntityWorld().getServer();
        if (server == null || GraceSystem.isActive(server)) return;
        if (AllianceSystem.get(server).isAlly(attacker, victim)) return;
        if (victim.isCreative() || victim.isSpectator()) return;

        int ticks = cfg.durationTicks();
        if (victim.isAlive()) {
            applyTag(victim, ticks, cfg.durationSeconds);
        } else {
            TAGS.remove(victim.getUuid());
            YPNetworking.syncHud(victim);
        }
        if (attacker.isAlive() && !attacker.isCreative() && !attacker.isSpectator()) {
            applyTag(attacker, ticks, cfg.durationSeconds);
        }
    }

    private static void applyTag(ServerPlayerEntity player, int ticks, int seconds) {
        boolean newlyTagged = tagPure(player.getUuid(), ticks);
        if (newlyTagged) {
            player.sendMessage(Text.literal("In PvP combat (" + seconds + "s) — do not log out!")
                    .formatted(Formatting.RED, Formatting.BOLD), true);
        }
        YPNetworking.syncHud(player);
    }

    /** Punishes combat logging on disconnect if the player is currently tagged. */
    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        if (player == null) return;
        Integer remaining = TAGS.remove(player.getUuid());
        if (remaining == null || remaining <= 0) return;

        CombatConfig cfg = YPConfig.get().combat;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (!cfg.enabled || !cfg.killOnLogout || server == null || GraceSystem.isActive(server)) return;
        if (!player.isAlive() || player.isCreative() || player.isSpectator()) return;

        if (player.getEntityWorld() instanceof ServerWorld world) {
            if (cfg.broadcastCombatLog) {
                server.getPlayerManager().broadcast(
                        Text.literal(player.getName().getString() + " combat-logged and was slain!")
                                .formatted(Formatting.RED, Formatting.BOLD),
                        false
                );
            }
            player.kill(world);
        }
    }

    public static void onPlayerDeath(ServerPlayerEntity player) {
        if (player != null && TAGS.remove(player.getUuid()) != null) {
            YPNetworking.syncHud(player);
        }
    }

    public static void serverTick(MinecraftServer server) {
        if (TAGS.isEmpty()) return;
        if (GraceSystem.isActive(server)) {
            for (UUID id : new java.util.ArrayList<>(TAGS.keySet())) {
                TAGS.remove(id);
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player != null) YPNetworking.syncHud(player);
            }
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = TAGS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
                UUID id = entry.getKey();
                it.remove();
                ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
                if (player != null) {
                    player.sendMessage(Text.literal("You are no longer in combat.").formatted(Formatting.GREEN), true);
                    YPNetworking.syncHud(player);
                }
            } else {
                entry.setValue(next);
            }
        }
    }
}
