package com.yppowers.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Unified named cooldown manager supporting both short volatile cooldowns and
 * world-persisted long cooldowns (e.g. Overdrive, Black Hole, Raigo, Domain, Heat Blast lockout).
 */
public final class CooldownSystem extends PersistentState {
    private final Map<UUID, Map<String, Integer>> persistent = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> volatileMap = new HashMap<>();
    private long lastSaveTick = -20;

    private static final Codec<CooldownSystem> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
            },
            sys -> new Dynamic<>(NbtOps.INSTANCE, sys.toNbt())
    );

    private static final PersistentStateType<CooldownSystem> STATE_TYPE = new PersistentStateType<>(
            "yppowers_v2_cooldowns", CooldownSystem::new, CODEC, null
    );

    public CooldownSystem() {}

    public static CooldownSystem get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public int getRemaining(UUID playerId, String key) {
        if (playerId == null || key == null || key.isEmpty()) return 0;
        int p = persistent.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(key, 0);
        int v = volatileMap.getOrDefault(playerId, Collections.emptyMap()).getOrDefault(key, 0);
        return Math.max(0, Math.max(p, v));
    }

    public boolean isOnCooldown(UUID playerId, String key) {
        return getRemaining(playerId, key) > 0;
    }

    /** Set a volatile (session-scoped) cooldown in ticks. */
    public void set(UUID playerId, String key, int ticks) {
        set(playerId, key, ticks, false);
    }

    /** Set a persistent (survives restart/disconnect) cooldown in ticks. */
    public void setPersistent(UUID playerId, String key, int ticks) {
        set(playerId, key, ticks, true);
    }

    public void set(UUID playerId, String key, int ticks, boolean persist) {
        if (playerId == null || key == null || key.isEmpty()) return;
        Map<UUID, Map<String, Integer>> target = persist ? persistent : volatileMap;
        if (ticks <= 0) {
            clearKey(playerId, key);
            return;
        }
        target.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(key, ticks);
        if (persist) markDirty();
    }

    public void clearKey(UUID playerId, String key) {
        Map<String, Integer> pMap = persistent.get(playerId);
        if (pMap != null && pMap.remove(key) != null) {
            if (pMap.isEmpty()) persistent.remove(playerId);
            markDirty();
        }
        Map<String, Integer> vMap = volatileMap.get(playerId);
        if (vMap != null) {
            vMap.remove(key);
            if (vMap.isEmpty()) volatileMap.remove(playerId);
        }
    }

    public void clearAll(UUID playerId) {
        if (persistent.remove(playerId) != null) markDirty();
        volatileMap.remove(playerId);
    }

    /** Checks cooldown and sends a clean action-bar notice if blocked. Returns true if ready. */
    public boolean checkReady(ServerPlayerEntity player, String key, String displayName) {
        int remaining = getRemaining(player.getUuid(), key);
        if (remaining > 0) {
            player.sendMessage(Text.literal(displayName + " cooldown: " + formatSeconds(remaining))
                    .formatted(Formatting.YELLOW), true);
            return false;
        }
        return true;
    }

    public static String formatSeconds(int ticks) {
        int totalSec = (Math.max(0, ticks) + 19) / 20;
        if (totalSec >= 60) {
            int mins = totalSec / 60;
            int secs = totalSec % 60;
            return secs == 0 ? mins + "m" : mins + "m " + secs + "s";
        }
        return totalSec + "s";
    }

    public void tickAll(long currentServerTick) {
        boolean persistentChanged = decrementMap(persistent);
        decrementMap(volatileMap);
        if (persistentChanged && currentServerTick - lastSaveTick >= 20L) {
            lastSaveTick = currentServerTick;
            markDirty();
        }
    }

    private static boolean decrementMap(Map<UUID, Map<String, Integer>> outer) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Map<String, Integer>>> outerIt = outer.entrySet().iterator();
        while (outerIt.hasNext()) {
            Map<String, Integer> inner = outerIt.next().getValue();
            Iterator<Map.Entry<String, Integer>> innerIt = inner.entrySet().iterator();
            while (innerIt.hasNext()) {
                Map.Entry<String, Integer> entry = innerIt.next();
                int next = entry.getValue() - 1;
                if (next <= 0) innerIt.remove();
                else entry.setValue(next);
                changed = true;
            }
            if (inner.isEmpty()) outerIt.remove();
        }
        return changed;
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        NbtCompound playersNbt = new NbtCompound();
        persistent.forEach((uuid, map) -> {
            if (map.isEmpty()) return;
            NbtCompound p = new NbtCompound();
            map.forEach((k, v) -> {
                if (v > 0) p.putInt(k, v);
            });
            if (!p.isEmpty()) playersNbt.put(uuid.toString(), p);
        });
        root.put("Players", playersNbt);
        return root;
    }

    public static CooldownSystem fromNbt(NbtCompound root) {
        CooldownSystem sys = new CooldownSystem();
        NbtCompound playersNbt = root.getCompoundOrEmpty("Players");
        for (String uuidKey : playersNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                NbtCompound p = playersNbt.getCompoundOrEmpty(uuidKey);
                Map<String, Integer> map = new HashMap<>();
                for (String cdKey : p.getKeys()) {
                    int ticks = p.getInt(cdKey, 0);
                    if (ticks > 0) map.put(cdKey, ticks);
                }
                if (!map.isEmpty()) sys.persistent.put(uuid, map);
            } catch (IllegalArgumentException ignored) {}
        }
        return sys;
    }
}
