package com.yppowers.command;

import com.yppowers.power.PowerManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-side grace-period checks shared by commands, keybinds, and combat hooks. */
public final class GraceManager {
    private static final long MESSAGE_INTERVAL_TICKS = 20L;
    private static final Map<UUID, Long> LAST_ABILITY_NOTICE = new HashMap<>();
    private static final Map<UUID, Long> LAST_PVP_NOTICE = new HashMap<>();

    private GraceManager() {}

    public static boolean isActive(MinecraftServer server) {
        return server != null && PowerManager.get(server).isGracePeriodActive();
    }

    /** Returns true when the attempted power ability must be denied. */
    public static boolean blockAbility(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (!isActive(server)) return false;
        sendThrottled(player, LAST_ABILITY_NOTICE,
                Text.literal("Abilities are disabled during the grace period.").formatted(Formatting.RED));
        return true;
    }

    public static void notifyPvpBlocked(ServerPlayerEntity player) {
        if (player == null || !isActive(player.getEntityWorld().getServer())) return;
        sendThrottled(player, LAST_PVP_NOTICE,
                Text.literal("PvP is disabled during the grace period.").formatted(Formatting.RED));
    }

    private static void sendThrottled(ServerPlayerEntity player, Map<UUID, Long> lastNotices, Text message) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        long now = server.getTicks();
        UUID id = player.getUuid();
        Long previous = lastNotices.get(id);
        if (previous == null || now - previous >= MESSAGE_INTERVAL_TICKS) {
            player.sendMessage(message, true);
            lastNotices.put(id, now);
        }
        if (lastNotices.size() > 1024) {
            Iterator<Map.Entry<UUID, Long>> iterator = lastNotices.entrySet().iterator();
            while (iterator.hasNext()) {
                if (now - iterator.next().getValue() > 1200L) iterator.remove();
            }
        }
    }

    /** Finds the player responsible for a direct, projectile, or player-channelled lightning hit. */
    public static ServerPlayerEntity findPlayerAttacker(DamageSource source) {
        ServerPlayerEntity player = findPlayerAttacker(source.getAttacker());
        return player != null ? player : findPlayerAttacker(source.getSource());
    }

    private static ServerPlayerEntity findPlayerAttacker(Entity entity) {
        if (entity instanceof ServerPlayerEntity player) return player;
        if (entity instanceof ProjectileEntity projectile && projectile.getOwner() instanceof ServerPlayerEntity player) {
            return player;
        }
        if (entity instanceof LightningEntity lightning) return lightning.getChanneler();
        return null;
    }
}
