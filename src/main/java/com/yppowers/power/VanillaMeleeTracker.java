package com.yppowers.power;

import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Marks only damage calculated from the vanilla PlayerEntity.attack call path.
 * Scripted powers sometimes intentionally use DamageTypes.PLAYER_ATTACK too, so
 * a damage-source check by itself is not enough to classify ordinary melee hits.
 */
public final class VanillaMeleeTracker {
    private static final ThreadLocal<Map<UUID, Integer>> ACTIVE_ATTACKS =
            ThreadLocal.withInitial(HashMap::new);

    private VanillaMeleeTracker() {}

    public static void begin(ServerPlayerEntity player) {
        if (player == null) return;
        ACTIVE_ATTACKS.get().merge(player.getUuid(), 1, Integer::sum);
    }

    public static void end(ServerPlayerEntity player) {
        if (player == null) return;
        Map<UUID, Integer> active = ACTIVE_ATTACKS.get();
        UUID id = player.getUuid();
        Integer depth = active.get(id);
        if (depth == null || depth <= 1) active.remove(id);
        else active.put(id, depth - 1);
        if (active.isEmpty()) ACTIVE_ATTACKS.remove();
    }

    public static boolean isActive(ServerPlayerEntity player) {
        return player != null && ACTIVE_ATTACKS.get().containsKey(player.getUuid());
    }
}
