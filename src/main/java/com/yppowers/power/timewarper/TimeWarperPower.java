package com.yppowers.power.timewarper;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.FreezeSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Time Warper: TIME / REALITY form switching (R), Time Stop (TIME G), 25s Global Rewind (H),
 * Black Hole (V), Time Clone (REALITY G), and Elite Clone (B).
 */
public final class TimeWarperPower extends Power {
    private static final String CD_TIME_STOP = "time_warper_stop";
    private static final String CD_REWIND = "time_warper_rewind";
    private static final String CD_FORM_SWITCH = "time_warper_form_switch";
    private static final String NBT_FORM = "Form";

    private final RewindHistory rewindHistory = new RewindHistory();
    private final BlackHoleHandler blackHoleHandler = new BlackHoleHandler();
    private final TimeCloneHandler cloneHandler = new TimeCloneHandler();
    private final Map<UUID, Integer> activeTimeStops = new HashMap<>();

    @Override
    public PowerId id() {
        return PowerId.TIME_WARPER;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        if (isRealityForm(player)) {
            return List.of(
                    AbilitySpec.utility(AbilitySlot.PRIMARY, "Time Clone"),
                    AbilitySpec.active(AbilitySlot.SECONDARY, "Rewind", CD_REWIND),
                    AbilitySpec.active(AbilitySlot.TERTIARY, "Black Hole", BlackHoleHandler.CD_BLACK_HOLE),
                    AbilitySpec.utility(AbilitySlot.QUATERNARY, "Elite Clone"),
                    AbilitySpec.active(AbilitySlot.ALTERNATE, "Form Switch", CD_FORM_SWITCH)
            );
        }
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Time Stop", CD_TIME_STOP),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Rewind", CD_REWIND),
                AbilitySpec.active(AbilitySlot.TERTIARY, "Black Hole", BlackHoleHandler.CD_BLACK_HOLE),
                AbilitySpec.utility(AbilitySlot.QUATERNARY, "Elite Clone"),
                AbilitySpec.active(AbilitySlot.ALTERNATE, "Form Switch", CD_FORM_SWITCH)
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        String form = isRealityForm(player) ? "REALITY" : "TIME";
        int shards = cloneHandler.getShardCount(player);
        return form + " • " + shards + " Shards";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY
                || slot == AbilitySlot.TERTIARY || slot == AbilitySlot.QUATERNARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        PowerConfigs.TimeWarper cfg = YPConfig.get().powers.timeWarper;
        return switch (slot) {
            case PRIMARY -> isRealityForm(player) ? cfg.cloneEnergyCost : super.energyCost(player, slot);
            case TERTIARY -> cfg.blackHoleEnergyCost;
            case QUATERNARY -> cfg.eliteCloneEnergyCost;
            case ALTERNATE -> 0.0;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        return switch (slot) {
            case PRIMARY -> isRealityForm(player)
                    ? cloneHandler.canSummon(player, false)
                    : cds.checkReady(player, CD_TIME_STOP, "Time Stop");
            case SECONDARY -> cds.checkReady(player, CD_REWIND, "Time Warp Rewind");
            case TERTIARY -> {
                if (blackHoleHandler.isChargingOrActive(player.getUuid())) {
                    player.sendMessage(Text.literal("A Black Hole is already active.").formatted(Formatting.YELLOW), true);
                    yield false;
                }
                yield cds.checkReady(player, BlackHoleHandler.CD_BLACK_HOLE, "Black Hole");
            }
            case QUATERNARY -> cloneHandler.canSummon(player, true);
            case ALTERNATE -> cds.checkReady(player, CD_FORM_SWITCH, "Form Switch");
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> {
                if (isRealityForm(player)) cloneHandler.summonClone(player, false);
                else startTimeStop(player);
            }
            case SECONDARY -> triggerGlobalRewind(player);
            case TERTIARY -> blackHoleHandler.start(player);
            case QUATERNARY -> cloneHandler.summonClone(player, true);
            case ALTERNATE -> switchForm(player);
            default -> {}
        }
    }

    private void switchForm(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        boolean nextReality = !isRealityForm(player);
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.TIME_WARPER).putString(NBT_FORM, nextReality ? "REALITY" : "TIME");
        store.markDirty();
        CooldownSystem.get(server).setPersistent(player.getUuid(), CD_FORM_SWITCH, YPConfig.get().powers.timeWarper.formSwitchCooldownTicks);
        player.sendMessage(Text.literal("Time Warper Form: " + (nextReality ? "REALITY" : "TIME"))
                .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
    }

    private void startTimeStop(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.TimeWarper cfg = YPConfig.get().powers.timeWarper;
        int dur = cfg.timeStopDurationTicks;
        activeTimeStops.put(player.getUuid(), dur);
        freezeEligibleAround(world.getServer(), player, dur);
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_TIME_STOP, cfg.timeStopCooldownTicks);
        VfxSystem.shockwave(world, ParticleTypes.PORTAL, ParticleTypes.END_ROD, player.getEntityPos().add(0, 1.0, 0), 16.0, 44);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1.8f, 0.5f);
        player.sendMessage(Text.literal("TIME STOP!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
    }

    private static void freezeEligibleAround(MinecraftServer server, ServerPlayerEntity caster, int ticks) {
        AllianceSystem alliances = AllianceSystem.get(server);
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity == caster) continue;
                if (entity instanceof ServerPlayerEntity sp && alliances.isAlly(caster, sp)) continue;
                FreezeSystem.freeze(entity, FreezeSystem.Source.TIME_STOP, ticks);
            }
        }
    }

    private void triggerGlobalRewind(ServerPlayerEntity caster) {
        MinecraftServer server = caster.getEntityWorld().getServer();
        if (server == null) return;
        int rewound = 0;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            RewindHistory.Snapshot snap = rewindHistory.getRewindTarget(p.getUuid());
            if (snap == null) continue;
            ServerWorld dest = server.getWorld(snap.dimension());
            if (dest == null) continue;
            p.teleport(dest, snap.x(), snap.y(), snap.z(), Set.of(), snap.yaw(), snap.pitch(), true);
            p.setHealth(Math.max(1.0f, Math.min(p.getMaxHealth(), snap.health())));
            if (p != caster) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 120, 0));
            }
            VfxSystem.burst(dest, ParticleTypes.REVERSE_PORTAL, p.getEntityPos().add(0, 1.0, 0), 24, 0.6, 0.1);
            rewound++;
        }
        CooldownSystem.get(server).set(caster.getUuid(), CD_REWIND, YPConfig.get().powers.timeWarper.rewindCooldownTicks);
        if (caster.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.playSound(world, caster.getEntityPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.5f, 1.4f);
        }
        caster.sendMessage(Text.literal("TIME WARP! Rewound " + rewound + " player(s) 25 seconds.").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity attacker, Entity target, DamageSource source, float amount) {
        // Caster deals 50% damage during their own Time Stop for balance
        return activeTimeStops.containsKey(attacker.getUuid()) ? amount * 0.50f : amount;
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        long tick = server.getTicks();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            rewindHistory.record(p.getUuid(), new RewindHistory.Snapshot(
                    p.getEntityWorld().getRegistryKey(), p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), p.getHealth()), tick);
        }
        if (tick % 20 == 0) rewindHistory.sweepStale(tick, 1200L);

        // Keep newly spawned entities frozen while Time Stop is active
        Iterator<Map.Entry<UUID, Integer>> it = activeTimeStops.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            int next = entry.getValue() - 1;
            if (next <= 0) {
                it.remove();
            } else {
                entry.setValue(next);
                if (next % 5 == 0) {
                    ServerPlayerEntity caster = server.getPlayerManager().getPlayer(entry.getKey());
                    if (caster != null) freezeEligibleAround(server, caster, next);
                }
            }
        }

        blackHoleHandler.serverTick(server);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        cloneHandler.collectNearbyShards(player);
    }

    @Override
    public void onAnyMobKilled(ServerWorld world, MobEntity victim, DamageSource source) {
        cloneHandler.dropShardsOnMobKill(world, victim);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        cloneHandler.restoreClones(player);
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        activeTimeStops.remove(player.getUuid());
        cloneHandler.suspendClones(player);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        activeTimeStops.remove(player.getUuid());
        cloneHandler.discardAllClones(player);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        activeTimeStops.clear();
        rewindHistory.clear();
        blackHoleHandler.clearAll();
    }

    private boolean isRealityForm(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        return "REALITY".equalsIgnoreCase(PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.TIME_WARPER)
                .getString(NBT_FORM, "TIME"));
    }
}
