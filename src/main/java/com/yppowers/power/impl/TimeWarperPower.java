package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.network.YPNetworking;
import com.yppowers.energy.EnergyManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
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
    private final Map<UUID, BlackHoleState> blackHoles = new HashMap<>();
    private static final Map<Entity, Integer> FREEZE_COUNTS = new IdentityHashMap<>();
    /** Reference counts let multiple overlapping Time Stops keep the same player's gray tint active. */
    private static final Map<Integer, Integer> TIME_STOP_VISUAL_COUNTS = new HashMap<>();

    private static void retainFrozen(Entity entity) { FREEZE_COUNTS.merge(entity, 1, Integer::sum); }

    private static void retainTimeStopVisual(Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        int entityId = player.getId();
        int count = TIME_STOP_VISUAL_COUNTS.getOrDefault(entityId, 0);
        TIME_STOP_VISUAL_COUNTS.put(entityId, count + 1);
        if (count == 0) {
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server != null) YPNetworking.broadcastTimeStopVisual(server, entityId, true);
        }
    }

    private static void releaseTimeStopVisual(Entity entity) {
        if (!(entity instanceof ServerPlayerEntity player)) return;
        int entityId = player.getId();
        Integer count = TIME_STOP_VISUAL_COUNTS.get(entityId);
        if (count == null || count <= 1) {
            TIME_STOP_VISUAL_COUNTS.remove(entityId);
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server != null) YPNetworking.broadcastTimeStopVisual(server, entityId, false);
        } else {
            TIME_STOP_VISUAL_COUNTS.put(entityId, count - 1);
        }
    }

    /** Re-sync active Time Stop tinting to clients that join while a stop is already active. */
    public static void syncTimeStopVisualsTo(ServerPlayerEntity recipient) {
        if (recipient == null) return;
        for (int entityId : TIME_STOP_VISUAL_COUNTS.keySet()) YPNetworking.syncTimeStopVisualTo(recipient, entityId);
    }

    /** Prevents stale entity IDs from carrying into a later integrated/server session. */
    public static void clearTimeStopVisualState() {
        TIME_STOP_VISUAL_COUNTS.clear();
    }
    private static void releaseFrozen(Entity entity) {
        Integer count = FREEZE_COUNTS.get(entity);
        if (count == null || count <= 1) FREEZE_COUNTS.remove(entity);
        else FREEZE_COUNTS.put(entity, count - 1);
    }

    private static final class BlackHoleState {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d center;
        final List<BlockPos> terrain;
        int elapsedTicks;
        int terrainCursor;
        boolean imploded;
        BlackHoleState(UUID ownerId, ServerWorld world, Vec3d center, List<BlockPos> terrain) {
            this.ownerId = ownerId;
            this.world = world;
            this.center = center;
            this.terrain = terrain;
        }
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
                        releaseTimeStopVisual(fe.entity);
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
                        retainTimeStopVisual(entity);
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
        tickBlackHoles(server);
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
                retainTimeStopVisual(entity);
            }
        }
        frozenEntities.put(uuid, frozen);
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0f, 1.0f);
        player.sendMessage(Text.literal("Time Stop!"), true);
    }

    @Override
    public void onTertiaryActivate(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || !(player.getEntityWorld() instanceof ServerWorld world)) return;
        if (blackHoles.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("A Black Hole is already charging.").formatted(Formatting.YELLOW), true);
            return;
        }
        if (com.yppowers.command.GraceManager.isActive(server)) {
            com.yppowers.command.GraceManager.blockAbility(player);
            return;
        }
        YPConfig config = YPConfig.getInstance();
        Vec3d look = player.getRotationVec(1.0f).normalize();
        Vec3d center = player.getEyePos().add(look.multiply(8.0));
        List<BlockPos> terrain = new ArrayList<>();
        int radius = MathHelper.ceil(config.time_warper_black_hole_terrain_radius);
        BlockPos origin = BlockPos.ofFloored(center);
        if (config.time_warper_black_hole_terrain_damage && config.time_warper_black_hole_max_blocks_per_tick > 0) {
            double r2 = config.time_warper_black_hole_terrain_radius * config.time_warper_black_hole_terrain_radius;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if ((double) x*x + (double) y*y + (double) z*z <= r2) terrain.add(origin.add(x, y, z).toImmutable());
                    }
                }
            }
            terrain.sort(Comparator.comparingDouble(pos -> pos.getSquaredDistance(center.x, center.y, center.z)));
        }
        blackHoles.put(player.getUuid(), new BlackHoleState(player.getUuid(), world, center, terrain));
        EnergyManager.get(server).startBlackHoleCooldown(player);
        double sacrifice = Math.max(0.0, Math.min(0.95, config.time_warper_black_hole_health_sacrifice_percent));
        player.setHealth(Math.max(1.0f, (float) (player.getHealth() * (1.0 - sacrifice))));
        player.sendMessage(Text.literal("BLACK HOLE — reality is collapsing...").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    private void tickBlackHoles(MinecraftServer server) {
        if (blackHoles.isEmpty()) return;
        Iterator<Map.Entry<UUID, BlackHoleState>> iterator = blackHoles.entrySet().iterator();
        YPConfig config = YPConfig.getInstance();
        while (iterator.hasNext()) {
            BlackHoleState hole = iterator.next().getValue();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(hole.ownerId);
            if (owner == null || !owner.isAlive() || owner.getEntityWorld().getServer() == null
                    || !owner.getEntityWorld().getRegistryKey().equals(hole.world.getRegistryKey())
                    || PowerManager.get(server).getPowerType(owner.getUuid()) != PowerType.TIME_WARPER
                    || com.yppowers.command.GraceManager.isActive(server)) {
                iterator.remove();
                continue;
            }
            int charge = Math.max(1, config.time_warper_black_hole_charge_ticks);
            int pull = Math.max(1, config.time_warper_black_hole_pull_ticks);
            if (hole.imploded) {
                processBlackHoleTerrain(hole, owner, config);
                if (!config.time_warper_black_hole_terrain_damage || hole.terrainCursor >= hole.terrain.size()) iterator.remove();
                continue;
            }
            hole.elapsedTicks++;
            boolean charging = hole.elapsedTicks <= charge;
            double progress = charging ? hole.elapsedTicks / (double) charge
                    : Math.min(1.0, (hole.elapsedTicks - charge) / (double) pull);
            double swirlRadius = 0.6 + progress * Math.max(1.0, config.time_warper_black_hole_entity_radius * 0.35);
            for (int i = 0; i < 20; i++) {
                double angle = (server.getTicks() * 0.12) + i * Math.PI * 2.0 / 20.0;
                double y = hole.center.y + Math.sin(angle * 2.0) * (0.4 + progress * 2.0);
                hole.world.spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        hole.center.x + Math.cos(angle) * swirlRadius, y,
                        hole.center.z + Math.sin(angle) * swirlRadius, 1, 0.06, 0.06, 0.06, 0.02);
            }
            hole.world.spawnParticles(ParticleTypes.LARGE_SMOKE, hole.center.x, hole.center.y, hole.center.z,
                    charging ? 3 : 7, 0.35, 0.35, 0.35, 0.015);
            if (charging) {
                // Begin bounded terrain processing during the charge so ordinary settings can
                // finish the eligible terrain work by the implosion rather than deleting blocks
                // in an unbounded burst at the end.
                processBlackHoleTerrain(hole, owner, config);
                if (hole.elapsedTicks % 10 == 0) {
                    hole.world.playSound(null, BlockPos.ofFloored(hole.center), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                            SoundCategory.PLAYERS, 0.8f, (float) (1.5 - progress * 0.8));
                }
                continue;
            }
            applyBlackHolePull(hole, owner, progress, config);
            processBlackHoleTerrain(hole, owner, config);
            if (hole.elapsedTicks >= charge + pull) {
                hole.imploded = true;
                finishBlackHole(hole, owner, config);
                if (!config.time_warper_black_hole_terrain_damage || hole.terrainCursor >= hole.terrain.size()) iterator.remove();
            }
        }
    }

    private void applyBlackHolePull(BlackHoleState hole, ServerPlayerEntity owner, double progress, YPConfig config) {
        double radius = Math.max(1.0, config.time_warper_black_hole_entity_radius);
        Box area = new Box(hole.center.x - radius, hole.center.y - radius, hole.center.z - radius,
                hole.center.x + radius, hole.center.y + radius, hole.center.z + radius);
        for (Entity entity : hole.world.getEntitiesByClass(Entity.class, area,
                e -> e != owner && !e.isRemoved() && (e instanceof LivingEntity || (config.time_warper_black_hole_affect_items && e instanceof ItemEntity)))) {
            if (entity.squaredDistanceTo(hole.center) > radius * radius) continue;
            if (entity instanceof ServerPlayerEntity && !config.time_warper_black_hole_affect_players) continue;
            if (entity instanceof MobEntity && !config.time_warper_black_hole_affect_mobs) continue;
            if (!(entity instanceof ServerPlayerEntity) && !(entity instanceof MobEntity) && !(entity instanceof ItemEntity)) continue;
            if (entity instanceof ServerPlayerEntity other && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other)) continue;
            Vec3d direction = hole.center.subtract(entity.getEntityPos());
            if (direction.lengthSquared() < 1.0e-5) continue;
            Vec3d impulse = direction.normalize().multiply(0.035 + progress * 0.12);
            Vec3d velocity = entity.getVelocity().add(impulse);
            double max = 1.6;
            if (velocity.lengthSquared() > max * max) velocity = velocity.normalize().multiply(max);
            entity.setVelocity(velocity);
            ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
            if (hole.elapsedTicks % 4 == 0) {
                hole.world.spawnParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + entity.getHeight() * 0.5,
                        entity.getZ(), 2, 0.15, 0.2, 0.15, 0.02);
            }
        }
    }

    private void processBlackHoleTerrain(BlackHoleState hole, ServerPlayerEntity owner, YPConfig config) {
        if (!config.time_warper_black_hole_terrain_damage || config.time_warper_black_hole_max_blocks_per_tick <= 0) return;
        int processed = 0;
        int max = Math.max(0, Math.min(512, config.time_warper_black_hole_max_blocks_per_tick));
        while (processed < max && hole.terrainCursor < hole.terrain.size()) {
            BlockPos pos = hole.terrain.get(hole.terrainCursor++);
            processed++;
            if (!hole.world.isChunkLoaded(ChunkPos.toLong(pos))) continue;
            BlockState state = hole.world.getBlockState(pos);
            if (state.isAir() || state.isOf(Blocks.BEDROCK) || state.isOf(Blocks.END_PORTAL_FRAME)
                    || state.isOf(Blocks.HEAVY_CORE) || state.getHardness(hole.world, pos) < 0.0f) continue;
            // Containers, signs, spawners and any other block entities are excluded so their
            // inventories/contents are never duplicated as drops.
            BlockEntity blockEntity = hole.world.getBlockEntity(pos);
            if (blockEntity != null) continue;
            if (pos.getSquaredDistance(hole.center.x, hole.center.y, hole.center.z) > config.time_warper_black_hole_terrain_radius * config.time_warper_black_hole_terrain_radius) continue;
            hole.world.breakBlock(pos, true, owner);
        }
    }

    private void finishBlackHole(BlackHoleState hole, ServerPlayerEntity owner, YPConfig config) {
        double radius = Math.max(1.0, config.time_warper_black_hole_implosion_radius);
        Box area = new Box(hole.center.x-radius, hole.center.y-radius, hole.center.z-radius,
                hole.center.x+radius, hole.center.y+radius, hole.center.z+radius);
        float damage = (float) Math.max(0.0, Math.min(10.0, config.time_warper_black_hole_max_damage));
        for (LivingEntity target : hole.world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != owner && !e.isRemoved() && e.isAlive()
                        && (e instanceof ServerPlayerEntity || e instanceof MobEntity))) {
            if (target.squaredDistanceTo(hole.center) > radius * radius) continue;
            if (target instanceof ServerPlayerEntity && !config.time_warper_black_hole_affect_players) continue;
            if (target instanceof MobEntity && !config.time_warper_black_hole_affect_mobs) continue;
            if (target instanceof ServerPlayerEntity other && AllianceManager.get(hole.world.getServer()).isAlly(owner, other)) continue;
            if (damage > 0.0f) target.damage(hole.world, hole.world.getDamageSources().magic(), damage);
            Vec3d direction = hole.center.subtract(target.getEntityPos());
            if (direction.lengthSquared() > 1.0e-5) target.setVelocity(direction.normalize().multiply(0.95));
        }
        hole.world.spawnParticles(ParticleTypes.EXPLOSION, hole.center.x, hole.center.y, hole.center.z, 1, 0, 0, 0, 0);
        hole.world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFF), hole.center.x, hole.center.y, hole.center.z, 1, 0, 0, 0, 0);
        hole.world.spawnParticles(ParticleTypes.REVERSE_PORTAL, hole.center.x, hole.center.y, hole.center.z, 160, radius * 0.45, radius * 0.45, radius * 0.45, 0.15);
        hole.world.playSound(null, BlockPos.ofFloored(hole.center), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0f, 0.45f);
        owner.sendMessage(Text.literal("Black Hole imploded.").formatted(Formatting.DARK_PURPLE), true);
    }

    public boolean isBlackHoleCharging(UUID id) { return blackHoles.containsKey(id); }

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
        blackHoles.remove(uuid);
        List<FrozenEntity> frozen = frozenEntities.remove(uuid);
        if (frozen != null) {
            for (FrozenEntity fe : frozen) {
                releaseFrozen(fe.entity);
                releaseTimeStopVisual(fe.entity);
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
