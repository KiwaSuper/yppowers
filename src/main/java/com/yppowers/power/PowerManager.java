package com.yppowers.power;

import com.yppowers.network.YPNetworking;
import com.yppowers.power.impl.ArrowType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

public class PowerManager extends PersistentState {
    private final Map<UUID, PowerType> playerPowers = new HashMap<>();
    /** Shadow Archer kill unlocks are stored by registry ID so they survive reconnects and server restarts. */
    private final Map<UUID, Set<String>> shadowArcherKills = new HashMap<>();
    /** Special arrows unlocked by each player, persisted across reconnects and server restarts. */
    private final Map<UUID, Set<String>> shadowArcherUnlockedArrows = new HashMap<>();
    /** Permanent Spirit Channeler forms unlocked by kills and ingredient collection. */
    private final Map<UUID, Set<String>> spiritChannelerUnlocks = new HashMap<>();
    /** Unbounded ordinary Time Shard counters; Golden Shards remain physical inventory items. */
    private final Map<UUID, Integer> timeShardCounts = new HashMap<>();
    /** Clones suspended while their owner is offline, with lifespan paused until restoration. */
    private final Map<UUID, List<NbtCompound>> storedTimeClones = new HashMap<>();
    private final Map<UUID, String> timeWarperForms = new HashMap<>();
    private final Map<UUID, Integer> timeWarperFormCooldowns = new HashMap<>();
    /** Selected Ice Emperor form, persisted by player UUID across reconnects and restarts. */
    private final Map<UUID, String> iceEmperorForms = new HashMap<>();
    /** Powers/passive kits explicitly toggled off by their owners (Ice Emperor and GOAT). */
    private final Map<UUID, Set<String>> disabledPowers = new HashMap<>();
    /** UUIDs who explicitly disabled Feather Falling on their soulbound Boots of the Sky. */
    private final Set<UUID> bootsSkyFeatherFallingDisabled = new HashSet<>();
    /** Persistent owner/entity/dimension/marker metadata for an away Thor hammer. */
    private final Map<UUID, NbtCompound> thorHammerStates = new HashMap<>();
    /** Remaining server ticks in the global grace period; persisted with the world. */
    private int graceTicksRemaining;
    /** Persistent long cooldowns for Teleport Gambit's Domain ability. */
    private final Map<UUID, Integer> teleportGambitDomainCooldowns = new HashMap<>();
    /** Players disconnected or respawned before a pocket-domain return could be completed. */
    private final Map<UUID, NbtCompound> teleportGambitPendingReturns = new HashMap<>();
    private static final Map<PowerType, Power> powerInstances = new HashMap<>();

    /** Codec wrapper keeps the existing compound structure while using 1.21.11 state persistence. */
    private static final Codec<PowerManager> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
            },
            manager -> new Dynamic<>(NbtOps.INSTANCE, manager.toNbt())
    );

    private static final PersistentStateType<PowerManager> STATE_TYPE = new PersistentStateType<>(
            "yppowers_data", PowerManager::new, CODEC, null
    );

    public static void registerPower(Power power) {
        powerInstances.put(power.getType(), power);
    }

    public void assignPower(ServerPlayerEntity player, PowerType type) {
        PowerType oldType = playerPowers.get(player.getUuid());
        if (oldType != null) {
            Power oldPower = powerInstances.get(oldType);
            if (oldPower != null) {
                oldPower.onRemove(player);
            }
        }
        
        // Assigning a power starts it enabled. Clear any remembered toggle state for both
        // the previous and newly assigned power so administrative reassignment is predictable.
        if (oldType != null) setPowerEnabled(player.getUuid(), oldType, true);
        if (type != null) setPowerEnabled(player.getUuid(), type, true);
        playerPowers.put(player.getUuid(), type);

        if (type != null) {
            Power newPower = powerInstances.get(type);
            if (newPower != null) {
                newPower.onAssign(player);
            }
        }
        
        YPNetworking.syncFireGod(player, type == PowerType.FIRE_GOD);
        YPNetworking.syncShadowArcher(player, type == PowerType.SHADOW_ARCHER);
        this.markDirty();
    }

    public void clearPower(ServerPlayerEntity player) {
        PowerType oldType = playerPowers.remove(player.getUuid());
        if (oldType != null) {
            Power oldPower = powerInstances.get(oldType);
            if (oldPower != null) {
                oldPower.onRemove(player);
            }
        }
        disabledPowers.remove(player.getUuid());
        YPNetworking.syncFireGod(player, false);
        YPNetworking.syncShadowArcher(player, false);
        this.markDirty();
    }

    /** Whether the selected player's given power is enabled; powers default to enabled. */
    public boolean isPowerEnabled(UUID playerId, PowerType type) {
        if (type == null) return false;
        return !disabledPowers.getOrDefault(playerId, java.util.Collections.emptySet()).contains(type.getId());
    }

    public boolean isPowerEnabled(ServerPlayerEntity player, PowerType type) {
        return isPowerEnabled(player.getUuid(), type);
    }

    /** Whether the Boots of the Sky Feather Falling enchantment is enabled for this player. */
    public boolean isBootsSkyFeatherFallingEnabled(UUID playerId) {
        return !bootsSkyFeatherFallingDisabled.contains(playerId);
    }

    /** Toggle only the Boots of the Sky Feather Falling protection; other fall protection is untouched. */
    public boolean toggleBootsSkyFeatherFalling(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        boolean enabled;
        if (bootsSkyFeatherFallingDisabled.remove(playerId)) enabled = true;
        else { bootsSkyFeatherFallingDisabled.add(playerId); enabled = false; }
        markDirty();
        return enabled;
    }

    /** Toggle a power's enabled state and persist the change. Returns the new state. */
    public boolean togglePowerEnabled(ServerPlayerEntity player, PowerType type) {
        Set<String> disabled = disabledPowers.computeIfAbsent(player.getUuid(), ignored -> new HashSet<>());
        boolean enabled;
        if (disabled.remove(type.getId())) {
            enabled = true;
        } else {
            disabled.add(type.getId());
            enabled = false;
        }
        if (disabled.isEmpty()) disabledPowers.remove(player.getUuid());
        markDirty();
        return enabled;
    }

    private void setPowerEnabled(UUID playerId, PowerType type, boolean enabled) {
        if (type == null) return;
        Set<String> disabled = disabledPowers.computeIfAbsent(playerId, ignored -> new HashSet<>());
        boolean changed = enabled ? disabled.remove(type.getId()) : disabled.add(type.getId());
        if (disabled.isEmpty()) disabledPowers.remove(playerId);
        if (changed) markDirty();
    }

    public PowerType getPowerType(UUID uuid) {
        return playerPowers.get(uuid);
    }

    public void saveThorHammerState(UUID playerId, NbtCompound state) {
        if (state == null || state.isEmpty()) {
            if (thorHammerStates.remove(playerId) != null) markDirty();
            return;
        }
        thorHammerStates.put(playerId, state.copy());
        markDirty();
    }

    public NbtCompound getThorHammerState(UUID playerId) {
        NbtCompound state = thorHammerStates.get(playerId);
        return state == null ? null : state.copy();
    }

    public Map<UUID, NbtCompound> getThorHammerStates() {
        Map<UUID, NbtCompound> copy = new HashMap<>();
        thorHammerStates.forEach((id, state) -> copy.put(id, state.copy()));
        return copy;
    }

    public void clearThorHammerState(UUID playerId) {
        if (thorHammerStates.remove(playerId) != null) markDirty();
    }

    public boolean isGracePeriodActive() {
        return graceTicksRemaining > 0;
    }

    public int getGraceTicksRemaining() {
        return Math.max(0, graceTicksRemaining);
    }

    /** Sets the remaining duration. One minute equals 1,200 server ticks. */
    public void setGraceTicksRemaining(int ticks) {
        int clamped = Math.max(0, ticks);
        if (graceTicksRemaining != clamped) {
            graceTicksRemaining = clamped;
            markDirty();
        }
    }

    /** Advance the global grace countdown once per server tick. */
    public void tickGracePeriod(MinecraftServer server) {
        if (graceTicksRemaining <= 0) return;
        graceTicksRemaining--;
        // Persist once per second rather than marking the world state dirty 20 times/second.
        if (graceTicksRemaining == 0 || graceTicksRemaining % 20 == 0) markDirty();
        if (graceTicksRemaining == 0) {
            server.getPlayerManager().broadcast(
                    net.minecraft.text.Text.literal("Grace period ended. Abilities and PvP are enabled again."), false);
        }
    }

    public int getTeleportGambitDomainCooldownTicks(UUID playerId) {
        return Math.max(0, teleportGambitDomainCooldowns.getOrDefault(playerId, 0));
    }

    public void setTeleportGambitDomainCooldownTicks(UUID playerId, int ticks) {
        int clamped = Math.max(0, ticks);
        if (clamped == 0) teleportGambitDomainCooldowns.remove(playerId);
        else teleportGambitDomainCooldowns.put(playerId, clamped);
        markDirty();
    }

    /** Count domain cooldowns down once per server tick and persist changes once per second. */
    public void tickTeleportGambitDomainCooldowns() {
        if (teleportGambitDomainCooldowns.isEmpty()) return;
        boolean shouldSave = false;
        var iterator = teleportGambitDomainCooldowns.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
                iterator.remove();
                shouldSave = true;
            } else {
                entry.setValue(next);
                if (next % 20 == 0) shouldSave = true;
            }
        }
        if (shouldSave) markDirty();
    }

    public void saveTeleportGambitPendingReturn(UUID playerId, NbtCompound location) {
        if (location == null || location.isEmpty()) return;
        teleportGambitPendingReturns.put(playerId, location.copy());
        markDirty();
    }

    public NbtCompound getTeleportGambitPendingReturn(UUID playerId) {
        NbtCompound data = teleportGambitPendingReturns.get(playerId);
        return data == null ? null : data.copy();
    }

    public void clearTeleportGambitPendingReturn(UUID playerId) {
        if (teleportGambitPendingReturns.remove(playerId) != null) markDirty();
    }

    public Power getPowerInstance(PowerType type) {
        return powerInstances.get(type);
    }

    public boolean hasPower(ServerPlayerEntity player, PowerType type) {
        return playerPowers.get(player.getUuid()) == type;
    }

    public void recordShadowArcherKill(UUID playerId, EntityType<?> entityType) {
        Identifier id = Registries.ENTITY_TYPE.getId(entityType);
        if (id != null && shadowArcherKills.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(id.toString())) {
            markDirty();
        }
    }

    public Set<String> getShadowArcherKills(UUID playerId) {
        return new HashSet<>(shadowArcherKills.getOrDefault(playerId, java.util.Collections.emptySet()));
    }

    public void recordShadowArcherArrowUnlock(UUID playerId, ArrowType arrowType) {
        if (arrowType != null && shadowArcherUnlockedArrows
                .computeIfAbsent(playerId, ignored -> new HashSet<>()).add(arrowType.name())) {
            markDirty();
        }
    }

    public Set<ArrowType> getShadowArcherUnlockedArrows(UUID playerId) {
        Set<ArrowType> result = new HashSet<>();
        for (String raw : shadowArcherUnlockedArrows.getOrDefault(playerId, java.util.Collections.emptySet())) {
            try {
                result.add(ArrowType.valueOf(raw));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown enum names from older or manually edited world data.
            }
        }
        return result;
    }

    public boolean unlockSpiritForm(UUID playerId, String form) {
        if (form == null || !Set.of("WOLF", "GUARDIAN", "WRAITH").contains(form)) return false;
        boolean changed = spiritChannelerUnlocks.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(form);
        if (changed) markDirty();
        return changed;
    }

    public Set<String> getSpiritChannelerUnlocks(UUID playerId) {
        return new HashSet<>(spiritChannelerUnlocks.getOrDefault(playerId, java.util.Collections.emptySet()));
    }

    public int getTimeShards(UUID playerId) { return Math.max(0, timeShardCounts.getOrDefault(playerId, 0)); }
    public void addTimeShards(UUID playerId, int amount) {
        if (amount <= 0) return;
        int old = getTimeShards(playerId);
        int next = (int)Math.min(Integer.MAX_VALUE, (long)old + amount);
        timeShardCounts.put(playerId, next); markDirty();
    }
    public boolean spendTimeShards(UUID playerId, int amount) {
        int cost = Math.max(0, amount); int current = getTimeShards(playerId);
        if (current < cost) return false;
        timeShardCounts.put(playerId, current - cost); markDirty(); return true;
    }
    public void saveStoredTimeClones(UUID owner, List<NbtCompound> snapshots) {
        List<NbtCompound> copy = new ArrayList<>();
        if (snapshots != null) for (NbtCompound nbt : snapshots) if (nbt != null) copy.add(nbt.copy());
        if (copy.isEmpty()) storedTimeClones.remove(owner); else storedTimeClones.put(owner, copy);
        markDirty();
    }
    public List<NbtCompound> takeStoredTimeClones(UUID owner) {
        List<NbtCompound> saved = storedTimeClones.remove(owner);
        if (saved == null) return new ArrayList<>();
        markDirty(); List<NbtCompound> copy = new ArrayList<>(); for (NbtCompound nbt : saved) copy.add(nbt.copy()); return copy;
    }

    public String getTimeWarperForm(UUID owner) { return timeWarperForms.getOrDefault(owner, "TIME"); }
    public void setTimeWarperForm(UUID owner, String form) {
        if (!"TIME".equals(form) && !"REALITY".equals(form)) return;
        timeWarperForms.put(owner, form); markDirty();
    }
    public String getIceEmperorForm(UUID playerId) {
        String form = iceEmperorForms.get(playerId);
        return form != null && Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(form) ? form : "ARMOURED";
    }

    public boolean hasIceEmperorForm(UUID playerId) {
        return iceEmperorForms.containsKey(playerId);
    }

    public void setIceEmperorForm(UUID playerId, String form) {
        if (playerId == null || form == null || !Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(form)) return;
        if (!form.equals(iceEmperorForms.put(playerId, form))) markDirty();
    }

    public void clearIceEmperorForm(UUID playerId) {
        if (iceEmperorForms.remove(playerId) != null) markDirty();
    }

    public int getTimeWarperFormCooldownTicks(UUID owner) { return Math.max(0, timeWarperFormCooldowns.getOrDefault(owner, 0)); }
    public void setTimeWarperFormCooldownTicks(UUID owner, int ticks) {
        int value = Math.max(0, ticks); if (value == 0) timeWarperFormCooldowns.remove(owner); else timeWarperFormCooldowns.put(owner, value); markDirty();
    }
    public void tickTimeWarperFormCooldowns() {
        if (timeWarperFormCooldowns.isEmpty()) return;
        boolean changed = false; var it = timeWarperFormCooldowns.entrySet().iterator();
        while (it.hasNext()) { var e = it.next(); int value = e.getValue() - 1; if (value <= 0) { it.remove(); changed = true; } else { e.setValue(value); if (value % 20 == 0) changed = true; } }
        if (changed) markDirty();
    }

    private NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        NbtCompound powersNbt = new NbtCompound();
        for (Map.Entry<UUID, PowerType> entry : playerPowers.entrySet()) {
            powersNbt.putString(entry.getKey().toString(), entry.getValue().getId());
        }
        nbt.put("PlayerPowers", powersNbt);

        NbtCompound killsNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<String>> entry : shadowArcherKills.entrySet()) {
            NbtList ids = new NbtList();
            for (String id : entry.getValue()) ids.add(NbtString.of(id));
            killsNbt.put(entry.getKey().toString(), ids);
        }
        nbt.put("ShadowArcherKills", killsNbt);

        NbtCompound unlockedArrowsNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<String>> entry : shadowArcherUnlockedArrows.entrySet()) {
            NbtList ids = new NbtList();
            for (String id : entry.getValue()) ids.add(NbtString.of(id));
            unlockedArrowsNbt.put(entry.getKey().toString(), ids);
        }
        nbt.put("ShadowArcherUnlockedArrows", unlockedArrowsNbt);

        NbtCompound spiritUnlocksNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<String>> entry : spiritChannelerUnlocks.entrySet()) {
            NbtList forms = new NbtList();
            for (String form : entry.getValue()) forms.add(NbtString.of(form));
            spiritUnlocksNbt.put(entry.getKey().toString(), forms);
        }
        nbt.put("SpiritChannelerUnlocks", spiritUnlocksNbt);

        NbtCompound timeShardsNbt = new NbtCompound();
        for (Map.Entry<UUID, Integer> entry : timeShardCounts.entrySet()) {
            int count = Math.max(0, entry.getValue()); if (count > 0) timeShardsNbt.putInt(entry.getKey().toString(), count);
        }
        nbt.put("TimeShardCounts", timeShardsNbt);

        NbtCompound storedClonesNbt = new NbtCompound();
        for (Map.Entry<UUID, List<NbtCompound>> entry : storedTimeClones.entrySet()) {
            NbtList list = new NbtList(); for (NbtCompound clone : entry.getValue()) list.add(clone.copy());
            storedClonesNbt.put(entry.getKey().toString(), list);
        }
        nbt.put("StoredTimeClones", storedClonesNbt);

        NbtCompound formsNbt = new NbtCompound();
        for (Map.Entry<UUID, String> entry : timeWarperForms.entrySet()) formsNbt.putString(entry.getKey().toString(), entry.getValue());
        nbt.put("TimeWarperForms", formsNbt);
        NbtCompound formCooldownsNbt = new NbtCompound();
        for (Map.Entry<UUID, Integer> entry : timeWarperFormCooldowns.entrySet()) if (entry.getValue() > 0) formCooldownsNbt.putInt(entry.getKey().toString(), entry.getValue());
        nbt.put("TimeWarperFormCooldowns", formCooldownsNbt);

        NbtCompound iceFormsNbt = new NbtCompound();
        for (Map.Entry<UUID, String> entry : iceEmperorForms.entrySet()) {
            if (Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(entry.getValue())) {
                iceFormsNbt.putString(entry.getKey().toString(), entry.getValue());
            }
        }
        nbt.put("IceEmperorForms", iceFormsNbt);

        NbtCompound disabledPowersNbt = new NbtCompound();
        for (Map.Entry<UUID, Set<String>> entry : disabledPowers.entrySet()) {
            NbtList ids = new NbtList();
            for (String id : entry.getValue()) ids.add(NbtString.of(id));
            disabledPowersNbt.put(entry.getKey().toString(), ids);
        }
        nbt.put("DisabledPowers", disabledPowersNbt);

        NbtList bootsFeatherDisabled = new NbtList();
        for (UUID playerId : bootsSkyFeatherFallingDisabled) bootsFeatherDisabled.add(NbtString.of(playerId.toString()));
        nbt.put("BootsSkyFeatherFallingDisabled", bootsFeatherDisabled);

        NbtCompound thorHammersNbt = new NbtCompound();
        for (Map.Entry<UUID, NbtCompound> entry : thorHammerStates.entrySet()) {
            thorHammersNbt.put(entry.getKey().toString(), entry.getValue().copy());
        }
        nbt.put("ThorHammerStates", thorHammersNbt);
        nbt.putInt("GraceTicksRemaining", Math.max(0, graceTicksRemaining));

        NbtCompound tgCooldownsNbt = new NbtCompound();
        for (Map.Entry<UUID, Integer> entry : teleportGambitDomainCooldowns.entrySet()) {
            if (entry.getValue() > 0) tgCooldownsNbt.putInt(entry.getKey().toString(), entry.getValue());
        }
        nbt.put("TeleportGambitDomainCooldowns", tgCooldownsNbt);

        NbtCompound tgPendingReturnsNbt = new NbtCompound();
        for (Map.Entry<UUID, NbtCompound> entry : teleportGambitPendingReturns.entrySet()) {
            tgPendingReturnsNbt.put(entry.getKey().toString(), entry.getValue().copy());
        }
        nbt.put("TeleportGambitPendingReturns", tgPendingReturnsNbt);
        return nbt;
    }

    public static PowerManager fromNbt(NbtCompound nbt) {
        PowerManager manager = new PowerManager();
        NbtCompound powersNbt = nbt.getCompoundOrEmpty("PlayerPowers");
        for (String key : powersNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                PowerType type = PowerType.fromId(powersNbt.getString(key, ""));
                if (type != null) manager.playerPowers.put(uuid, type);
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid stale player keys rather than preventing world data from loading.
            }
        }
        NbtCompound killsNbt = nbt.getCompoundOrEmpty("ShadowArcherKills");
        for (String key : killsNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtList ids = killsNbt.getListOrEmpty(key);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < ids.size(); i++) {
                    String value = ids.getString(i, "");
                    if (Identifier.tryParse(value) != null) set.add(value);
                }
                if (!set.isEmpty()) manager.shadowArcherKills.put(uuid, set);
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid saved unlock entries.
            }
        }
        NbtCompound unlockedArrowsNbt = nbt.getCompoundOrEmpty("ShadowArcherUnlockedArrows");
        for (String key : unlockedArrowsNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtList ids = unlockedArrowsNbt.getListOrEmpty(key);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < ids.size(); i++) {
                    String value = ids.getString(i, "");
                    try {
                        ArrowType.valueOf(value);
                        set.add(value);
                    } catch (IllegalArgumentException ignored) {
                        // Ignore invalid arrow type identifiers.
                    }
                }
                if (!set.isEmpty()) manager.shadowArcherUnlockedArrows.put(uuid, set);
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid player UUIDs in stored arrow unlock entries.
            }
        }
        NbtCompound spiritUnlocksNbt = nbt.getCompoundOrEmpty("SpiritChannelerUnlocks");
        for (String key : spiritUnlocksNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtList forms = spiritUnlocksNbt.getListOrEmpty(key);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < forms.size(); i++) {
                    String form = forms.getString(i, "");
                    if (Set.of("WOLF", "GUARDIAN", "WRAITH").contains(form)) set.add(form);
                }
                if (!set.isEmpty()) manager.spiritChannelerUnlocks.put(uuid, set);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed player ids / unlock values.
            }
        }

        NbtCompound timeShardsNbt = nbt.getCompoundOrEmpty("TimeShardCounts");
        for (String key : timeShardsNbt.getKeys()) {
            try { UUID uuid = UUID.fromString(key); int count = Math.max(0, timeShardsNbt.getInt(key, 0)); if (count > 0) manager.timeShardCounts.put(uuid, count); }
            catch (IllegalArgumentException ignored) { }
        }
        NbtCompound storedClonesNbt = nbt.getCompoundOrEmpty("StoredTimeClones");
        for (String key : storedClonesNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key); NbtList list = storedClonesNbt.getListOrEmpty(key); List<NbtCompound> snapshots = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound clone = list.getCompoundOrEmpty(i);
                    String variant = clone.getString("Variant", ""); int ticks = Math.max(0, clone.getInt("Ticks", 0));
                    if ((variant.equals("ORDINARY") || variant.equals("ELITE")) && ticks > 0) snapshots.add(clone.copy());
                }
                if (!snapshots.isEmpty()) manager.storedTimeClones.put(uuid, snapshots);
            } catch (IllegalArgumentException ignored) { }
        }

        NbtCompound formsNbt = nbt.getCompoundOrEmpty("TimeWarperForms");
        for (String key : formsNbt.getKeys()) {
            try { UUID uuid=UUID.fromString(key); String form=formsNbt.getString(key, "TIME"); if (form.equals("TIME")||form.equals("REALITY")) manager.timeWarperForms.put(uuid,form); }
            catch (IllegalArgumentException ignored) { }
        }
        NbtCompound formCooldownsNbt = nbt.getCompoundOrEmpty("TimeWarperFormCooldowns");
        for (String key : formCooldownsNbt.getKeys()) {
            try { UUID uuid=UUID.fromString(key); int ticks=Math.max(0,formCooldownsNbt.getInt(key,0)); if(ticks>0)manager.timeWarperFormCooldowns.put(uuid,ticks); }
            catch (IllegalArgumentException ignored) { }
        }

        NbtCompound iceFormsNbt = nbt.getCompoundOrEmpty("IceEmperorForms");
        for (String key : iceFormsNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                String form = iceFormsNbt.getString(key, "ARMOURED");
                if (Set.of("ARMOURED", "STEALTH", "ASSASSIN").contains(form)) manager.iceEmperorForms.put(uuid, form);
            } catch (IllegalArgumentException ignored) {
                // Ignore corrupt form records without blocking world startup.
            }
        }

        NbtCompound disabledPowersNbt = nbt.getCompoundOrEmpty("DisabledPowers");
        for (String key : disabledPowersNbt.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtList ids = disabledPowersNbt.getListOrEmpty(key);
                Set<String> set = new HashSet<>();
                for (int i = 0; i < ids.size(); i++) {
                    String id = ids.getString(i, "");
                    PowerType type = PowerType.fromId(id);
                    if (type == PowerType.GOAT || type == PowerType.ICE_EMPEROR) set.add(id);
                }
                if (!set.isEmpty()) manager.disabledPowers.put(uuid, set);
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid player UUIDs in stored power-toggle entries.
            }
        }
        NbtList bootsFeatherDisabled = nbt.getListOrEmpty("BootsSkyFeatherFallingDisabled");
        for (int i = 0; i < bootsFeatherDisabled.size(); i++) {
            try { manager.bootsSkyFeatherFallingDisabled.add(UUID.fromString(bootsFeatherDisabled.getString(i, ""))); }
            catch (IllegalArgumentException ignored) { /* Ignore corrupt UUID toggle records. */ }
        }
        manager.graceTicksRemaining = Math.max(0, nbt.getInt("GraceTicksRemaining", 0));

        NbtCompound tgCooldownsNbt = nbt.getCompoundOrEmpty("TeleportGambitDomainCooldowns");
        for (String key : tgCooldownsNbt.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                int remaining = Math.max(0, tgCooldownsNbt.getInt(key, 0));
                if (remaining > 0) manager.teleportGambitDomainCooldowns.put(playerId, remaining);
            } catch (IllegalArgumentException ignored) {
                // Ignore stale malformed cooldown entries.
            }
        }

        NbtCompound tgPendingReturnsNbt = nbt.getCompoundOrEmpty("TeleportGambitPendingReturns");
        for (String key : tgPendingReturnsNbt.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                NbtCompound location = tgPendingReturnsNbt.getCompoundOrEmpty(key);
                if (Identifier.tryParse(location.getString("Dimension", "")) != null) {
                    manager.teleportGambitPendingReturns.put(playerId, location.copy());
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed pending locations rather than breaking the world save.
            }
        }

        NbtCompound thorHammersNbt = nbt.getCompoundOrEmpty("ThorHammerStates");
        for (String key : thorHammersNbt.getKeys()) {
            try {
                UUID playerId = UUID.fromString(key);
                NbtCompound state = thorHammersNbt.getCompoundOrEmpty(key);
                UUID.fromString(state.getString("EntityId", ""));
                if (net.minecraft.util.Identifier.tryParse(state.getString("Dimension", "")) != null) {
                    manager.thorHammerStates.put(playerId, state.copy());
                }
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed saved hammer metadata rather than breaking the world save.
            }
        }
        return manager;
    }

    public static PowerManager get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }
}
