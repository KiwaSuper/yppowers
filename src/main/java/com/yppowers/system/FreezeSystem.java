package com.yppowers.system;

import com.yppowers.network.YPNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Single source of truth for entity freeze/stun states across all powers
 * (Time Stop, Serious Punch stun, Ice Emperor Absolute Zero / Ice Meteor, Poseidon Tsunami stun).
 */
public final class FreezeSystem {
    public enum Source {
        TIME_STOP(true, true),
        SERIOUS_PUNCH(false, false),
        ICE_EMPEROR(false, false),
        POSEIDON_TSUNAMI(false, false);

        public final boolean resetHurtInvuln;
        public final boolean applyFrozenVisualTint;

        Source(boolean resetHurtInvuln, boolean applyFrozenVisualTint) {
            this.resetHurtInvuln = resetHurtInvuln;
            this.applyFrozenVisualTint = applyFrozenVisualTint;
        }
    }

    private static final Map<UUID, EnumMap<Source, Integer>> FROZEN = new HashMap<>();
    private static final Map<UUID, Integer> SYNCED_PLAYER_ENTITY_IDS = new HashMap<>();

    private FreezeSystem() {}

    public static void freeze(Entity entity, Source source, int ticks) {
        if (entity == null || source == null || ticks <= 0) return;
        boolean hadVisual = hasVisualFreeze(entity.getUuid());
        EnumMap<Source, Integer> map = FROZEN.computeIfAbsent(entity.getUuid(), ignored -> new EnumMap<>(Source.class));
        map.merge(source, ticks, Math::max);

        if (entity instanceof ServerPlayerEntity player && !hadVisual && source.applyFrozenVisualTint) {
            SYNCED_PLAYER_ENTITY_IDS.put(player.getUuid(), player.getId());
            if (player.getEntityWorld().getServer() != null) {
                YPNetworking.broadcastTimeStopVisual(player.getEntityWorld().getServer(), player.getId(), true);
            }
        }
    }

    public static void unfreeze(Entity entity, Source source) {
        if (entity == null || source == null) return;
        unfreezeByUuid(entity.getUuid(), source, entity.getEntityWorld().getServer());
    }

    public static void unfreezeByUuid(UUID uuid, Source source, MinecraftServer server) {
        EnumMap<Source, Integer> map = FROZEN.get(uuid);
        if (map == null) return;
        boolean hadVisual = hasVisualFreeze(uuid);
        map.remove(source);
        if (map.isEmpty()) FROZEN.remove(uuid);
        if (hadVisual && !hasVisualFreeze(uuid)) {
            Integer entityId = SYNCED_PLAYER_ENTITY_IDS.remove(uuid);
            if (entityId != null && server != null) {
                YPNetworking.broadcastTimeStopVisual(server, entityId, false);
            }
        }
    }

    public static void clearSource(Source source, MinecraftServer server) {
        Iterator<Map.Entry<UUID, EnumMap<Source, Integer>>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, EnumMap<Source, Integer>> entry = it.next();
            UUID uuid = entry.getKey();
            boolean hadVisual = hasVisualFreeze(uuid);
            entry.getValue().remove(source);
            if (entry.getValue().isEmpty()) it.remove();
            if (hadVisual && !hasVisualFreeze(uuid)) {
                Integer entityId = SYNCED_PLAYER_ENTITY_IDS.remove(uuid);
                if (entityId != null && server != null) {
                    YPNetworking.broadcastTimeStopVisual(server, entityId, false);
                }
            }
        }
    }

    public static boolean isFrozen(Entity entity) {
        return entity != null && isFrozen(entity.getUuid());
    }

    public static boolean isFrozen(UUID uuid) {
        EnumMap<Source, Integer> map = FROZEN.get(uuid);
        return map != null && !map.isEmpty();
    }

    public static boolean isFrozenBy(Entity entity, Source source) {
        if (entity == null || source == null) return false;
        EnumMap<Source, Integer> map = FROZEN.get(entity.getUuid());
        return map != null && map.getOrDefault(source, 0) > 0;
    }

    public static boolean shouldResetHurtInvulnerability(LivingEntity entity) {
        if (entity == null) return false;
        EnumMap<Source, Integer> map = FROZEN.get(entity.getUuid());
        if (map == null) return false;
        for (Source src : map.keySet()) {
            if (src.resetHurtInvuln) return true;
        }
        return false;
    }

    private static boolean hasVisualFreeze(UUID uuid) {
        EnumMap<Source, Integer> map = FROZEN.get(uuid);
        if (map == null) return false;
        for (Source src : map.keySet()) {
            if (src.applyFrozenVisualTint) return true;
        }
        return false;
    }

    public static void serverTick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, EnumMap<Source, Integer>>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, EnumMap<Source, Integer>> entry = it.next();
            UUID uuid = entry.getKey();
            EnumMap<Source, Integer> map = entry.getValue();
            boolean hadVisual = hasVisualFreeze(uuid);

            Iterator<Map.Entry<Source, Integer>> srcIt = map.entrySet().iterator();
            while (srcIt.hasNext()) {
                Map.Entry<Source, Integer> srcEntry = srcIt.next();
                int next = srcEntry.getValue() - 1;
                if (next <= 0) srcIt.remove();
                else srcEntry.setValue(next);
            }

            if (map.isEmpty()) it.remove();
            if (hadVisual && !hasVisualFreeze(uuid)) {
                Integer entityId = SYNCED_PLAYER_ENTITY_IDS.remove(uuid);
                if (entityId != null && server != null) {
                    YPNetworking.broadcastTimeStopVisual(server, entityId, false);
                }
            }
        }
    }

    public static void syncVisualsTo(ServerPlayerEntity target) {
        for (int entityId : SYNCED_PLAYER_ENTITY_IDS.values()) {
            YPNetworking.sendTimeStopVisual(target, entityId, true);
        }
    }

    public static void clearAll() {
        FROZEN.clear();
        SYNCED_PLAYER_ENTITY_IDS.clear();
    }
}
