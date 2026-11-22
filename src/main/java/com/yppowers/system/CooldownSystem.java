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
 * world-persisted long cooldowns, tracking both remaining and total duration for HUD progress bars.
 */
public final class CooldownSystem extends PersistentState {
    private static final class CooldownEntry {
        int remaining;
        int maxDuration;

        CooldownEntry(int remaining, int maxDuration) {
            this.remaining = remaining;
            this.maxDuration = Math.max(remaining, maxDuration);
        }
    }

    private final Map<UUID, Map<String, CooldownEntry>> persistent = new HashMap<>();
    private final Map<UUID, Map<String, CooldownEntry>> volatileMap = new HashMap<>();
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
        CooldownEntry p = persistent.getOrDefault(playerId, Collections.emptyMap()).get(key);
        CooldownEntry v = volatileMap.getOrDefault(playerId, Collections.emptyMap()).get(key);
        int pr = p == null ? 0 : p.remaining;
        int vr = v == null ? 0 : v.remaining;
        return Math.max(0, Math.max(pr, vr));
    }

    public int getMaxDuration(UUID playerId, String key) {
        if (playerId == null || key == null || key.isEmpty()) return 0;
        CooldownEntry p = persistent.getOrDefault(playerId, Collections.emptyMap()).get(key);
        CooldownEntry v = volatileMap.getOrDefault(playerId, Collections.emptyMap()).get(key);
        int pr = p == null ? 0 : p.remaining;
        int vr = v == null ? 0 : v.remaining;
        if (pr >= vr && p != null) return p.maxDuration;
        return v == null ? 0 : v.maxDuration;
    }

    public boolean isOnCooldown(UUID playerId, String key) {
        return getRemaining(playerId, key) > 0;
    }

    public void set(UUID playerId, String key, int ticks) {
        set(playerId, key, ticks, false);
    }

    public void setPersistent(UUID playerId, String key, int ticks) {
        set(playerId, key, ticks, true);
    }

    public void set(UUID playerId, String key, int ticks, boolean persist) {
        if (playerId == null || key == null || key.isEmpty()) return;
        Map<UUID, Map<String, CooldownEntry>> target = persist ? persistent : volatileMap;
        if (ticks <= 0) {
            clearKey(playerId, key);
            return;
        }
        target.computeIfAbsent(playerId, ignored -> new HashMap<>()).put(key, new CooldownEntry(ticks, ticks));
        if (persist) markDirty();
    }

    public void clearKey(UUID playerId, String key) {
        Map<String, CooldownEntry> pMap = persistent.get(playerId);
        if (pMap != null && pMap.remove(key) != null) {
            if (pMap.isEmpty()) persistent.remove(playerId);
            markDirty();
        }
        Map<String, CooldownEntry> vMap = volatileMap.get(playerId);
        if (vMap != null) {
            vMap.remove(key);
            if (vMap.isEmpty()) volatileMap.remove(playerId);
        }
    }

    public void clearAll(UUID playerId) {
        if (persistent.remove(playerId) != null) markDirty();
        volatileMap.remove(playerId);
    }

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

    private static boolean decrementMap(Map<UUID, Map<String, CooldownEntry>> outer) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, Map<String, CooldownEntry>>> outerIt = outer.entrySet().iterator();
        while (outerIt.hasNext()) {
            Map<String, CooldownEntry> inner = outerIt.next().getValue();
            Iterator<Map.Entry<String, CooldownEntry>> innerIt = inner.entrySet().iterator();
            while (innerIt.hasNext()) {
                CooldownEntry entry = innerIt.next().getValue();
                entry.remaining--;
                if (entry.remaining <= 0) innerIt.remove();
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
                if (v.remaining > 0) {
                    NbtCompound item = new NbtCompound();
                    item.putInt("R", v.remaining);
                    item.putInt("M", v.maxDuration);
                    p.put(k, item);
                }
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
                Map<String, CooldownEntry> map = new HashMap<>();
                for (String cdKey : p.getKeys()) {
                    NbtCompound item = p.getCompoundOrEmpty(cdKey);
                    int rem = item.getInt("R", p.getInt(cdKey, 0));
                    int max = item.getInt("M", rem);
                    if (rem > 0) map.put(cdKey, new CooldownEntry(rem, Math.max(rem, max)));
                }
                if (!map.isEmpty()) sys.persistent.put(uuid, map);
            } catch (IllegalArgumentException ignored) {}
        }
        return sys;
    }
}
