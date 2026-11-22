package com.yppowers.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * World-persisted bidirectional player alliance system.
 * Caches last-known player names so {@code /yp alliance list} works cleanly even for offline allies.
 */
public final class AllianceSystem extends PersistentState {
    public static final long INVITE_LIFETIME_TICKS = 1200L; // 60 seconds

    public record PendingInvite(UUID sender, UUID target, long expiresAtTick) {}

    private final Map<UUID, Set<UUID>> alliances = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();
    /** Ephemeral pending invites keyed by target UUID -> (sender UUID -> expiresAtTick). */
    private final Map<UUID, Map<UUID, Long>> pendingInvites = new HashMap<>();

    private static final Codec<AllianceSystem> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
            },
            sys -> new Dynamic<>(NbtOps.INSTANCE, sys.toNbt())
    );

    private static final PersistentStateType<AllianceSystem> STATE_TYPE = new PersistentStateType<>(
            "yppowers_v2_alliances", AllianceSystem::new, CODEC, null
    );

    public AllianceSystem() {}

    public static AllianceSystem get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public void rememberName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return;
        if (!name.equals(knownNames.get(uuid))) {
            knownNames.put(uuid, name);
            markDirty();
        }
    }

    public String getDisplayName(MinecraftServer server, UUID uuid) {
        if (server != null) {
            ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
            if (online != null) return online.getName().getString();
        }
        return knownNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    public boolean addAlliance(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        boolean addedA = alliances.computeIfAbsent(a, ignored -> new HashSet<>()).add(b);
        boolean addedB = alliances.computeIfAbsent(b, ignored -> new HashSet<>()).add(a);
        if (addedA || addedB) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean removeAlliance(UUID a, UUID b) {
        if (a == null || b == null) return false;
        boolean changed = false;
        Set<UUID> aSet = alliances.get(a);
        if (aSet != null) {
            changed |= aSet.remove(b);
            if (aSet.isEmpty()) alliances.remove(a);
        }
        Set<UUID> bSet = alliances.get(b);
        if (bSet != null) {
            changed |= bSet.remove(a);
            if (bSet.isEmpty()) alliances.remove(b);
        }
        if (changed) markDirty();
        return changed;
    }

    public boolean areAllied(UUID a, UUID b) {
        if (a == null || b == null) return false;
        if (a.equals(b)) return true;
        Set<UUID> set = alliances.get(a);
        return set != null && set.contains(b);
    }

    public boolean isAlly(ServerPlayerEntity p1, ServerPlayerEntity p2) {
        if (p1 == null || p2 == null) return false;
        return areAllied(p1.getUuid(), p2.getUuid());
    }

    public boolean isAlly(ServerPlayerEntity player, Entity entity) {
        return entity instanceof ServerPlayerEntity other && isAlly(player, other);
    }

    public Set<UUID> getAllies(UUID player) {
        Set<UUID> set = alliances.get(player);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(new HashSet<>(set));
    }

    // -------------------------------------------------------------------------
    // Consent-based Alliance Invites (prevents unilateral PvP force-ally exploit)
    // -------------------------------------------------------------------------

    public enum InviteResult { SENT, ALREADY_ALLIED, ALREADY_PENDING, SELF_INVITE }

    public InviteResult sendInvite(UUID sender, UUID target, long currentTick) {
        if (sender == null || target == null || sender.equals(target)) return InviteResult.SELF_INVITE;
        if (areAllied(sender, target)) return InviteResult.ALREADY_ALLIED;
        pruneExpiredInvites(target, currentTick);
        Map<UUID, Long> inbox = pendingInvites.computeIfAbsent(target, ignored -> new HashMap<>());
        if (inbox.containsKey(sender)) return InviteResult.ALREADY_PENDING;
        inbox.put(sender, currentTick + INVITE_LIFETIME_TICKS);
        return InviteResult.SENT;
    }

    public boolean hasPendingInvite(UUID sender, UUID target, long currentTick) {
        if (sender == null || target == null) return false;
        pruneExpiredInvites(target, currentTick);
        Map<UUID, Long> inbox = pendingInvites.get(target);
        return inbox != null && inbox.containsKey(sender);
    }

    public boolean acceptInvite(UUID target, UUID sender, long currentTick) {
        if (sender == null || target == null) return false;
        pruneExpiredInvites(target, currentTick);
        Map<UUID, Long> inbox = pendingInvites.get(target);
        if (inbox == null || inbox.remove(sender) == null) return false;
        if (inbox.isEmpty()) pendingInvites.remove(target);
        // Also clear any mutual opposite-direction invite
        Map<UUID, Long> reverse = pendingInvites.get(sender);
        if (reverse != null) {
            reverse.remove(target);
            if (reverse.isEmpty()) pendingInvites.remove(sender);
        }
        addAlliance(sender, target);
        return true;
    }

    public boolean declineInvite(UUID target, UUID sender) {
        if (sender == null || target == null) return false;
        Map<UUID, Long> inbox = pendingInvites.get(target);
        if (inbox == null || inbox.remove(sender) == null) return false;
        if (inbox.isEmpty()) pendingInvites.remove(target);
        return true;
    }

    private void pruneExpiredInvites(UUID target, long currentTick) {
        Map<UUID, Long> inbox = pendingInvites.get(target);
        if (inbox == null) return;
        inbox.entrySet().removeIf(e -> currentTick >= e.getValue());
        if (inbox.isEmpty()) pendingInvites.remove(target);
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        NbtCompound mapNbt = new NbtCompound();
        alliances.forEach((owner, allies) -> {
            NbtList list = new NbtList();
            for (UUID ally : allies) list.add(NbtString.of(ally.toString()));
            mapNbt.put(owner.toString(), list);
        });
        root.put("Alliances", mapNbt);

        NbtCompound namesNbt = new NbtCompound();
        knownNames.forEach((uuid, name) -> namesNbt.putString(uuid.toString(), name));
        root.put("Names", namesNbt);
        return root;
    }

    public static AllianceSystem fromNbt(NbtCompound root) {
        AllianceSystem sys = new AllianceSystem();
        NbtCompound mapNbt = root.getCompoundOrEmpty("Alliances");
        for (String key : mapNbt.getKeys()) {
            try {
                UUID owner = UUID.fromString(key);
                NbtList list = mapNbt.getListOrEmpty(key);
                for (int i = 0; i < list.size(); i++) {
                    String raw = list.getString(i, "");
                    try {
                        UUID ally = UUID.fromString(raw);
                        if (!owner.equals(ally)) {
                            sys.alliances.computeIfAbsent(owner, ignored -> new HashSet<>()).add(ally);
                            sys.alliances.computeIfAbsent(ally, ignored -> new HashSet<>()).add(owner);
                        }
                    } catch (IllegalArgumentException ignored) {}
                }
            } catch (IllegalArgumentException ignored) {}
        }
        NbtCompound namesNbt = root.getCompoundOrEmpty("Names");
        for (String key : namesNbt.getKeys()) {
            try {
                UUID id = UUID.fromString(key);
                String name = namesNbt.getString(key, "");
                if (!name.isBlank()) sys.knownNames.put(id, name);
            } catch (IllegalArgumentException ignored) {}
        }
        return sys;
    }
}
