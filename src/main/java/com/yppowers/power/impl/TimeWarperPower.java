package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.network.YPNetworking;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.YPItemData;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
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
    private final Map<UUID, Deque<PlayerSnapshot>> playerHistories = new HashMap<>();
    private final Map<UUID, BlackHoleState> blackHoles = new HashMap<>();
    private static final Map<UUID, Integer> TIME_SHARD_LIFETIME = new HashMap<>();
    private static final class TimeClone { final ZombieEntity entity; final boolean elite; int ticks;
        TimeClone(ZombieEntity entity, boolean elite, int ticks) { this.entity=entity; this.elite=elite; this.ticks=ticks; } }
    private final Map<UUID, List<TimeClone>> timeClones = new HashMap<>();
    private final Map<UUID, Entity> lastOwnerTargets = new HashMap<>();
    private final List<BlackHoleImplosionVfx> blackHoleImplosionVfx = new ArrayList<>();
    private static final int BLACK_HOLE_IMPLOSION_VFX_TICKS = 5;
    private static final TintedParticleEffect BLACK_HOLE_DARK_PARTICLE =
            TintedParticleEffect.create(ParticleTypes.ENTITY_EFFECT, 0x08080C);
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
        final BlockPos terrainOrigin;
        final int terrainRadiusBlocks;
        final double terrainRadiusSquared;
        int elapsedTicks;
        int terrainShell;
        int terrainX;
        int terrainY;
        int terrainZ;
        boolean terrainFinished;
        boolean imploded;

        BlackHoleState(UUID ownerId, ServerWorld world, Vec3d center, double terrainRadius, boolean terrainEnabled) {
            this.ownerId = ownerId;
            this.world = world;
            this.center = center;
            this.terrainOrigin = BlockPos.ofFloored(center);
            this.terrainRadiusBlocks = Math.max(0, (int) Math.ceil(terrainRadius));
            this.terrainRadiusSquared = Math.max(0.0, terrainRadius) * Math.max(0.0, terrainRadius);
            this.terrainShell = 0;
            this.terrainX = 0;
            this.terrainY = 0;
            this.terrainZ = 0;
            this.terrainFinished = !terrainEnabled;
        }

        /** Generate the same spherical block set lazily in near-to-far shells, avoiding a large list and sort on cast. */
        BlockPos nextTerrainPosition() {
            while (!terrainFinished) {
                int x = terrainX;
                int y = terrainY;
                int z = terrainZ;
                advanceTerrainCursor();
                if ((double) x * x + (double) y * y + (double) z * z > terrainRadiusSquared + 1.0e-7) continue;
                return terrainOrigin.add(x, y, z).toImmutable();
            }
            return null;
        }

        private void advanceTerrainCursor() {
            // Walk only the current cube shell: full Z rows on X/Y faces, and only the two Z ends
            // on interior rows. This keeps near-center blocks first without sorting a large list.
            if (Math.abs(terrainX) == terrainShell || Math.abs(terrainY) == terrainShell) {
                if (terrainZ < terrainShell) {
                    terrainZ++;
                    return;
                }
            } else {
                if (terrainZ == -terrainShell) {
                    terrainZ = terrainShell;
                    return;
                }
            }

            terrainZ = -terrainShell;
            if (terrainY < terrainShell) {
                terrainY++;
                return;
            }
            terrainY = -terrainShell;
            if (terrainX < terrainShell) {
                terrainX++;
                return;
            }
            terrainShell++;
            if (terrainShell > terrainRadiusBlocks) {
                terrainFinished = true;
                return;
            }
            terrainX = -terrainShell;
            terrainY = -terrainShell;
            terrainZ = -terrainShell;
        }
    }

    /** Client-only presentation state; it never keeps the ability or its effects active. */
    private static final class BlackHoleImplosionVfx {
        final ServerWorld world;
        final Vec3d center;
        final double radius;
        int age;

        BlackHoleImplosionVfx(ServerWorld world, Vec3d center, double radius) {
            this.world = world;
            this.center = center;
            this.radius = radius;
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
        // Histories are a fixed-size FIFO. ArrayDeque avoids allocating a linked-list node for
        // every player snapshot every tick. Offline history cleanup runs once per second; this
        // only delays stale-memory cleanup, not rewind behavior (rewind only iterates online players).
        if (server.getTicks() % 20 == 0) {
            Set<UUID> onlineIds = new HashSet<>();
            for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) onlineIds.add(online.getUuid());
            playerHistories.keySet().retainAll(onlineIds);
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            Deque<PlayerSnapshot> history = playerHistories.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>(REWIND_TICKS + 1));
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
        tickTimeClones(server);
        tickTimeShards(server);
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
        if (isRealityForm(player)) { summonTimeClone(player, false); return; }
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
        boolean terrainEnabled = config.time_warper_black_hole_terrain_damage
                && config.time_warper_black_hole_max_blocks_per_tick > 0;
        // Candidate block positions are now generated under the existing per-tick budget instead of
        // allocating and sorting a potentially very large sphere synchronously on ability activation.
        blackHoles.put(player.getUuid(), new BlackHoleState(player.getUuid(), world, center,
                config.time_warper_black_hole_terrain_radius, terrainEnabled));
        EnergyManager.get(server).startBlackHoleCooldown(player);
        double sacrifice = Math.max(0.0, Math.min(0.95, config.time_warper_black_hole_health_sacrifice_percent));
        player.setHealth(Math.max(1.0f, (float) (player.getHealth() * (1.0 - sacrifice))));
        player.sendMessage(Text.literal("BLACK HOLE — reality is collapsing...").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 2.0f, 0.5f);
    }

    private void tickBlackHoles(MinecraftServer server) {
        if (blackHoles.isEmpty()) {
            tickBlackHoleImplosionVfx();
            return;
        }
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
                if (!config.time_warper_black_hole_terrain_damage || hole.terrainFinished) iterator.remove();
                continue;
            }
            hole.elapsedTicks++;
            boolean charging = hole.elapsedTicks <= charge;
            double progress = charging ? hole.elapsedTicks / (double) charge
                    : Math.min(1.0, (hole.elapsedTicks - charge) / (double) pull);
            // Visual progress spans both charge and pull so the sphere grows continuously;
            // the original `progress` still drives the gameplay pull below unchanged.
            double visualProgress = Math.min(1.0, hole.elapsedTicks / (double) (charge + pull));
            double maxVisualRadius = Math.max(2.5, config.time_warper_black_hole_entity_radius * 0.45);
            double ballRadius = 0.35 + maxVisualRadius * Math.sqrt(visualProgress);
            boolean visualFrame = hole.elapsedTicks % 3 == 0;
            if (visualFrame) {
                hole.world.spawnParticles(BLACK_HOLE_DARK_PARTICLE,
                        hole.center.x, hole.center.y, hole.center.z, 28,
                        ballRadius * 0.34, ballRadius * 0.34, ballRadius * 0.34, 0.003);
                hole.world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                        hole.center.x, hole.center.y, hole.center.z, 14,
                        ballRadius * 0.42, ballRadius * 0.42, ballRadius * 0.42, 0.004);
                hole.world.spawnParticles(ParticleTypes.ASH,
                        hole.center.x, hole.center.y, hole.center.z, 8,
                        ballRadius * 0.36, ballRadius * 0.36, ballRadius * 0.36, 0.002);
            }
            // Five points trace the growing 3-D spiral on every third tick.
            if (visualFrame) {
                double rotation = server.getTicks() * 0.24;
                for (int i = 0; i < 5; i++) {
                    double fraction = (i + 1.0) / 5.0;
                    double angle = rotation + fraction * Math.PI * 4.0;
                    double orbit = ballRadius * (0.2 + 0.78 * fraction);
                    double particleY = hole.center.y + Math.sin(angle * 0.72) * ballRadius * 0.58;
                    hole.world.spawnParticles(BLACK_HOLE_DARK_PARTICLE,
                            hole.center.x + Math.cos(angle) * orbit, particleY,
                            hole.center.z + Math.sin(angle) * orbit, 1,
                            0.03, 0.03, 0.03, 0.004);
                }
            }
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
                if (!config.time_warper_black_hole_terrain_damage || hole.terrainFinished) iterator.remove();
            }
        }
        tickBlackHoleImplosionVfx();
    }

    /** Runs only particles after the gameplay implosion has already resolved. */
    private void tickBlackHoleImplosionVfx() {
        if (blackHoleImplosionVfx.isEmpty()) return;
        Iterator<BlackHoleImplosionVfx> iterator = blackHoleImplosionVfx.iterator();
        while (iterator.hasNext()) {
            BlackHoleImplosionVfx effect = iterator.next();
            int frame = effect.age;
            double progress = frame / (double) (BLACK_HOLE_IMPLOSION_VFX_TICKS - 1);
            double radius = effect.radius * (1.0 - progress);
            double spread = Math.max(0.015, radius * 0.38);
            effect.world.spawnParticles(BLACK_HOLE_DARK_PARTICLE,
                    effect.center.x, effect.center.y, effect.center.z,
                    32 - frame * 5, spread, spread, spread, 0.002);
            effect.world.spawnParticles(ParticleTypes.LARGE_SMOKE,
                    effect.center.x, effect.center.y, effect.center.z,
                    18 - frame * 3, spread * 1.1, spread * 1.1, spread * 1.1, 0.001);
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI / 2.0 + frame * 0.72;
                double orbit = radius * (0.35 + 0.08 * (i % 3));
                double y = effect.center.y + Math.sin(angle * 1.4) * radius * 0.3;
                effect.world.spawnParticles(BLACK_HOLE_DARK_PARTICLE,
                        effect.center.x + Math.cos(angle) * orbit, y,
                        effect.center.z + Math.sin(angle) * orbit,
                        1, 0.01, 0.01, 0.01, 0.001);
            }
            if (frame >= BLACK_HOLE_IMPLOSION_VFX_TICKS - 1) {
                effect.world.spawnParticles(ParticleTypes.EXPLOSION,
                        effect.center.x, effect.center.y, effect.center.z, 1, 0, 0, 0, 0);
                EpicVfx.impact(effect.world, effect.center.x, effect.center.y, effect.center.z, 1.6);
                effect.world.spawnParticles(TintedParticleEffect.create(ParticleTypes.FLASH, 0xFFFFFF),
                        effect.center.x, effect.center.y, effect.center.z, 1, 0, 0, 0, 0);
                effect.world.spawnParticles(ParticleTypes.END_ROD,
                        effect.center.x, effect.center.y, effect.center.z, 36, 0.45, 0.45, 0.45, 0.08);
                iterator.remove();
            } else {
                effect.age++;
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
        while (processed < max && !hole.terrainFinished) {
            BlockPos pos = hole.nextTerrainPosition();
            if (pos == null) break;
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
        // Gameplay damage/knockback still resolves immediately above. Only the visual finish
        // is delayed for five ticks so the dark sphere visibly collapses before the white flash.
        double vfxRadius = Math.max(2.5, config.time_warper_black_hole_entity_radius * 0.45);
        blackHoleImplosionVfx.add(new BlackHoleImplosionVfx(hole.world, hole.center, vfxRadius));
        hole.world.playSound(null, BlockPos.ofFloored(hole.center), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0f, 0.45f);
        owner.sendMessage(Text.literal("Black Hole imploded.").formatted(Formatting.DARK_PURPLE), true);
    }

    public boolean isBlackHoleCharging(UUID id) { return blackHoles.containsKey(id); }

    public boolean isRealityForm(ServerPlayerEntity player) {
        return player != null && player.getEntityWorld().getServer() != null
                && "REALITY".equals(PowerManager.get(player.getEntityWorld().getServer()).getTimeWarperForm(player.getUuid()));
    }

    @Override public boolean canUseKey(ServerPlayerEntity player, int keyType) {
        if (player == null || player.getEntityWorld().getServer() == null) return false;
        boolean reality = isRealityForm(player);
        if (keyType == 4) {
            int cd = PowerManager.get(player.getEntityWorld().getServer()).getTimeWarperFormCooldownTicks(player.getUuid());
            if (cd > 0) { player.sendMessage(Text.literal("Form switch cooldown: " + ((cd + 19) / 20) + "s"), true); return false; }
            return true;
        }
        if (!reality) return keyType == 0 || keyType == 1;
        if (keyType == 0 || keyType == 3) {
            List<TimeClone> clones = timeClones.getOrDefault(player.getUuid(), Collections.emptyList());
            int living = (int)clones.stream().filter(c -> c.entity.isAlive() && !c.entity.isRemoved()).count();
            if (living >= Math.max(1, YPConfig.getInstance().time_warper_clone_max_active)) {
                player.sendMessage(Text.literal("Time Clone cap reached (" + YPConfig.getInstance().time_warper_clone_max_active + ")."), true); return false;
            }
            if (keyType == 0 && PowerManager.get(player.getEntityWorld().getServer()).getTimeShards(player.getUuid())
                    < Math.max(0, YPConfig.getInstance().time_warper_clone_shards_cost)) {
                player.sendMessage(Text.literal("Not enough Time Shards. Collect them by physically touching the black shards."), true); return false;
            }
            if (keyType == 3 && !hasGoldenShard(player)) {
                player.sendMessage(Text.literal("An inventory Golden Time Shard is required for an Elite Clone."), true); return false;
            }
            return true;
        }
        return keyType == 2;
    }

    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer(); if (server == null) return;
        PowerManager data = PowerManager.get(server); UUID id = player.getUuid();
        int cooldown = data.getTimeWarperFormCooldownTicks(id);
        if (cooldown > 0) { player.sendMessage(Text.literal("Form switch cooldown: " + ((cooldown+19)/20) + "s"), true); return; }
        String next = isRealityForm(player) ? "TIME" : "REALITY";
        data.setTimeWarperForm(id, next);
        data.setTimeWarperFormCooldownTicks(id, Math.max(0, YPConfig.getInstance().time_warper_form_switch_cooldown_ticks));
        player.sendMessage(Text.literal("Time Warper form switched to " + next + ". Ability cooldowns and summons are unchanged.")
                .formatted(next.equals("TIME") ? Formatting.AQUA : Formatting.DARK_PURPLE, Formatting.BOLD), true);
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0f, next.equals("TIME") ? 1.2f : .7f);
    }

    @Override public void onQuaternaryActivate(ServerPlayerEntity player) { summonTimeClone(player, true); }

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
            Deque<PlayerSnapshot> history = playerHistories.get(target.getUuid());
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
        // The player no longer has Time Warper, so clean up its dependent summons as well.
        List<TimeClone> ownedClones = timeClones.remove(uuid);
        if (ownedClones != null) for (TimeClone clone : ownedClones) clone.entity.discard();
        lastOwnerTargets.remove(uuid);
    }

    private void summonTimeClone(ServerPlayerEntity owner, boolean elite) {
        if (!(owner.getEntityWorld() instanceof ServerWorld world) || owner.getEntityWorld().getServer() == null) return;
        UUID id = owner.getUuid(); PowerManager data = PowerManager.get(world.getServer());
        if (elite && !hasGoldenShard(owner)) return;
        if (!elite && data.getTimeShards(id) < Math.max(0, YPConfig.getInstance().time_warper_clone_shards_cost)) return;
        ZombieEntity clone = EntityType.ZOMBIE.create(world, SpawnReason.TRIGGERED);
        if (clone == null) { owner.sendMessage(Text.literal("The clone could not manifest."), true); return; }
        List<TimeClone> list = timeClones.computeIfAbsent(id, ignored -> new ArrayList<>());
        clone.refreshPositionAndAngles(owner.getX() + (list.size()%5-2)*1.2, owner.getY(), owner.getZ() + 1.5 + (list.size()/5)*.8, owner.getYaw(), 0);
        clone.setCustomName(Text.literal("YPCLONE|" + id + "|" + (elite ? "G" : "N")));
        clone.setCustomNameVisible(false); clone.addCommandTag("yppowers_time_clone"); clone.addCommandTag("yppowers_owner_" + id); if (elite) clone.addCommandTag("yppowers_time_clone_golden"); clone.setPersistent();
        clone.setCanPickUpLoot(false);
        clone.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
        if (elite) {
            clone.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            clone.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            if (clone.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH) != null)
                clone.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(40.0);
            clone.setHealth(40.0f);
        } else clone.setHealth(clone.getMaxHealth());
        if (elite) { if (!consumeGoldenShard(owner)) { clone.discard(); return; } }
        else if (!data.spendTimeShards(id, Math.max(0, YPConfig.getInstance().time_warper_clone_shards_cost))) { clone.discard(); return; }
        world.spawnEntity(clone);
        list.add(new TimeClone(clone, elite, Math.max(20, YPConfig.getInstance().time_warper_clone_lifespan_ticks)));
        world.spawnParticles(ParticleTypes.PORTAL, clone.getX(), clone.getBodyY(.5), clone.getZ(), elite ? 40 : 18, .35, .5, .35, .2);
        world.playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, .8f, elite ? .6f : 1.3f);
        owner.sendMessage(Text.literal(elite ? "Elite Time Clone summoned." : "Time Clone summoned. Shards remaining: " + data.getTimeShards(id)), true);
    }

    private boolean hasGoldenShard(ServerPlayerEntity player) {
        for (int i=0;i<player.getInventory().size();i++) if (YPItemData.hasFlag(player.getInventory().getStack(i), "YPGoldenTimeShard")) return true;
        return false;
    }

    private boolean consumeGoldenShard(ServerPlayerEntity player) {
        for (int i=0;i<player.getInventory().size();i++) {
            ItemStack stack=player.getInventory().getStack(i);
            if(YPItemData.hasFlag(stack,"YPGoldenTimeShard")){stack.decrement(1);return true;}
        }
        return false;
    }

    @Override public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (target != null && target.isAlive() && PowerCombatUtil.canHarm(attacker, target)) lastOwnerTargets.put(attacker.getUuid(), target);
    }

    private void tickTimeClones(MinecraftServer server) {
        PowerManager data=PowerManager.get(server);
        Iterator<Map.Entry<UUID,List<TimeClone>>> owners=timeClones.entrySet().iterator();
        while(owners.hasNext()) {
            Map.Entry<UUID,List<TimeClone>> entry=owners.next(); UUID ownerId=entry.getKey();
            ServerPlayerEntity owner=server.getPlayerManager().getPlayer(ownerId);
            if(owner==null) {
                List<NbtCompound> snapshots=new ArrayList<>();
                for(TimeClone c:entry.getValue()) if(c.entity.isAlive()&&!c.entity.isRemoved()&&c.ticks>0) {
                    NbtCompound nbt=new NbtCompound();nbt.putString("Variant",c.elite?"ELITE":"ORDINARY");nbt.putInt("Ticks",c.ticks);snapshots.add(nbt);c.entity.discard();
                }
                data.saveStoredTimeClones(ownerId,snapshots);owners.remove();continue;
            }
            List<TimeClone> list=entry.getValue();
            list.removeIf(c->{if(c.entity.isRemoved()||!c.entity.isAlive()||--c.ticks<=0){c.entity.discard();return true;}return false;});
            if(list.isEmpty()){owners.remove();continue;}
            Entity last=lastOwnerTargets.get(ownerId);
            for(TimeClone c:list) {
                if(c.ticks%10!=0)continue;
                Entity target=last!=null&&last.isAlive()&&last.getEntityWorld()==owner.getEntityWorld()&&PowerCombatUtil.canHarm(owner,last)?last:null;
                if(target==null)target=PowerCombatUtil.nearestTarget(owner,24.0);
                if(target instanceof LivingEntity living)c.entity.setTarget(living);else c.entity.setTarget(null);
                if(target==null&&c.entity.squaredDistanceTo(owner)>100)c.entity.getNavigation().startMovingTo(owner,1.15);
                else if(target!=null)c.entity.getNavigation().stop();
                if (c.elite && c.ticks % 5 == 0 && c.entity.getEntityWorld() instanceof ServerWorld cloneWorld) {
                    cloneWorld.spawnParticles(ParticleTypes.PORTAL, c.entity.getX(), c.entity.getY() + 1.0, c.entity.getZ(), 0, 0.0, 0.04, 0.0, 1.0);
                }
            }
        }
    }

    @Override public void onPlayerJoin(ServerPlayerEntity player) { restoreStoredClones(player); }

    private void restoreStoredClones(ServerPlayerEntity owner) {
        if(owner.getEntityWorld().getServer()==null||!(owner.getEntityWorld() instanceof ServerWorld world))return;
        List<NbtCompound> saved=PowerManager.get(world.getServer()).takeStoredTimeClones(owner.getUuid());
        List<TimeClone> list=timeClones.computeIfAbsent(owner.getUuid(),ignored->new ArrayList<>());
        int index=0;
        for(NbtCompound nbt:saved) {
            if(list.size()>=Math.max(1,YPConfig.getInstance().time_warper_clone_max_active))break;
            boolean elite=nbt.getString("Variant","").equals("ELITE");int ticks=Math.max(20,nbt.getInt("Ticks",20));
            ZombieEntity clone=EntityType.ZOMBIE.create(world,SpawnReason.TRIGGERED);if(clone==null)continue;
            clone.refreshPositionAndAngles(owner.getX()+(index%5-2)*1.2,owner.getY(),owner.getZ()+1.5+(index/5)*.8,owner.getYaw(),0);
            clone.setCustomName(Text.literal("YPCLONE|"+owner.getUuid()+"|"+(elite?"G":"N")));
            clone.addCommandTag("yppowers_time_clone");clone.addCommandTag("yppowers_owner_"+owner.getUuid());if(elite)clone.addCommandTag("yppowers_time_clone_golden");clone.setPersistent();clone.setCanPickUpLoot(false);
            clone.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE_SWORD));
            if(elite){clone.equipStack(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));clone.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.IRON_CHESTPLATE));
                if(clone.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH)!=null)clone.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.MAX_HEALTH).setBaseValue(40.0);clone.setHealth(40.0f);}
            else clone.setHealth(clone.getMaxHealth());
            world.spawnEntity(clone);list.add(new TimeClone(clone,elite,ticks));index++;
        }
    }

    @Override public void onPlayerDisconnect(ServerPlayerEntity player) {
        UUID id=player.getUuid();List<TimeClone> list=timeClones.remove(id);if(list==null)return;
        List<NbtCompound> saved=new ArrayList<>();
        for(TimeClone c:list)if(c.entity.isAlive()&&!c.entity.isRemoved()&&c.ticks>0){NbtCompound nbt=new NbtCompound();nbt.putString("Variant",c.elite?"ELITE":"ORDINARY");nbt.putInt("Ticks",c.ticks);saved.add(nbt);c.entity.discard();}
        if(player.getEntityWorld().getServer()!=null)PowerManager.get(player.getEntityWorld().getServer()).saveStoredTimeClones(id,saved);
        lastOwnerTargets.remove(id);
    }

    /** Called for each mob death. Ordinary shards are temporary touch pickups; boss shards remain physical items. */
    public static void dropTimeShards(ServerWorld world, Entity dead) {
        if(world==null||dead==null||dead instanceof ServerPlayerEntity)return;
        YPConfig cfg=YPConfig.getInstance();
        if(world.random.nextDouble()<Math.max(0,Math.min(1,cfg.time_warper_time_shard_drop_chance))) {
            ItemStack stack=new ItemStack(Items.ECHO_SHARD);
            stack.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Time Shard").formatted(Formatting.DARK_PURPLE,Formatting.BOLD));
            YPItemData.setFlag(stack,"YPTimeShard",true);
            ItemEntity entity=new ItemEntity(world,dead.getX(),dead.getY()+.2,dead.getZ(),stack);entity.setPickupDelay(32767);entity.setInvulnerable(true);world.spawnEntity(entity);
            TIME_SHARD_LIFETIME.put(entity.getUuid(),Math.max(1,cfg.time_warper_time_shard_lifetime_ticks));
        }
        boolean boss=dead.getType()==EntityType.WITHER||dead.getType()==EntityType.ENDER_DRAGON||dead.getType()==EntityType.WARDEN||dead.getType()==EntityType.ELDER_GUARDIAN;
        if(boss&&world.random.nextDouble()<Math.max(0,Math.min(1,cfg.time_warper_golden_shard_drop_chance))) {
            ItemStack golden=new ItemStack(Items.NETHER_STAR);golden.set(DataComponentTypes.CUSTOM_NAME,Text.literal("Golden Time Shard").formatted(Formatting.GOLD,Formatting.BOLD));YPItemData.setFlag(golden,"YPGoldenTimeShard",true);
            ItemEntity entity=new ItemEntity(world,dead.getX(),dead.getY()+.4,dead.getZ(),golden);entity.setPickupDelay(0);world.spawnEntity(entity);
            world.spawnParticles(ParticleTypes.END_ROD,dead.getX(),dead.getY()+.5,dead.getZ(),30,.4,.4,.4,.04);
        }
    }

    /** Physical contact (not homing) converts a short-lived ordinary shard into its unbounded counter. */
    public static void tryCollectTimeShard(ServerPlayerEntity player) {
        if(player==null||player.getEntityWorld().getServer()==null||!(player.getEntityWorld() instanceof ServerWorld world))return;
        for(ItemEntity shard:world.getEntitiesByClass(ItemEntity.class,player.getBoundingBox().expand(.2),e->YPItemData.hasFlag(e.getStack(),"YPTimeShard"))) {
            if(!shard.getBoundingBox().intersects(player.getBoundingBox()))continue;
            PowerManager.get(world.getServer()).addTimeShards(player.getUuid(),1);shard.discard();
            player.sendMessage(Text.literal("Time Shard collected: "+PowerManager.get(world.getServer()).getTimeShards(player.getUuid())).formatted(Formatting.DARK_PURPLE),true);
            world.playSound(null,player.getBlockPos(),SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.PLAYERS,.55f,1.7f);
        }
    }

    private static void tickTimeShards(MinecraftServer server) {
        Set<UUID> live=new HashSet<>();
        for(ServerWorld world:server.getWorlds())for(Entity entity:world.iterateEntities())if(entity instanceof ItemEntity item&&YPItemData.hasFlag(item.getStack(),"YPTimeShard")) {
            UUID id=item.getUuid();live.add(id);int remaining=TIME_SHARD_LIFETIME.getOrDefault(id,Math.max(1,YPConfig.getInstance().time_warper_time_shard_lifetime_ticks))-1;
            if(remaining<=0){item.discard();TIME_SHARD_LIFETIME.remove(id);continue;}TIME_SHARD_LIFETIME.put(id,remaining);
            if(server.getTicks()%3==0)world.spawnParticles(BLACK_HOLE_DARK_PARTICLE,item.getX(),item.getY()+.15,item.getZ(),3,.12,.12,.12,0);
        }
        TIME_SHARD_LIFETIME.keySet().retainAll(live);
    }

    public static void clearTimeShardRuntimeState() { TIME_SHARD_LIFETIME.clear(); }

}
