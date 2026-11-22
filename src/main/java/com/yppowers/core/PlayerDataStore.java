package com.yppowers.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.yppowers.network.YPNetworking;
import com.yppowers.system.SoulboundItemSystem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Clean world-scoped persistent store for power assignments and per-power NBT state bags.
 * Each power reads/writes its own scoped {@link NbtCompound} rather than adding ad-hoc fields here.
 */
public final class PlayerDataStore extends PersistentState {
    private final Map<UUID, PowerId> assignments = new HashMap<>();
    private final Map<UUID, Map<PowerId, NbtCompound>> powerData = new HashMap<>();

    private static final Codec<PlayerDataStore> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
            },
            store -> new Dynamic<>(NbtOps.INSTANCE, store.toNbt())
    );

    private static final PersistentStateType<PlayerDataStore> STATE_TYPE = new PersistentStateType<>(
            "yppowers_v2_players", PlayerDataStore::new, CODEC, null
    );

    private PlayerDataStore() {}

    public static PlayerDataStore get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public PowerId getPowerId(UUID playerId) {
        return assignments.get(playerId);
    }

    public Power getPower(UUID playerId) {
        return PowerRegistry.get(assignments.get(playerId));
    }

    public Power getPower(ServerPlayerEntity player) {
        return player == null ? null : getPower(player.getUuid());
    }

    public boolean hasPower(UUID playerId, PowerId id) {
        return id != null && assignments.get(playerId) == id;
    }

    public boolean hasPower(ServerPlayerEntity player, PowerId id) {
        return player != null && hasPower(player.getUuid(), id);
    }

    public void assignPower(ServerPlayerEntity player, PowerId newId) {
        UUID uuid = player.getUuid();
        PowerId oldId = assignments.get(uuid);
        if (oldId != null) {
            Power oldPower = PowerRegistry.get(oldId);
            if (oldPower != null) oldPower.onRemove(player);
            SoulboundItemSystem.stripAllForPower(player, oldId);
        }

        if (newId == null) {
            assignments.remove(uuid);
        } else {
            assignments.put(uuid, newId);
            Power newPower = PowerRegistry.get(newId);
            if (newPower != null) newPower.onAssign(player);
        }

        markDirty();
        YPNetworking.syncPlayerFull(player);
    }

    public void clearPower(ServerPlayerEntity player) {
        assignPower(player, null);
    }

    /**
     * Returns the mutable persistent NBT bag scoped to {@code (playerId, powerId)}.
     * Call {@link #markDirty()} after mutating it.
     */
    public NbtCompound getPowerNbt(UUID playerId, PowerId powerId) {
        return powerData
                .computeIfAbsent(playerId, ignored -> new EnumMap<>(PowerId.class))
                .computeIfAbsent(powerId, ignored -> new NbtCompound());
    }

    public void clearPowerNbt(UUID playerId, PowerId powerId) {
        Map<PowerId, NbtCompound> map = powerData.get(playerId);
        if (map != null && map.remove(powerId) != null) {
            if (map.isEmpty()) powerData.remove(playerId);
            markDirty();
        }
    }

    private NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();

        NbtCompound assignedNbt = new NbtCompound();
        assignments.forEach((uuid, powerId) -> assignedNbt.putString(uuid.toString(), powerId.id()));
        root.put("Assignments", assignedNbt);

        NbtCompound dataNbt = new NbtCompound();
        powerData.forEach((uuid, perPower) -> {
            NbtCompound playerBag = new NbtCompound();
            perPower.forEach((powerId, compound) -> {
                if (!compound.isEmpty()) {
                    playerBag.put(powerId.id(), compound.copy());
                }
            });
            if (!playerBag.isEmpty()) {
                dataNbt.put(uuid.toString(), playerBag);
            }
        });
        root.put("PowerData", dataNbt);
        return root;
    }

    private static PlayerDataStore fromNbt(NbtCompound root) {
        PlayerDataStore store = new PlayerDataStore();

        NbtCompound assignedNbt = root.getCompoundOrEmpty("Assignments");
        for (String key : assignedNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                PowerId powerId = PowerId.fromId(assignedNbt.getString(key, ""));
                if (powerId != null) store.assignments.put(uuid, powerId);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs
            }
        }

        NbtCompound dataNbt = root.getCompoundOrEmpty("PowerData");
        for (String key : dataNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtCompound playerBag = dataNbt.getCompoundOrEmpty(key);
                Map<PowerId, NbtCompound> map = new EnumMap<>(PowerId.class);
                for (String powerKey : playerBag.getKeys()) {
                    PowerId powerId = PowerId.fromId(powerKey);
                    if (powerId != null) {
                        map.put(powerId, playerBag.getCompoundOrEmpty(powerKey).copy());
                    }
                }
                if (!map.isEmpty()) store.powerData.put(uuid, map);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUIDs
            }
        }
        return store;
    }
}
