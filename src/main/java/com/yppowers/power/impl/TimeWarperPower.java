package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

import java.util.*;

/** Time Warper shared state. Histories and stops are advanced once per server tick. */
public class TimeWarperPower extends Power {
    private static final int REWIND_TICKS = 25 * 20;
    private final Map<UUID, Integer> timeStopActive = new HashMap<>();
    private final Map<UUID, List<FrozenEntity>> frozenEntities = new HashMap<>();
    private final Map<UUID, Long> timeStopCooldown = new HashMap<>();
    private final Map<UUID, Long> timeWarpCooldown = new HashMap<>();
    private final Map<UUID, LinkedList<PlayerSnapshot>> playerHistories = new HashMap<>();
    private static final Map<Entity, Integer> FREEZE_COUNTS = new IdentityHashMap<>();

    private static void retainFrozen(Entity entity) { FREEZE_COUNTS.merge(entity, 1, Integer::sum); }
    private static void releaseFrozen(Entity entity) {
        Integer count = FREEZE_COUNTS.get(entity);
        if (count == null || count <= 1) FREEZE_COUNTS.remove(entity);
        else FREEZE_COUNTS.put(entity, count - 1);
    }

    private static class PlayerSnapshot {
        final double x, y, z;
        final float yaw, pitch, health;
        final RegistryKey<World> dimension;
        PlayerSnapshot(ServerPlayerEntity player) {
            x = player.getX(); y = player.getY(); z = player.getZ();
            yaw = player.getYaw(); pitch = player.getPitch(); health = player.getHealth();
            dimension = player.getEntityWorld().getRegistryKey();
        }
    }

    private static class FrozenEntity {
        final Entity entity;
        Vec3d position;
        Vec3d velocity;
        float yaw, pitch;
        FrozenEntity(Entity entity) {
            this.entity = entity;
            this.position = entity.getEntityPos();
            Vec3d savedVelocity = TimeWarperPower.getFrozenVelocity(entity);
            if (savedVelocity == null) savedVelocity = SeriousPunchPower.getFrozenVelocity(entity);
            if (savedVelocity == null) savedVelocity = IceEmperorPower.getFrozenVelocity(entity);
            this.velocity = savedVelocity == null ? entity.getVelocity() : savedVelocity;
            this.yaw = entity.getYaw();
            this.pitch = entity.getPitch();
        }
    }

    @Override
    public PowerType getType() { return PowerType.TIME_WARPER; }

    /** Records every online player, regardless of which power they have. */
    public static void serverTick(MinecraftServer server) {
        Power instance = PowerManager.get(server).getPowerInstance(PowerType.TIME_WARPER);
        if (instance instanceof TimeWarperPower timeWarper) timeWarper.tickServer(server);
    }

    private void tickServer(MinecraftServer server) {
        Set<UUID> onlineIds = new HashSet<>();
        for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) onlineIds.add(online.getUuid());
        playerHistories.keySet().retainAll(onlineIds);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            LinkedList<PlayerSnapshot> history = playerHistories.computeIfAbsent(player.getUuid(), k -> new LinkedList<>());
            history.addLast(new PlayerSnapshot(player));
            // 501 snapshots allow the oldest one to be exactly 500 ticks behind the newest.
            while (history.size() > REWIND_TICKS + 1) history.removeFirst();
        }

        Iterator<Map.Entry<UUID, Integer>> stops = timeStopActive.entrySet().iterator();
        while (stops.hasNext()) {
            Map.Entry<UUID, Integer> stop = stops.next();
            UUID casterId = stop.getKey();
            int remaining = stop.getValue() - 1;
            List<FrozenEntity> frozen = frozenEntities.get(casterId);
            if (remaining <= 0) {
                if (frozen != null) {
                    for (FrozenEntity fe : frozen) {
                        releaseFrozen(fe.entity);
                        if (fe.entity.isAlive() && !fe.entity.isRemoved()
                                && !FREEZE_COUNTS.containsKey(fe.entity)
                                && !SeriousPunchPower.isEntityFrozen(fe.entity)
                                && !IceEmperorPower.isEntityFrozen(fe.entity)) {
                            fe.entity.setVelocity(fe.velocity);
                            ((com.yppowers.mixin.EntityVelocityAccessor) fe.entity).invokeYppowersScheduleVelocityUpdate();
                        }
                    }
                }
                frozenEntities.remove(casterId);
                stops.remove();
                ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
                if (caster != null) caster.sendMessage(Text.literal("Time Stop ended."), true);
                continue;
            }
            stop.setValue(remaining);
            if (frozen == null) continue;

            // Entities spawned during Time Stop are also frozen before their next tick.
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
            if (caster != null) {
                Set<Entity> known = Collections.newSetFromMap(new IdentityHashMap<>());
                for (FrozenEntity fe : frozen) known.add(fe.entity);
                AllianceManager alliances = AllianceManager.get(server);
                for (ServerWorld world : server.getWorlds()) {
                    for (Entity entity : world.iterateEntities()) {
                        if (known.contains(entity) || entity == caster || entity.isRemoved()) continue;
                        if (entity instanceof ServerPlayerEntity other && alliances.isAlly(caster, other)) continue;
                        frozen.add(new FrozenEntity(entity));
                        known.add(entity);
                        retainFrozen(entity);
                    }
                }
            }

            for (FrozenEntity fe : frozen) {
                if (fe.entity.isAlive() && !fe.entity.isRemoved()) {
                    fe.entity.setPosition(fe.position);
                    fe.entity.setVelocity(Vec3d.ZERO);
                    fe.entity.setYaw(fe.yaw);
                    fe.entity.setPitch(fe.pitch);
                    ((com.yppowers.mixin.EntityVelocityAccessor) fe.entity).invokeYppowersScheduleVelocityUpdate();
                    if (server.getTicks() % 5 == 0 && fe.entity.getEntityWorld() instanceof ServerWorld world) {
                        world.spawnParticles(ParticleTypes.WITCH, fe.position.x, fe.position.y + 1.0, fe.position.z, 2, 0.2, 0.2, 0.2, 0);
                    }
                }
            }
        }
    }

    public static boolean isEntityFrozen(Entity entity) {
        return entity != null && FREEZE_COUNTS.containsKey(entity);
    }

    /** Returns the earliest saved velocity when this entity is already inside a Time Stop. */
    public static Vec3d getFrozenVelocity(Entity entity) {
        if (entity == null || !(entity.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return null;
        Power registered = PowerManager.get(world.getServer()).getPowerInstance(PowerType.TIME_WARPER);
        if (!(registered instanceof TimeWarperPower warper)) return null;
        for (List<FrozenEntity> list : warper.frozenEntities.values()) {
            for (FrozenEntity frozen : list) {
                if (frozen.entity == entity) return frozen.velocity;
            }
        }
        return null;
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        long currentTicks = player.getEntityWorld().getServer().getTicks();
        long cooldown = Math.max(0, YPConfig.getInstance().time_stop_cooldown_ticks);
        Long lastUse = timeStopCooldown.get(uuid);
        if (lastUse != null && currentTicks - lastUse < cooldown) {
            player.sendMessage(Text.literal("Time Stop on cooldown!" + " " + ((cooldown - (currentTicks - lastUse)) / 20) + "s"), true);
            return;
        }
        if (timeStopActive.containsKey(uuid)) {
            player.sendMessage(Text.literal("Time Stop is already active."), true);
            return;
        }

        timeStopCooldown.put(uuid, currentTicks);
        timeStopActive.put(uuid, YPConfig.getInstance().time_stop_max_duration_ticks);
        List<FrozenEntity> frozen = new ArrayList<>();
        AllianceManager alliances = AllianceManager.get(player.getEntityWorld().getServer());
        for (ServerWorld world : player.getEntityWorld().getServer().getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity == player || entity.isRemoved()) continue;
                if (entity instanceof ServerPlayerEntity other && alliances.isAlly(player, other)) continue;
                frozen.add(new FrozenEntity(entity));
                retainFrozen(entity);
            }
        }
        frozenEntities.put(uuid, frozen);
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.literal("Time Stop!"), true);
    }

    @Override
    public void onSecondaryActivate(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        UUID playerId = player.getUuid();
        long currentTicks = server.getTicks();
        int cooldownTicks = Math.max(0, YPConfig.getInstance().time_warp_rewind_cooldown_ticks);
        Long lastUse = timeWarpCooldown.get(playerId);
        if (cooldownTicks > 0 && lastUse != null && currentTicks - lastUse < cooldownTicks) {
            long remaining = cooldownTicks - (currentTicks - lastUse);
            player.sendMessage(Text.literal("Time Warp on cooldown: " + ((remaining + 19) / 20) + "s"), true);
            return;
        }
        boolean rewoundAny = false;
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            LinkedList<PlayerSnapshot> history = playerHistories.get(target.getUuid());
            if (history == null || history.size() <= REWIND_TICKS) {
                if (target == player) player.sendMessage(Text.literal("Not enough history yet; Time Warp needs 25 seconds of recorded play."), true);
                continue;
            }
            PlayerSnapshot snapshot = history.getFirst();
            ServerWorld targetWorld = server.getWorld(snapshot.dimension);
            if (targetWorld == null) continue;
            target.teleport(targetWorld, snapshot.x, snapshot.y, snapshot.z, Set.of(), snapshot.yaw, snapshot.pitch, true);
            rewoundAny = true;
            target.setHealth(Math.min(snapshot.health, target.getMaxHealth()));
            target.setVelocity(Vec3d.ZERO);
            ((com.yppowers.mixin.EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
            for (List<FrozenEntity> frozenList : frozenEntities.values()) {
                for (FrozenEntity frozen : frozenList) {
                    if (frozen.entity == target) {
                        frozen.position = target.getEntityPos();
                        frozen.velocity = Vec3d.ZERO;
                        frozen.yaw = snapshot.yaw;
                        frozen.pitch = snapshot.pitch;
                    }
                }
            }
            targetWorld.playSound(null, target.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
            targetWorld.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + 1.0, target.getZ(), 24, 0.5, 0.8, 0.5, 0.1);
            if (target != player) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 1));
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 120, 0));
            }
        }
        if (rewoundAny && cooldownTicks > 0) timeWarpCooldown.put(playerId, currentTicks);
        player.sendMessage(Text.literal("Time Warp! Rewound all players by 25 seconds."), true);
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, float amount) {
        return timeStopActive.containsKey(player.getUuid()) ? amount * 0.5f : amount;
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        timeStopActive.remove(uuid);
        timeWarpCooldown.remove(uuid);
        List<FrozenEntity> frozen = frozenEntities.remove(uuid);
        if (frozen != null) {
            for (FrozenEntity fe : frozen) {
                releaseFrozen(fe.entity);
                if (fe.entity.isAlive() && !fe.entity.isRemoved()
                        && !FREEZE_COUNTS.containsKey(fe.entity)
                        && !SeriousPunchPower.isEntityFrozen(fe.entity)
                        && !IceEmperorPower.isEntityFrozen(fe.entity)) {
                    fe.entity.setVelocity(fe.velocity);
                    ((com.yppowers.mixin.EntityVelocityAccessor) fe.entity).invokeYppowersScheduleVelocityUpdate();
                }
            }
        }
    }
}
