package com.yppowers.power.impl;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.PowerManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative implementation of Teleport Gambit's persistent pocket Domain. */
public final class TeleportGambitDomainManager {
    public static final RegistryKey<World> DOMAIN_KEY = RegistryKey.of(
            RegistryKeys.WORLD, Identifier.of("yppowers", "tg_domain"));

    private static final int FLOOR_Y = 64;
    private static final int PLATFORM_RADIUS = 128;
    private static final int PLATFORM_SIZE = PLATFORM_RADIUS * 2 + 1;
    private static final int PLATFORM_BLOCKS_PER_TICK = 1024;
    private static final double MAX_PULL_VELOCITY = 1.35;
    private static final double DOMAIN_BOUNDARY_MARGIN = 3.0;

    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final Map<UUID, DomainSession> SESSIONS = new HashMap<>();
    private static final Map<UUID, UUID> SESSION_OWNER_BY_MEMBER = new HashMap<>();

    private static final List<BoostSpec> BOOSTS = List.of(
            boost(EntityAttributes.MAX_HEALTH, "max_health"),
            boost(EntityAttributes.MOVEMENT_SPEED, "movement_speed"),
            boost(EntityAttributes.ATTACK_DAMAGE, "attack_damage"),
            boost(EntityAttributes.ATTACK_SPEED, "attack_speed"),
            boost(EntityAttributes.ATTACK_KNOCKBACK, "attack_knockback"),
            boost(EntityAttributes.ARMOR, "armor"),
            boost(EntityAttributes.ARMOR_TOUGHNESS, "armor_toughness"),
            boost(EntityAttributes.KNOCKBACK_RESISTANCE, "knockback_resistance"),
            boost(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, "explosion_knockback_resistance"),
            boost(EntityAttributes.MAX_ABSORPTION, "max_absorption"),
            boost(EntityAttributes.BLOCK_INTERACTION_RANGE, "block_interaction_range"),
            boost(EntityAttributes.ENTITY_INTERACTION_RANGE, "entity_interaction_range"),
            boost(EntityAttributes.BLOCK_BREAK_SPEED, "block_break_speed"),
            boost(EntityAttributes.MINING_EFFICIENCY, "mining_efficiency"),
            boost(EntityAttributes.SUBMERGED_MINING_SPEED, "submerged_mining_speed"),
            boost(EntityAttributes.MOVEMENT_EFFICIENCY, "movement_efficiency"),
            boost(EntityAttributes.SNEAKING_SPEED, "sneaking_speed"),
            boost(EntityAttributes.STEP_HEIGHT, "step_height"),
            boost(EntityAttributes.OXYGEN_BONUS, "oxygen_bonus"),
            boost(EntityAttributes.SWEEPING_DAMAGE_RATIO, "sweeping_damage_ratio"),
            boost(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, "water_movement_efficiency"),
            boost(EntityAttributes.SAFE_FALL_DISTANCE, "safe_fall_distance")
    );

    private TeleportGambitDomainManager() {}

    private static BoostSpec boost(RegistryEntry<EntityAttribute> attribute, String suffix) {
        return new BoostSpec(attribute, Identifier.of("yppowers", "tg_domain_5x_" + suffix));
    }

    public static void beginCharge(ServerPlayerEntity caster) {
        MinecraftServer server = caster.getEntityWorld().getServer();
        if (server == null) return;
        if (GraceManager.blockAbility(caster)) return;
        UUID ownerId = caster.getUuid();

        if (caster.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
            caster.sendMessage(Text.literal("You cannot open another Domain from inside a Domain."), true);
            return;
        }
        if (CHARGES.containsKey(ownerId) || SESSIONS.containsKey(ownerId)) {
            caster.sendMessage(Text.literal("Your Domain is already charging or active."), true);
            return;
        }
        UUID currentOwner = SESSION_OWNER_BY_MEMBER.get(ownerId);
        if (currentOwner != null && SESSIONS.containsKey(currentOwner)) {
            caster.sendMessage(Text.literal("You are trapped inside another Teleport Gambit's Domain."), true);
            return;
        }

        PowerManager manager = PowerManager.get(server);
        int cooldown = manager.getTeleportGambitDomainCooldownTicks(ownerId);
        if (cooldown > 0) {
            caster.sendMessage(Text.literal("Domain cooldown: " + ((cooldown + 1199) / 1200) + " minute(s) remaining."), true);
            return;
        }

        ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);
        if (domainWorld == null) {
            caster.sendMessage(Text.literal("Teleport Gambit's Domain dimension is not loaded. Restart the world/server with this mod version and try again."), false);
            return;
        }

        ServerWorld sourceWorld = (ServerWorld) caster.getEntityWorld();
        BlockPos framePos = caster.getBlockPos().up(3).toImmutable();
        BlockState oldState = sourceWorld.getBlockState(framePos);
        if (oldState.hasBlockEntity() || (!oldState.isAir() && !oldState.isReplaceable())
                || oldState.getHardness(sourceWorld, framePos) < 0) {
            caster.sendMessage(Text.literal("There is no safe space for the Domain's End Portal Frame above you."), true);
            return;
        }

        int duration = Math.max(40, YPConfig.getInstance().teleport_gambit_domain_charge_ticks);
        double radius = Math.max(1.0, YPConfig.getInstance().teleport_gambit_domain_pull_radius);
        BlockPos center = domainCenter(ownerId);
        PlatformBuilder builder = domainWorld.getBlockState(center.down()).isOf(Blocks.BEDROCK)
                ? null : new PlatformBuilder(center);
        Charge charge = new Charge(ownerId, sourceWorld.getRegistryKey(), framePos, oldState, duration, radius, center, builder);
        CHARGES.put(ownerId, charge);

        sourceWorld.setBlockState(framePos, Blocks.END_PORTAL_FRAME.getDefaultState(), 3);
        sourceWorld.spawnParticles(ParticleTypes.PORTAL, framePos.getX() + 0.5, framePos.getY() + 0.8,
                framePos.getZ() + 0.5, 45, 0.55, 0.7, 0.55, 0.12);
        sourceWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, framePos.getX() + 0.5, framePos.getY() + 0.5,
                framePos.getZ() + 0.5, 30, 0.35, 0.4, 0.35, 0.06);
        sourceWorld.playSound(null, framePos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.5f, 0.65f);
        manager.setTeleportGambitDomainCooldownTicks(ownerId,
                Math.max(0, YPConfig.getInstance().teleport_gambit_domain_cooldown_ticks));
        charge.affectedPlayers.add(ownerId);
        caster.sendMessage(Text.literal("The Domain is forming. Stay nearby as the gate charges!"), true);
    }

    /** Called every server tick, independently of which power each affected player owns. */
    public static void serverTick(MinecraftServer server) {
        tickCharges(server);
        tickSessions(server);
    }

    private static void tickCharges(MinecraftServer server) {
        if (CHARGES.isEmpty()) return;
        for (Charge charge : new ArrayList<>(CHARGES.values())) {
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(charge.ownerId);
            ServerWorld sourceWorld = server.getWorld(charge.sourceDimension);
            if (caster == null || !caster.isAlive() || sourceWorld == null
                    || !caster.getEntityWorld().getRegistryKey().equals(charge.sourceDimension)) {
                cancelCharge(charge.ownerId, server, true);
                continue;
            }

            ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);
            if (domainWorld == null) {
                caster.sendMessage(Text.literal("The Domain dimension became unavailable; the charge was cancelled."), false);
                cancelCharge(charge.ownerId, server, true);
                continue;
            }

            if (!sourceWorld.getBlockState(charge.framePos).isOf(Blocks.END_PORTAL_FRAME)) {
                sourceWorld.setBlockState(charge.framePos, Blocks.END_PORTAL_FRAME.getDefaultState(), 3);
            }
            charge.elapsedTicks++;
            double progress = Math.min(1.0, charge.elapsedTicks / (double) charge.durationTicks);
            double cx = charge.framePos.getX() + 0.5;
            double cy = charge.framePos.getY() + 0.5;
            double cz = charge.framePos.getZ() + 0.5;
            emitChargeVfx(sourceWorld, charge.framePos, progress, charge.elapsedTicks);
            pullNearbyPlayers(server, sourceWorld, charge, cx, cy, cz, progress);

            if (charge.platformBuilder != null && !charge.platformBuilder.isComplete()) {
                charge.platformBuilder.advance(domainWorld, PLATFORM_BLOCKS_PER_TICK);
            }
            if (charge.elapsedTicks >= charge.durationTicks) {
                if (charge.platformBuilder != null && !charge.platformBuilder.isComplete()) {
                    // If an operator configured an unusually short charge, continue the final
                    // phase until the safe platform is finished instead of dropping players into void.
                    charge.elapsedTicks = charge.durationTicks - 1;
                    continue;
                }
                completeCharge(server, charge, sourceWorld, domainWorld);
            }
        }
    }

    private static void emitChargeVfx(ServerWorld world, BlockPos pos, double progress, int ticks) {
        double spread = 0.45 + 2.1 * progress;
        int portalCount = 3 + (int) (progress * 15.0);
        int sparkCount = 1 + (int) (progress * 7.0);
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, portalCount, spread * 0.42, spread * 0.55, spread * 0.42, 0.08 + progress * 0.12);
        world.spawnParticles(ParticleTypes.PORTAL, x, y, z, portalCount, spread, spread * 0.85, spread, 0.06 + progress * 0.18);
        world.spawnParticles(ParticleTypes.END_ROD, x, y, z, sparkCount, spread * 0.6, spread, spread * 0.6, 0.02 + progress * 0.12);
        if (ticks % 3 == 0) world.spawnParticles(ParticleTypes.ENCHANT, x, y, z, 4 + (int) (progress * 12), spread, spread, spread, 0.4 + progress * 0.8);
        if (ticks % 4 == 0) world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, sparkCount, spread * 0.8, spread, spread * 0.8, 0.08 + progress * 0.15);
        if (ticks % 5 == 0) world.spawnParticles(net.minecraft.particle.DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), x, y, z, 2 + (int) (progress * 9), spread * 0.5, spread * 0.7, spread * 0.5, 0.02 + progress * 0.08);
        if (ticks % 20 == 0) {
            world.playSound(null, pos, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.PLAYERS,
                    (float) (0.25 + progress * 0.7), (float) (0.45 + progress * 0.65));
        }
    }

    private static void pullNearbyPlayers(MinecraftServer server, ServerWorld sourceWorld, Charge charge,
                                          double cx, double cy, double cz, double progress) {
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (target.getEntityWorld() != sourceWorld) continue;
            double dx = cx - target.getX();
            double dy = cy - (target.getY() + target.getHeight() * 0.45);
            double dz = cz - target.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > charge.radius) continue;
            charge.affectedPlayers.add(target.getUuid());
            if (distance < 0.75) continue;
            double intensity = 0.012 + 0.070 * progress;
            double inverseDistance = 1.0 / distance;
            Vec3d velocity = target.getVelocity().add(dx * inverseDistance * intensity,
                    dy * inverseDistance * intensity, dz * inverseDistance * intensity);
            double speed = velocity.length();
            if (speed > MAX_PULL_VELOCITY) velocity = velocity.multiply(MAX_PULL_VELOCITY / speed);
            target.setVelocity(velocity);
            ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
            if (charge.elapsedTicks % 5 == 0) {
                sourceWorld.spawnParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + target.getHeight() * 0.5,
                        target.getZ(), 2 + (int) (progress * 5), 0.25, 0.35, 0.25, 0.04 + progress * 0.05);
            }
        }
    }

    private static void completeCharge(MinecraftServer server, Charge charge, ServerWorld sourceWorld, ServerWorld domainWorld) {
        CHARGES.remove(charge.ownerId);
        restoreFrame(server, charge);

        List<ServerPlayerEntity> arrivals = new ArrayList<>();
        ServerPlayerEntity caster = server.getPlayerManager().getPlayer(charge.ownerId);
        if (caster != null && caster.isAlive() && caster.getEntityWorld() == sourceWorld) arrivals.add(caster);
        for (UUID id : charge.affectedPlayers) {
            if (id.equals(charge.ownerId)) continue;
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(id);
            if (target == null || !target.isAlive() || target.getEntityWorld() != sourceWorld) continue;
            double distanceSq = target.getEntityPos().squaredDistanceTo(charge.framePos.toCenterPos());
            if (distanceSq <= charge.radius * charge.radius) arrivals.add(target);
        }

        if (arrivals.isEmpty()) return;
        DomainSession session = new DomainSession(charge.ownerId, charge.domainCenter);
        for (ServerPlayerEntity player : arrivals) session.origins.put(player.getUuid(), PlayerOrigin.capture(player));
        SESSIONS.put(charge.ownerId, session);
        for (ServerPlayerEntity player : arrivals) SESSION_OWNER_BY_MEMBER.put(player.getUuid(), charge.ownerId);

        double preferredRadius = Math.min(24.0, 5.0 + Math.sqrt(Math.max(0, arrivals.size() - 1)) * 2.0);
        for (int i = 0; i < arrivals.size(); i++) {
            ServerPlayerEntity player = arrivals.get(i);
            double angle = i == 0 ? 0 : i * 2.399963229728653;
            double ringRadius = i == 0 ? 0 : preferredRadius;
            double preferredX = charge.domainCenter.getX() + 0.5 + Math.cos(angle) * ringRadius;
            double preferredZ = charge.domainCenter.getZ() + 0.5 + Math.sin(angle) * ringRadius;
            BlockPos safe = findSafeSpawn(domainWorld, charge.domainCenter, preferredX, preferredZ);
            player.teleport(domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    Set.of(), player.getYaw(), player.getPitch(), false);
            player.setVelocity(Vec3d.ZERO);
            ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
            domainWorld.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 32, 0.7, 1.0, 0.7, 0.16);
            domainWorld.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 14, 0.8, 1.0, 0.8, 0.05);
            domainWorld.playSound(null, safe, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.2f, 0.65f);
            player.sendMessage(Text.literal("Teleport Gambit has dragged you into a pocket Domain. You are trapped until its owner dies or leaves."), false);
        }

        if (caster != null && arrivals.stream().anyMatch(p -> p.getUuid().equals(charge.ownerId))) {
            ensureDomainBoost(caster);
            caster.setHealth(caster.getMaxHealth());
            caster.sendMessage(Text.literal("Your Domain is open. Your core stats are multiplied by 5; jump strength is unchanged."), false);
        }
        domainWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, charge.domainCenter.getX() + 0.5,
                FLOOR_Y + 1.0, charge.domainCenter.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    private static BlockPos findSafeSpawn(ServerWorld world, BlockPos center, double preferredX, double preferredZ) {
        int baseX = (int) Math.floor(preferredX);
        int baseZ = (int) Math.floor(preferredZ);
        BlockPos exact = safeAt(world, baseX, FLOOR_Y + 1, baseZ);
        if (exact != null) return exact;
        for (int radius = 1; radius <= 48; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                BlockPos north = safeAt(world, center.getX() + dx, FLOOR_Y + 1, center.getZ() - radius);
                if (north != null) return north;
                BlockPos south = safeAt(world, center.getX() + dx, FLOOR_Y + 1, center.getZ() + radius);
                if (south != null) return south;
            }
            for (int dz = -radius + 1; dz < radius; dz++) {
                BlockPos west = safeAt(world, center.getX() - radius, FLOOR_Y + 1, center.getZ() + dz);
                if (west != null) return west;
                BlockPos east = safeAt(world, center.getX() + radius, FLOOR_Y + 1, center.getZ() + dz);
                if (east != null) return east;
            }
        }
        // The Domain slab is bedrock, and all regular entry slots should be clear. This last
        // fallback remains on the slab if an owner has filled every nearby landing space.
        return center.up();
    }

    private static BlockPos safeAt(ServerWorld world, int x, int y, int z) {
        BlockPos feet = new BlockPos(x, y, z);
        if (!world.getBlockState(feet.down()).isSolidBlock(world, feet.down())) return null;
        if (!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir()) return null;
        if (!world.getFluidState(feet).isEmpty() || !world.getFluidState(feet.up()).isEmpty()) return null;
        return feet;
    }

    private static void tickSessions(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);
        for (DomainSession session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(session.ownerId);
            if (owner == null) {
                endSession(server, session.ownerId, EndReason.OWNER_DISCONNECTED);
                continue;
            }
            if (!owner.isAlive()) {
                endSession(server, session.ownerId, EndReason.OWNER_DIED);
                continue;
            }
            if (domainWorld == null || owner.getEntityWorld() != domainWorld) {
                endSession(server, session.ownerId, EndReason.OWNER_LEFT_DIMENSION);
                continue;
            }
            if (!insideDomainRegion(owner, session.center)) {
                endSession(server, session.ownerId, EndReason.OWNER_LEFT_REGION);
                continue;
            }

            ensureDomainBoost(owner);
            // The custom dimension type uses fixed_time=6000. Reapplying the time here also
            // protects servers which hot-reloaded the data pack while the world was running.
            if (server.getTicks() % 200 == 0) domainWorld.setTimeOfDay(6000L);

            for (UUID memberId : new ArrayList<>(session.origins.keySet())) {
                if (memberId.equals(session.ownerId)) continue;
                ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
                if (member == null || !member.isAlive()) continue;
                if (member.getEntityWorld() != domainWorld || !insideDomainRegion(member, session.center)) {
                    BlockPos safe = findSafeSpawn(domainWorld, session.center, session.center.getX() + 0.5, session.center.getZ() + 0.5);
                    member.teleport(domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                            Set.of(), member.getYaw(), member.getPitch(), false);
                    member.setVelocity(Vec3d.ZERO);
                    ((EntityVelocityAccessor) member).invokeYppowersScheduleVelocityUpdate();
                    member.sendMessage(Text.literal("The Domain is sealed. You cannot leave while its owner remains inside."), true);
                }
            }
        }
    }

    private static boolean insideDomainRegion(ServerPlayerEntity player, BlockPos center) {
        if (!player.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) return false;
        double dx = Math.abs(player.getX() - (center.getX() + 0.5));
        double dz = Math.abs(player.getZ() - (center.getZ() + 0.5));
        return dx <= PLATFORM_RADIUS - DOMAIN_BOUNDARY_MARGIN
                && dz <= PLATFORM_RADIUS - DOMAIN_BOUNDARY_MARGIN
                && player.getY() >= FLOOR_Y + 1.0
                && player.getY() < 250.0;
    }

    public static boolean isProtectedChargeFrame(ServerWorld world, BlockPos pos) {
        for (Charge charge : CHARGES.values()) {
            if (charge.framePos.equals(pos) && charge.sourceDimension.equals(world.getRegistryKey())) return true;
        }
        return false;
    }

    public static void onPlayerDeath(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        cancelCharge(player.getUuid(), server, true);
        UUID ownerId = SESSION_OWNER_BY_MEMBER.get(player.getUuid());
        if (ownerId != null && ownerId.equals(player.getUuid())) {
            endSession(server, ownerId, EndReason.OWNER_DIED);
        }
    }

    public static void onPlayerDisconnect(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        cancelCharge(player.getUuid(), server, true);
        UUID ownerId = SESSION_OWNER_BY_MEMBER.get(player.getUuid());
        if (ownerId != null && ownerId.equals(player.getUuid())) {
            endSession(server, ownerId, EndReason.OWNER_DISCONNECTED);
        }
        removeDomainBoost(player);
    }

    public static void onPowerRemoved(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        cancelCharge(player.getUuid(), server, true);
        UUID ownerId = SESSION_OWNER_BY_MEMBER.get(player.getUuid());
        if (ownerId != null && ownerId.equals(player.getUuid())) {
            endSession(server, ownerId, EndReason.OWNER_POWER_REMOVED);
        }
    }

    public static void onPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PowerManager manager = PowerManager.get(server);
        UUID id = player.getUuid();

        NbtCompound pending = manager.getTeleportGambitPendingReturn(id);
        if (pending != null && restorePendingReturn(player, server, pending)) {
            manager.clearTeleportGambitPendingReturn(id);
            return;
        }

        UUID ownerId = SESSION_OWNER_BY_MEMBER.get(id);
        DomainSession active = ownerId == null ? null : SESSIONS.get(ownerId);
        if (active != null && ownerId != null) {
            ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);
            if (domainWorld != null) {
                BlockPos safe = findSafeSpawn(domainWorld, active.center, active.center.getX() + 0.5, active.center.getZ() + 0.5);
                player.teleport(domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                        Set.of(), player.getYaw(), player.getPitch(), false);
                if (id.equals(active.ownerId)) ensureDomainBoost(player);
                return;
            }
        }

        if (player.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
            // If the server crashed before the normal shutdown cleanup could run, never leave a
            // player stranded in an orphaned pocket dimension without a live owner/session.
            BlockPos overworldSpawn = server.getOverworld().getSpawnPoint().getPos();
            player.teleport(server.getOverworld(), overworldSpawn.getX() + 0.5,
                    overworldSpawn.getY() + 1.0, overworldSpawn.getZ() + 0.5,
                    Set.of(), player.getYaw(), player.getPitch(), false);
        }
        removeDomainBoost(player);
    }

    public static void onPlayerRespawn(ServerPlayerEntity player) {
        onPlayerJoin(player);
    }

    public static void shutdown(MinecraftServer server) {
        for (UUID ownerId : new ArrayList<>(CHARGES.keySet())) cancelCharge(ownerId, server, true);
        for (UUID ownerId : new ArrayList<>(SESSIONS.keySet())) endSession(server, ownerId, EndReason.SERVER_SHUTDOWN);
    }

    private static void endSession(MinecraftServer server, UUID ownerId, EndReason reason) {
        DomainSession session = SESSIONS.remove(ownerId);
        if (session == null) return;
        PowerManager manager = PowerManager.get(server);
        for (Map.Entry<UUID, PlayerOrigin> entry : session.origins.entrySet()) {
            UUID memberId = entry.getKey();
            PlayerOrigin origin = entry.getValue();
            SESSION_OWNER_BY_MEMBER.remove(memberId);
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (memberId.equals(ownerId)) {
                if (reason == EndReason.OWNER_LEFT_REGION) {
                    manager.clearTeleportGambitPendingReturn(memberId);
                    continue;
                }
                if (reason == EndReason.OWNER_DIED || reason == EndReason.OWNER_DISCONNECTED) {
                    manager.saveTeleportGambitPendingReturn(memberId, origin.toNbt());
                    if (member != null) removeDomainBoost(member);
                    continue;
                }
                if (reason == EndReason.OWNER_LEFT_DIMENSION && member != null
                        && !member.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
                    removeDomainBoost(member);
                    manager.clearTeleportGambitPendingReturn(memberId);
                    continue;
                }
            }
            if (member != null && member.isAlive()) {
                origin.teleport(member, server);
                manager.clearTeleportGambitPendingReturn(memberId);
                member.sendMessage(Text.literal("Teleport Gambit's Domain has closed. You have been returned to your previous location."), false);
            } else {
                manager.saveTeleportGambitPendingReturn(memberId, origin.toNbt());
                if (member != null) removeDomainBoost(member);
            }
        }
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
        if (owner != null) {
            removeDomainBoost(owner);
            owner.setHealth(Math.min(owner.getHealth(), owner.getMaxHealth()));
            if (reason == EndReason.OWNER_LEFT_REGION) {
                PlayerOrigin origin = session.origins.get(ownerId);
                if (origin != null) origin.teleport(owner, server);
            }
        }
        session.origins.clear();
    }

    private static void cancelCharge(UUID ownerId, MinecraftServer server, boolean notify) {
        Charge charge = CHARGES.remove(ownerId);
        if (charge == null) return;
        restoreFrame(server, charge);
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
        if (notify && owner != null && owner.isAlive()) {
            owner.sendMessage(Text.literal("The Domain charge was interrupted."), true);
        }
    }

    private static void restoreFrame(MinecraftServer server, Charge charge) {
        ServerWorld source = server.getWorld(charge.sourceDimension);
        if (source != null && source.getBlockState(charge.framePos).isOf(Blocks.END_PORTAL_FRAME)) {
            source.setBlockState(charge.framePos, charge.originalState, 3);
        }
    }

    private static void ensureDomainBoost(ServerPlayerEntity player) {
        for (BoostSpec spec : BOOSTS) {
            EntityAttributeInstance attribute = player.getAttributeInstance(spec.attribute());
            if (attribute != null && attribute.getModifier(spec.id()) == null) {
                attribute.addTemporaryModifier(new EntityAttributeModifier(
                        spec.id(), 4.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static void removeDomainBoost(ServerPlayerEntity player) {
        for (BoostSpec spec : BOOSTS) {
            EntityAttributeInstance attribute = player.getAttributeInstance(spec.attribute());
            if (attribute != null) attribute.removeModifier(spec.id());
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private static BlockPos domainCenter(UUID ownerId) {
        long hash = ownerId.getMostSignificantBits() ^ Long.rotateLeft(ownerId.getLeastSignificantBits(), 23);
        hash ^= hash >>> 33;
        hash *= 0xff51afd7ed558ccdl;
        hash ^= hash >>> 33;
        hash *= 0xc4ceb9fe1a85ec53l;
        hash ^= hash >>> 33;
        int xSlot = (int) (hash & 0x3fffL);
        int zSlot = (int) ((hash >>> 14) & 0x3fffL);
        // Each owner gets a deterministic 1024-block-separated area, so their saved builds do not overlap.
        int x = 512 + xSlot * 1024;
        int z = 512 + zSlot * 1024;
        return new BlockPos(x, FLOOR_Y + 1, z);
    }

    private record BoostSpec(RegistryEntry<EntityAttribute> attribute, Identifier id) {}

    private enum EndReason {
        OWNER_DIED,
        OWNER_DISCONNECTED,
        OWNER_LEFT_DIMENSION,
        OWNER_LEFT_REGION,
        OWNER_POWER_REMOVED,
        SERVER_SHUTDOWN
    }

    private static final class Charge {
        private final UUID ownerId;
        private final RegistryKey<World> sourceDimension;
        private final BlockPos framePos;
        private final BlockState originalState;
        private final int durationTicks;
        private final double radius;
        private final BlockPos domainCenter;
        private final Set<UUID> affectedPlayers = new HashSet<>();
        private final PlatformBuilder platformBuilder;
        private int elapsedTicks;

        private Charge(UUID ownerId, RegistryKey<World> sourceDimension, BlockPos framePos,
                       BlockState originalState, int durationTicks, double radius,
                       BlockPos domainCenter, PlatformBuilder platformBuilder) {
            this.ownerId = ownerId;
            this.sourceDimension = sourceDimension;
            this.framePos = framePos;
            this.originalState = originalState;
            this.durationTicks = durationTicks;
            this.radius = radius;
            this.domainCenter = domainCenter;
            this.platformBuilder = platformBuilder;
        }
    }

    private static final class PlatformBuilder {
        private final BlockPos center;
        private final int total = PLATFORM_SIZE * PLATFORM_SIZE;
        private int cursor;
        private boolean complete;

        private PlatformBuilder(BlockPos center) { this.center = center; }

        private boolean isComplete() { return complete; }

        private void advance(ServerWorld world, int allowance) {
            int processed = 0;
            while (cursor < total && processed < allowance) {
                int index = cursor++;
                int dx = index % PLATFORM_SIZE - PLATFORM_RADIUS;
                int dz = index / PLATFORM_SIZE - PLATFORM_RADIUS;
                // Place the center last; it acts as the completion marker if a server stops mid-build.
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = new BlockPos(center.getX() + dx, FLOOR_Y, center.getZ() + dz);
                if (!world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
                    world.setBlockState(pos, Blocks.BEDROCK.getDefaultState(), 2);
                }
                processed++;
            }
            if (cursor >= total) {
                BlockPos marker = new BlockPos(center.getX(), FLOOR_Y, center.getZ());
                if (!world.getBlockState(marker).isOf(Blocks.BEDROCK)) {
                    world.setBlockState(marker, Blocks.BEDROCK.getDefaultState(), 3);
                }
                complete = true;
            }
        }
    }

    private static final class DomainSession {
        private final UUID ownerId;
        private final BlockPos center;
        private final Map<UUID, PlayerOrigin> origins = new HashMap<>();
        private DomainSession(UUID ownerId, BlockPos center) {
            this.ownerId = ownerId;
            this.center = center;
        }
    }

    private record PlayerOrigin(String dimension, double x, double y, double z, float yaw, float pitch) {
        private static PlayerOrigin capture(ServerPlayerEntity player) {
            return new PlayerOrigin(player.getEntityWorld().getRegistryKey().getValue().toString(),
                    player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        }

        private NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("Dimension", dimension);
            nbt.putDouble("X", x);
            nbt.putDouble("Y", y);
            nbt.putDouble("Z", z);
            nbt.putFloat("Yaw", yaw);
            nbt.putFloat("Pitch", pitch);
            return nbt;
        }

        private static PlayerOrigin fromNbt(NbtCompound nbt) {
            String dimension = nbt.getString("Dimension", "");
            if (Identifier.tryParse(dimension) == null) return null;
            return new PlayerOrigin(dimension,
                    nbt.getDouble("X", 0.0), nbt.getDouble("Y", 80.0), nbt.getDouble("Z", 0.0),
                    nbt.getFloat("Yaw", 0.0f), nbt.getFloat("Pitch", 0.0f));
        }

        private void teleport(ServerPlayerEntity player, MinecraftServer server) {
            Identifier identifier = Identifier.tryParse(dimension);
            ServerWorld destination = null;
            if (identifier != null) {
                RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, identifier);
                destination = server.getWorld(key);
            }
            if (destination == null) destination = server.getOverworld();
            player.teleport(destination, x, y, z, Set.of(), yaw, pitch, false);
            player.setVelocity(Vec3d.ZERO);
            ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        }
    }

    private static boolean restorePendingReturn(ServerPlayerEntity player, MinecraftServer server, NbtCompound nbt) {
        PlayerOrigin origin = PlayerOrigin.fromNbt(nbt);
        if (origin == null) return false;
        origin.teleport(player, server);
        player.sendMessage(Text.literal("You have been returned from Teleport Gambit's Domain."), false);
        return true;
    }
}
