package com.yppowers.alliance;

import net.minecraft.entity.Entity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.world.PersistentStateType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AllianceManager extends PersistentState {
    private final Map<UUID, Set<UUID>> alliances = new HashMap<>();

    /** Codec wrapper keeps saved alliance data readable while adopting 1.21.11 persistence. */
    private static final Codec<AllianceManager> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
            },
            manager -> new Dynamic<>(NbtOps.INSTANCE, manager.toNbt())
    );

    private static final PersistentStateType<AllianceManager> STATE_TYPE = new PersistentStateType<>(
            "yppowers_alliances", AllianceManager::new, CODEC, null
    );

    public void addAlliance(UUID a, UUID b) {
        alliances.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        alliances.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        this.markDirty();
    }

    public void removeAlliance(UUID a, UUID b) {
        Set<UUID> aAllies = alliances.get(a);
        if (aAllies != null) {
            aAllies.remove(b);
            if (aAllies.isEmpty()) alliances.remove(a);
        }
        
        Set<UUID> bAllies = alliances.get(b);
        if (bAllies != null) {
            bAllies.remove(a);
            if (bAllies.isEmpty()) alliances.remove(b);
        }
        this.markDirty();
    }

    public boolean areAllied(UUID a, UUID b) {
        Set<UUID> aAllies = alliances.get(a);
        return aAllies != null && aAllies.contains(b);
    }

    public Set<UUID> getAllies(UUID player) {
        return new HashSet<>(alliances.getOrDefault(player, new HashSet<>()));
    }

    public boolean isAlly(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1 == p2 || p1.getUuid().equals(p2.getUuid())) return true;
        return areAllied(p1.getUuid(), p2.getUuid());
    }

    /** Returns true only for an allied player; non-player entities are never allies. */
    public boolean isAlly(ServerPlayerEntity player, Entity entity) {
        return entity instanceof ServerPlayerEntity other && isAlly(player, other);
    }

    private NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        NbtCompound alliancesNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<UUID>> entry : alliances.entrySet()) {
            NbtList list = new NbtList();
            for (UUID ally : entry.getValue()) {
                list.add(NbtString.of(ally.toString()));
            }
            alliancesNbt.put(entry.getKey().toString(), list);
        }
        nbt.put("Alliances", alliancesNbt);
        return nbt;
    }

    public static AllianceManager fromNbt(NbtCompound nbt) {
        AllianceManager manager = new AllianceManager();
        NbtCompound alliancesNbt = nbt.getCompoundOrEmpty("Alliances");
        for (String key : alliancesNbt.getKeys()) {
            UUID player;
            try {
                player = UUID.fromString(key);
            } catch (IllegalArgumentException ignored) {
                // Skip malformed legacy keys so one bad entry cannot break world loading.
                continue;
            }
            NbtList list = alliancesNbt.getListOrEmpty(key);
            Set<UUID> allies = new HashSet<>();
            for (int i = 0; i < list.size(); i++) {
                String allyId = list.getString(i, "");
                try {
                    allies.add(UUID.fromString(allyId));
                } catch (IllegalArgumentException ignored) {
                    // Skip malformed old data rather than preventing the state from loading.
                }
            }
            manager.alliances.put(player, allies);
        }
        return manager;
    }

    public static AllianceManager get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }
}
