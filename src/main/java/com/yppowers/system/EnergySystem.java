package com.yppowers.system;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.yppowers.config.YPConfig;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.network.YPNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pure server-authoritative Energy pool, regeneration, and Bleeding lockout system.
 * Contains zero power-specific branching — powers declare costs and gates via {@link Power}.
 */
public final class EnergySystem extends PersistentState {
    public static final class EnergyState {
        public double energy;
        public int bleedingTicks;
        public int bleedDamageClock;

        public EnergyState(double initialEnergy) {
            this.energy = initialEnergy;
        }
    }

    private final Map<UUID, EnergyState> players = new HashMap<>();
    private final Map<UUID, Long> lastNoticeTick = new HashMap<>();
    private long lastTickSave = -20;

    private static final Codec<EnergySystem> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
            },
            sys -> new Dynamic<>(NbtOps.INSTANCE, sys.toNbt())
    );

    private static final PersistentStateType<EnergySystem> STATE_TYPE = new PersistentStateType<>(
            "yppowers_v2_energy", EnergySystem::new, CODEC, null
    );

    public EnergySystem() {}

    public static EnergySystem get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public EnergyState state(UUID playerId) {
        return players.computeIfAbsent(playerId, ignored -> new EnergyState(YPConfig.get().energy.maxEnergy));
    }

    public double getEnergy(UUID playerId) {
        return clampEnergy(state(playerId).energy);
    }

    public int getBleedingTicks(UUID playerId) {
        return Math.max(0, state(playerId).bleedingTicks);
    }

    public boolean isBleeding(UUID playerId) {
        return getBleedingTicks(playerId) > 0;
    }

    public void setEnergy(UUID playerId, double value) {
        EnergyState s = state(playerId);
        s.energy = clampEnergy(value);
        markDirty();
    }

    public void clearBleeding(UUID playerId) {
        EnergyState s = state(playerId);
        if (s.bleedingTicks > 0) {
            s.bleedingTicks = 0;
            s.bleedDamageClock = 0;
            markDirty();
        }
    }

    public double drainAll(ServerPlayerEntity player) {
        EnergyState s = state(player.getUuid());
        double drained = clampEnergy(s.energy);
        s.energy = 0.0;
        markDirty();
        YPNetworking.syncHud(player);
        return drained;
    }

    /**
     * Spend Energy for a standard ability cast. If Energy is insufficient, triggers Bleeding
     * (without stacking/extending an existing Bleeding window) and returns false.
     */
    public boolean trySpend(ServerPlayerEntity player, double rawCost, String abilityLabel) {
        if (player == null || player.getEntityWorld().getServer() == null) return false;
        if (GraceSystem.blockAbility(player)) return false;

        EnergyState s = state(player.getUuid());
        if (s.bleedingTicks > 0) {
            notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
            return false;
        }

        double cost = Math.max(0.0, rawCost);
        if (cost <= 1.0e-9) return true;

        if (s.energy + 1.0e-6 < cost) {
            triggerBleeding(s);
            String label = (abilityLabel == null || abilityLabel.isBlank()) ? "that ability" : abilityLabel;
            notice(player, "Not enough Energy for " + label + "! You are Bleeding.", Formatting.RED);
            markDirty();
            YPNetworking.syncHud(player);
            return false;
        }

        s.energy = clampEnergy(s.energy - cost);
        markDirty();
        YPNetworking.syncHud(player);
        return true;
    }

    /**
     * Spend periodic upkeep (e.g. Boots of the Sky flight) without triggering Bleeding on empty.
     */
    public boolean trySpendUpkeep(ServerPlayerEntity player, double rawCost) {
        if (player == null || player.getEntityWorld().getServer() == null) return false;
        double cost = Math.max(0.0, rawCost);
        if (cost <= 1.0e-9) return true;
        EnergyState s = state(player.getUuid());
        if (s.energy + 1.0e-6 < cost) return false;
        s.energy = clampEnergy(s.energy - cost);
        markDirty();
        return true;
    }

    /** Pure logic helper for testing energy spend/bleeding transitions without a MinecraftServer. */
    public boolean trySpendPure(UUID playerId, double rawCost) {
        EnergyState s = state(playerId);
        if (s.bleedingTicks > 0) return false;
        double cost = Math.max(0.0, rawCost);
        if (cost <= 1.0e-9) return true;
        if (s.energy + 1.0e-6 < cost) {
            triggerBleeding(s);
            return false;
        }
        s.energy = clampEnergy(s.energy - cost);
        return true;
    }

    public void tickRegenPure(UUID playerId, boolean pauseRegen) {
        EnergyState s = state(playerId);
        if (!pauseRegen) {
            s.energy = clampEnergy(s.energy + Math.max(0.0, YPConfig.get().energy.regenPerSecond) / 20.0);
        }
        if (s.bleedingTicks > 0) {
            s.bleedingTicks--;
            s.bleedDamageClock++;
        }
    }

    private static void triggerBleeding(EnergyState s) {
        if (s.bleedingTicks <= 0) {
            YPConfig cfg = YPConfig.get();
            s.bleedingTicks = Math.min(Math.max(1, cfg.energy.bleedingMaxTicks), Math.max(1, cfg.energy.bleedingInitialTicks));
            s.bleedDamageClock = 0;
        }
    }

    public static void serverTick(MinecraftServer server) {
        EnergySystem sys = get(server);
        PlayerDataStore store = PlayerDataStore.get(server);
        YPConfig cfg = YPConfig.get();
        boolean changed = false;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            EnergyState s = sys.state(player.getUuid());
            Power power = store.getPower(player);
            boolean pauseRegen = power != null && power.pausesEnergyRegen(player);

            double prev = s.energy;
            if (!pauseRegen) {
                s.energy = sys.clampEnergy(s.energy + Math.max(0.0, cfg.energy.regenPerSecond) / 20.0);
            }
            if (Math.abs(s.energy - prev) > 1.0e-7) changed = true;

            if (s.bleedingTicks > 0) {
                s.bleedingTicks--;
                s.bleedDamageClock++;
                changed = true;
                if (s.bleedDamageClock >= 20 && player.getEntityWorld() instanceof ServerWorld world) {
                    s.bleedDamageClock = 0;
                    float dmg = (float) Math.max(0.0, cfg.energy.bleedingDamagePerSecond);
                    if (dmg > 0.0f) {
                        player.damage(world, world.getDamageSources().magic(), dmg);
                    }
                }
            }
        }

        if (changed && server.getTicks() - sys.lastTickSave >= 20L) {
            sys.lastTickSave = server.getTicks();
            sys.markDirty();
        }
    }

    private double clampEnergy(double value) {
        double max = Math.max(1.0, YPConfig.get().energy.maxEnergy);
        if (!Double.isFinite(value)) return max;
        return Math.max(0.0, Math.min(max, value));
    }

    private void notice(ServerPlayerEntity player, String message, Formatting formatting) {
        MinecraftServer server = player.getEntityWorld().getServer();
        long now = server == null ? 0L : server.getTicks();
        Long last = lastNoticeTick.get(player.getUuid());
        if (last == null || now - last >= 20L) {
            player.sendMessage(Text.literal(message).formatted(formatting), true);
            lastNoticeTick.put(player.getUuid(), now);
        }
    }

    public NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        NbtCompound all = new NbtCompound();
        players.forEach((uuid, s) -> {
            NbtCompound n = new NbtCompound();
            n.putDouble("Energy", clampEnergy(s.energy));
            n.putInt("BleedingTicks", Math.max(0, s.bleedingTicks));
            n.putInt("BleedClock", Math.max(0, s.bleedDamageClock));
            all.put(uuid.toString(), n);
        });
        root.put("Players", all);
        return root;
    }

    public static EnergySystem fromNbt(NbtCompound root) {
        EnergySystem sys = new EnergySystem();
        NbtCompound all = root.getCompoundOrEmpty("Players");
        for (String key : all.getKeys()) {
            try {
                UUID id = UUID.fromString(key);
                NbtCompound n = all.getCompoundOrEmpty(key);
                EnergyState s = new EnergyState(n.getDouble("Energy", YPConfig.get().energy.maxEnergy));
                s.bleedingTicks = Math.max(0, n.getInt("BleedingTicks", 0));
                s.bleedDamageClock = Math.max(0, Math.min(19, n.getInt("BleedClock", 0)));
                sys.players.put(id, s);
            } catch (IllegalArgumentException ignored) {}
        }
        return sys;
    }
}
