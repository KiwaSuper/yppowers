package com.yppowers.energy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.yppowers.config.YPConfig;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.impl.GoatPower;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Persistent server-authoritative player Energy, Bleeding, GOAT Overdrive and ultimate cooldown state. */
public final class EnergyManager extends PersistentState {
    private static final class PlayerState {
        double energy = 100.0;
        int bleedingTicks;
        int overdriveTicks;
        int overdriveCooldownTicks;
        double goatDamageDebt;
        int blackHoleCooldownTicks;
        int raigoCooldownTicks;
        int bleedDamageClock;
    }

    private final Map<UUID, PlayerState> players = new HashMap<>();
    private static final Codec<EnergyManager> CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                NbtElement element = dynamic.convert(NbtOps.INSTANCE).getValue();
                return fromNbt(element instanceof NbtCompound compound ? compound : new NbtCompound());
            }, manager -> new Dynamic<>(NbtOps.INSTANCE, manager.toNbt()));
    private static final PersistentStateType<EnergyManager> STATE_TYPE = new PersistentStateType<>(
            "yppowers_energy", EnergyManager::new, CODEC, null);
    private final Map<UUID, Long> lastNoticeTick = new HashMap<>();
    private long lastTickSave = -20;

    private EnergyManager() {}

    public static EnergyManager get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    private PlayerState state(UUID id) {
        return players.computeIfAbsent(id, ignored -> {
            PlayerState state = new PlayerState();
            state.energy = YPConfig.getInstance().energy_max;
            return state;
        });
    }

    public double getEnergy(UUID playerId) { return clampEnergy(state(playerId).energy); }
    public int getBleedingTicks(UUID playerId) { return Math.max(0, state(playerId).bleedingTicks); }
    public int getOverdriveTicks(UUID playerId) { return Math.max(0, state(playerId).overdriveTicks); }
    public int getGoatCooldownTicks(UUID playerId) { return Math.max(0, state(playerId).overdriveCooldownTicks); }
    public double getGoatDamageDebt(UUID playerId) { return Math.max(0.0, state(playerId).goatDamageDebt); }
    public int getBlackHoleCooldownTicks(UUID playerId) { return Math.max(0, state(playerId).blackHoleCooldownTicks); }
    public int getRaigoCooldownTicks(UUID playerId) { return Math.max(0, state(playerId).raigoCooldownTicks); }
    public boolean isOverdriveActive(UUID playerId) { return getOverdriveTicks(playerId) > 0; }

    public void setEnergy(UUID playerId, double value) {
        PlayerState state = state(playerId);
        state.energy = clampEnergy(value);
        markDirty();
    }

    private double clampEnergy(double value) {
        return Math.max(0.0, Math.min(Math.max(1.0, YPConfig.getInstance().energy_max), value));
    }

    /** Validates cooldown/Bleeding server-side, then spends the cost associated with a key slot. */
    public boolean trySpendAbility(ServerPlayerEntity player, int keyType) {
        if (player == null || player.getEntityWorld().getServer() == null) return false;
        if (com.yppowers.command.GraceManager.blockAbility(player)) return false;
        PlayerState state = state(player.getUuid());
        if (state.bleedingTicks > 0) {
            notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
            return false;
        }

        PowerManager powerManager = PowerManager.get(player.getEntityWorld().getServer());
        PowerType power = powerManager.getPowerType(player.getUuid());
        YPConfig config = YPConfig.getInstance();
        double cost;
        if (keyType == 2 && power == PowerType.GOAT) {
            if (state.overdriveTicks > 0 || state.overdriveCooldownTicks > 0) {
                notice(player, state.overdriveTicks > 0 ? "GOAT Overdrive is already active." : cooldownText("GOAT Overdrive", state.overdriveCooldownTicks), Formatting.YELLOW);
                return false;
            }
            cost = config.goat_overdrive_energy_cost;
        } else if (keyType == 2 && power == PowerType.TIME_WARPER) {
            Power timeWarper = powerManager.getPowerInstance(PowerType.TIME_WARPER);
            if (timeWarper instanceof com.yppowers.power.impl.TimeWarperPower tw && tw.isBlackHoleCharging(player.getUuid())) {
                notice(player, "A Black Hole is already charging.", Formatting.YELLOW);
                return false;
            }
            if (state.blackHoleCooldownTicks > 0) {
                notice(player, cooldownText("Black Hole", state.blackHoleCooldownTicks), Formatting.YELLOW);
                return false;
            }
            cost = config.time_warper_black_hole_energy_cost;
        } else if (keyType == 4 && power == PowerType.BOOTS_SKY) {
            Power boots = powerManager.getPowerInstance(PowerType.BOOTS_SKY);
            if (boots instanceof com.yppowers.power.impl.BootsOfTheSkyPower sky && sky.isRaigoCharging(player.getUuid())) {
                notice(player, "Raigo is already charging.", Formatting.YELLOW);
                return false;
            }
            if (state.raigoCooldownTicks > 0) {
                notice(player, cooldownText("Raigo", state.raigoCooldownTicks), Formatting.YELLOW);
                return false;
            }
            cost = config.boots_raigo_energy_cost;
        } else {
            // keyType 5 is the existing double-jump flight toggle and pays the primary cost.
            cost = switch (keyType) {
                case 0, 5 -> config.energy_cost_primary;
                case 1 -> config.energy_cost_secondary;
                case 2 -> config.energy_cost_tertiary;
                case 3 -> config.energy_cost_quaternary;
                case 4 -> config.energy_cost_alternate;
                default -> 0.0;
            };
        }

        cost = Math.max(0.0, cost);
        if (state.energy + 1.0e-6 < cost) {
            // Repeated attempts do not stack or extend the fixed Bleeding window.
            if (state.bleedingTicks <= 0) {
                state.bleedingTicks = Math.min(Math.max(1, config.energy_bleeding_max_ticks),
                        Math.max(1, config.energy_bleeding_initial_ticks));
                state.bleedDamageClock = 0;
            }
            notice(player, "Not enough Energy! You are Bleeding and cannot use abilities.", Formatting.RED);
            markDirty();
            sync(player);
            return false;
        }
        state.energy = clampEnergy(state.energy - cost);
        markDirty();
        sync(player);
        return true;
    }

    public boolean trySpendDirect(ServerPlayerEntity player, double amount, String abilityName) {
        if (player == null || player.getEntityWorld().getServer() == null) return false;
        PlayerState state = state(player.getUuid());
        if (com.yppowers.command.GraceManager.blockAbility(player)) return false;
        if (state.bleedingTicks > 0) {
            notice(player, "You are Bleeding. Abilities are disabled until the effect ends.", Formatting.RED);
            return false;
        }
        double cost = Math.max(0.0, amount);
        if (state.energy + 1.0e-6 < cost) {
            if (state.bleedingTicks <= 0) {
                YPConfig config = YPConfig.getInstance();
                state.bleedingTicks = Math.min(Math.max(1, config.energy_bleeding_max_ticks), Math.max(1, config.energy_bleeding_initial_ticks));
                state.bleedDamageClock = 0;
            }
            notice(player, "Not enough Energy for " + abilityName + "! Bleeding prevents ability use.", Formatting.RED);
            markDirty();
            sync(player);
            return false;
        }
        state.energy = clampEnergy(state.energy - cost);
        markDirty();
        sync(player);
        return true;
    }

    public void startGoatOverdrive(ServerPlayerEntity player) {
        PlayerState state = state(player.getUuid());
        YPConfig config = YPConfig.getInstance();
        state.overdriveTicks = Math.max(1, config.goat_overdrive_duration_ticks);
        state.overdriveCooldownTicks = Math.max(state.overdriveCooldownTicks, config.goat_overdrive_cooldown_ticks);
        Power goat = PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT);
        if (goat instanceof GoatPower goatPower) goatPower.applyOverdrive(player);
        markDirty();
        sync(player);
    }

    public void startBlackHoleCooldown(ServerPlayerEntity player) {
        PlayerState state = state(player.getUuid());
        state.blackHoleCooldownTicks = Math.max(state.blackHoleCooldownTicks, YPConfig.getInstance().time_warper_black_hole_cooldown_ticks);
        markDirty();
        sync(player);
    }

    public void startRaigoCooldown(ServerPlayerEntity player) {
        PlayerState state = state(player.getUuid());
        state.raigoCooldownTicks = Math.max(state.raigoCooldownTicks, YPConfig.getInstance().boots_raigo_cooldown_ticks);
        markDirty();
        sync(player);
    }

    public void addGoatDamageDebt(ServerPlayerEntity player, double damage) {
        if (damage <= 0.0 || !Double.isFinite(damage)) return;
        PlayerState state = state(player.getUuid());
        state.goatDamageDebt = Math.min(Math.max(1.0, YPConfig.getInstance().goat_overdrive_max_damage_debt), state.goatDamageDebt + damage);
        markDirty();
    }

    public void endGoatOverdrive(ServerPlayerEntity player) {
        PlayerState state = state(player.getUuid());
        if (state.overdriveTicks > 0) state.overdriveTicks = 0;
        Power goat = PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT);
        if (goat instanceof GoatPower goatPower) goatPower.removeOverdrive(player);
        markDirty();
        sync(player);
    }

    public void onPlayerDeath(ServerPlayerEntity player) {
        PlayerState state = state(player.getUuid());
        state.overdriveTicks = 0;
        Power goat = player.getEntityWorld().getServer() == null ? null : PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT);
        if (goat instanceof GoatPower goatPower) goatPower.removeOverdrive(player);
        // Energy, cooldown and damage debt deliberately survive death.
        markDirty();
    }

    /** End the temporary transformation on logout while preserving Energy, cooldown, and debt. */
    public void onPlayerDisconnect(ServerPlayerEntity player) {
        PlayerState state = state(player.getUuid());
        state.overdriveTicks = 0;
        MinecraftServer server = player.getEntityWorld().getServer();
        Power goat = server == null ? null : PowerManager.get(server).getPowerInstance(PowerType.GOAT);
        if (goat instanceof GoatPower goatPower) goatPower.removeOverdrive(player);
        // Do not clear the debt or refund the cooldown when reconnecting.
        markDirty();
    }

    /** End temporary Overdrive before a world saves, keeping its debt and cooldown persistent. */
    public static void endOverdrivesForShutdown(MinecraftServer server) {
        EnergyManager manager = get(server);
        Power goat = PowerManager.get(server).getPowerInstance(PowerType.GOAT);
        boolean changed = false;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerState state = manager.state(player.getUuid());
            if (state.overdriveTicks <= 0) continue;
            state.overdriveTicks = 0;
            if (goat instanceof GoatPower goatPower) goatPower.removeOverdrive(player);
            changed = true;
        }
        if (changed) manager.markDirty();
    }

    public void onJoin(ServerPlayerEntity player) {
        applyPlayerOnlyState(player);
        sync(player);
    }

    private void applyPlayerOnlyState(ServerPlayerEntity player) {
        GlobalHealthManager.apply(player);
        PlayerState state = state(player.getUuid());
        Power goat = player.getEntityWorld().getServer() == null ? null : PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT);
        if (goat instanceof GoatPower goatPower) {
            if (state.overdriveTicks > 0) goatPower.applyOverdrive(player);
            else goatPower.removeOverdrive(player);
        }
    }

    public void sync(ServerPlayerEntity player) {
        if (player == null) return;
        PlayerState state = state(player.getUuid());
        YPNetworking.syncEnergy(player, clampEnergy(state.energy), Math.max(1.0, YPConfig.getInstance().energy_max),
                state.bleedingTicks, YPConfig.getInstance().global_health_enabled);
    }

    private void notice(ServerPlayerEntity player, String message, Formatting formatting) {
        MinecraftServer server = player.getEntityWorld().getServer();
        long tick = server == null ? 0 : server.getTicks();
        Long last = lastNoticeTick.get(player.getUuid());
        if (last == null || tick - last >= 20L) {
            player.sendMessage(Text.literal(message).formatted(formatting), true);
            lastNoticeTick.put(player.getUuid(), tick);
        }
    }

    private static String cooldownText(String name, int ticks) {
        return name + " cooldown: " + ((Math.max(0, ticks) + 19) / 20) + "s.";
    }

    /** Tick only players who are online; values and outstanding damage debts are persistent. */
    public static void serverTick(MinecraftServer server) {
        EnergyManager manager = get(server);
        YPConfig config = YPConfig.getInstance();
        boolean changed = false;
        Iterator<Map.Entry<UUID, PlayerState>> entries = manager.players.entrySet().iterator();
        while (entries.hasNext()) {
            PlayerState state = entries.next().getValue();
            if (state.overdriveCooldownTicks > 0) { state.overdriveCooldownTicks--; changed = true; }
            if (state.blackHoleCooldownTicks > 0) { state.blackHoleCooldownTicks--; changed = true; }
            if (state.raigoCooldownTicks > 0) { state.raigoCooldownTicks--; changed = true; }
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerState state = manager.state(player.getUuid());
            double previousEnergy = state.energy;
            state.energy = manager.clampEnergy(state.energy + Math.max(0.0, config.energy_regen_per_second) / 20.0);
            if (Math.abs(state.energy - previousEnergy) > 1.0e-7) changed = true;

            if (state.overdriveTicks > 0) {
                Power goat = PowerManager.get(server).getPowerInstance(PowerType.GOAT);
                if (com.yppowers.command.GraceManager.isActive(server)) {
                    state.overdriveTicks = 0;
                    if (goat instanceof GoatPower goatPower) goatPower.removeOverdrive(player);
                    player.sendMessage(Text.literal("GOAT Overdrive ended because the grace period began; deferred damage remains.")
                            .formatted(Formatting.YELLOW), true);
                } else {
                    state.overdriveTicks--;
                    if (state.overdriveTicks == 0) {
                        if (goat instanceof GoatPower goatPower) goatPower.removeOverdrive(player);
                        player.sendMessage(Text.literal("GOAT Overdrive ended. Deferred damage is catching up.").formatted(Formatting.GOLD), true);
                    } else if (goat instanceof GoatPower goatPower) {
                        goatPower.tickOverdrive(player);
                    }
                }
                changed = true;
            }

            if (state.bleedingTicks > 0) {
                state.bleedingTicks--;
                state.bleedDamageClock++;
                changed = true;
                if (state.bleedDamageClock >= 20 && player.getEntityWorld() instanceof ServerWorld world) {
                    state.bleedDamageClock = 0;
                    player.damage(world, world.getDamageSources().magic(), (float) Math.max(0.0, config.energy_bleeding_damage_per_second));
                }
            }

            // Deferred GOAT damage is paid after Overdrive, independent of healing. Debt is
            // deducted before damage is applied to prevent recursive/duplicate debt collection.
            if (state.overdriveTicks <= 0 && state.goatDamageDebt > 0.0 && player.isAlive()
                    && server.getTicks() % Math.max(1, config.goat_overdrive_debt_release_interval_ticks) == 0
                    && player.getEntityWorld() instanceof ServerWorld world) {
                double release = Math.min(state.goatDamageDebt, Math.max(0.1, config.goat_overdrive_debt_release_per_hit));
                state.goatDamageDebt = Math.max(0.0, state.goatDamageDebt - release);
                changed = true;
                player.damage(world, world.getDamageSources().magic(), (float) release);
            }

            if (server.getTicks() % 5 == 0) manager.sync(player);
        }
        if (changed && server.getTicks() - manager.lastTickSave >= 20) {
            manager.lastTickSave = server.getTicks();
            manager.markDirty();
        }
    }

    private NbtCompound toNbt() {
        NbtCompound root = new NbtCompound();
        NbtCompound all = new NbtCompound();
        for (Map.Entry<UUID, PlayerState> entry : players.entrySet()) {
            PlayerState s = entry.getValue();
            NbtCompound n = new NbtCompound();
            n.putDouble("Energy", s.energy);
            n.putInt("BleedingTicks", Math.max(0, s.bleedingTicks));
            n.putInt("BleedDamageClock", Math.max(0, s.bleedDamageClock));
            n.putInt("OverdriveTicks", Math.max(0, s.overdriveTicks));
            n.putInt("OverdriveCooldownTicks", Math.max(0, s.overdriveCooldownTicks));
            n.putDouble("GoatDamageDebt", Math.max(0.0, s.goatDamageDebt));
            n.putInt("BlackHoleCooldownTicks", Math.max(0, s.blackHoleCooldownTicks));
            n.putInt("RaigoCooldownTicks", Math.max(0, s.raigoCooldownTicks));
            all.put(entry.getKey().toString(), n);
        }
        root.put("Players", all);
        return root;
    }

    public static EnergyManager fromNbt(NbtCompound root) {
        EnergyManager manager = new EnergyManager();
        NbtCompound all = root.getCompoundOrEmpty("Players");
        for (String key : all.getKeys()) {
            try {
                UUID id = UUID.fromString(key);
                NbtCompound n = all.getCompoundOrEmpty(key);
                PlayerState s = new PlayerState();
                s.energy = Math.max(0.0, Math.min(100000.0, n.getDouble("Energy", 100.0)));
                s.bleedingTicks = Math.max(0, n.getInt("BleedingTicks", 0));
                s.bleedDamageClock = Math.max(0, Math.min(19, n.getInt("BleedDamageClock", 0)));
                s.overdriveTicks = Math.max(0, n.getInt("OverdriveTicks", 0));
                s.overdriveCooldownTicks = Math.max(0, n.getInt("OverdriveCooldownTicks", 0));
                s.goatDamageDebt = Math.max(0.0, Math.min(10000.0, n.getDouble("GoatDamageDebt", 0.0)));
                s.blackHoleCooldownTicks = Math.max(0, n.getInt("BlackHoleCooldownTicks", 0));
                s.raigoCooldownTicks = Math.max(0, n.getInt("RaigoCooldownTicks", 0));
                manager.players.put(id, s);
            } catch (IllegalArgumentException ignored) {
                // Ignore malformed UUIDs in player state.
            }
        }
        return manager;
    }
}
