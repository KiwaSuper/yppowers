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
import net.minecraft.particle.TintedParticleEffect;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers / Teleport Gambit Domain");

    private static final int FLOOR_Y = 64;
    private static final int PLATFORM_RADIUS = 128;
    private static final int PLATFORM_SIZE = PLATFORM_RADIUS * 2 + 1;
    private static final int PLATFORM_BLOCKS_PER_TICK = 1024;
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

    private static final int BEACON_BEAM_TICKS = 8; // 0.4 seconds
    private static final int PULL_ANIMATION_TICKS = 4; // 0.2 seconds: fast, decisive pull
    private static final TintedParticleEffect DOMAIN_DARK_PARTICLE =
            TintedParticleEffect.create(ParticleTypes.ENTITY_EFFECT, 0x07070B);

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

            // The beacon block is deliberately temporary. Do not overwrite it during its short beam phase.
            if (charge.phase != ChargePhase.BEACON_BEAM && !charge.gateRemoved
                    && !sourceWorld.getBlockState(charge.framePos).isOf(Blocks.END_PORTAL_FRAME)) {
                sourceWorld.setBlockState(charge.framePos, Blocks.END_PORTAL_FRAME.getDefaultState(), 3);
            }

            if (charge.phase == ChargePhase.CHARGING) {
                charge.elapsedTicks++;
                double progress = Math.min(1.0, charge.elapsedTicks / (double) charge.durationTicks);
                emitChargeVfx(sourceWorld, charge.framePos, progress, charge.elapsedTicks);

                if (charge.platformBuilder != null && !charge.platformBuilder.isComplete()) {
                    charge.platformBuilder.advance(domainWorld, PLATFORM_BLOCKS_PER_TICK);
                }
                if (charge.elapsedTicks >= charge.durationTicks) {
                    if (charge.platformBuilder != null && !charge.platformBuilder.isComplete()) {
                        // Never open the Domain before its bedrock landing platform is complete.
                        charge.elapsedTicks = charge.durationTicks - 1;
                        continue;
                    }
                    beginBeaconBeam(sourceWorld, charge);
                }
                continue;
            }

            if (charge.phase == ChargePhase.BEACON_BEAM) {
                charge.phaseTicks++;
                // The beam is decorative; emit alternate animation frames to reduce particle
                // packet volume while the phase timer and gate transfer remain unchanged.
                if ((charge.phaseTicks & 1) == 0) emitBeaconBeam(sourceWorld, charge.framePos, charge.phaseTicks);
                if (charge.phaseTicks >= BEACON_BEAM_TICKS) {
                    sourceWorld.setBlockState(charge.framePos, Blocks.END_PORTAL_FRAME.getDefaultState(), 3);
                    beginPullAnimation(server, sourceWorld, charge);
                }
                continue;
            }

            if (charge.phase == ChargePhase.PULLING) {
                charge.phaseTicks++;
                animatePull(server, sourceWorld, charge);
                // The pull is deliberately short. On its final tick, transfer directly to the Domain.
                // Do not add a settle tick, a TeleportTarget fallback, or a second transfer path.
                if (charge.phaseTicks >= PULL_ANIMATION_TICKS) {
                    completeCharge(server, charge, sourceWorld, domainWorld);
                }
            }
        }
    }

    private static void beginBeaconBeam(ServerWorld sourceWorld, Charge charge) {
        charge.phase = ChargePhase.BEACON_BEAM;
        charge.phaseTicks = 0;
        // A real Beacon block gives the gate its familiar beam. The brief particle column below
        // keeps the visual visible even before the Beacon block entity has refreshed its beam.
        sourceWorld.setBlockState(charge.framePos, Blocks.BEACON.getDefaultState(), 3);
        sourceWorld.playSound(null, charge.framePos, SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.PLAYERS, 1.6f, 0.72f);
        sourceWorld.playSound(null, charge.framePos, SoundEvents.BLOCK_END_PORTAL_SPAWN,
                SoundCategory.PLAYERS, 1.4f, 0.6f);

        // A one-shot, high-contrast burst marks the moment the gate reaches full charge.
        double x = charge.framePos.getX() + 0.5;
        double y = charge.framePos.getY() + 0.5;
        double z = charge.framePos.getZ() + 0.5;
        sourceWorld.spawnParticles(ParticleTypes.END_ROD, x, y, z,
                64, 1.15, 0.75, 1.15, 0.16);
        sourceWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                52, 1.0, 0.6, 1.0, 0.12);
        sourceWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z,
                48, 1.1, 0.7, 1.1, 0.08);
        sourceWorld.spawnParticles(net.minecraft.particle.DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F),
                x, y, z, 28, 0.8, 0.5, 0.8, 0.04);
    }

    private static void emitBeaconBeam(ServerWorld world, BlockPos pos, int phaseTick) {
        double x = pos.getX() + 0.5;
        double z = pos.getZ() + 0.5;
        // Short-lived, narrow column of bright particles makes the pre-pull beam obvious.
        for (double y = pos.getY() + 0.5; y < pos.getY() + 18.0; y += 0.5) {
            world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
        }
        // Rotating horizontal halo makes the single-block gate's fully charged state easy to spot.
        double rotation = phaseTick * 0.42;
        for (int i = 0; i < 16; i++) {
            double angle = rotation + Math.PI * 2.0 * i / 16.0;
            double ringX = x + Math.cos(angle) * 1.35;
            double ringZ = z + Math.sin(angle) * 1.35;
            double ringY = pos.getY() + 0.45 + Math.sin(angle * 2.0) * 0.18;
            world.spawnParticles(ParticleTypes.END_ROD, ringX, ringY, ringZ, 1, 0, 0, 0, 0);
            if ((i & 1) == 0) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, ringX, ringY, ringZ, 1, 0, 0, 0, 0);
            }
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, pos.getY() + 0.45, z,
                24, 0.2, 0.24, 0.2, 0.03);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, pos.getY() + 0.5, z,
                22, 0.6, 0.5, 0.6, 0.06);
    }

    private static void emitPullGateCollapse(ServerWorld world, BlockPos pos, double progress) {
        // The gate's block is removed during this phase. Shrinking particle spreads create a
        // compacting black sphere without changing the pull timing or the eventual transfer.
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        double radius = 0.18 + (1.0 - progress) * 2.0;
        int darkCount = 58 + (int) ((1.0 - progress) * 26.0);
        world.spawnParticles(DOMAIN_DARK_PARTICLE, x, y, z, darkCount,
                radius * 0.34, radius * 0.34, radius * 0.34, 0.008);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 42,
                radius * 0.42, radius * 0.42, radius * 0.42, 0.006);
        world.spawnParticles(ParticleTypes.SMOKE, x, y, z, 24,
                radius * 0.38, radius * 0.38, radius * 0.38, 0.004);

        // A short corkscrew contracts with the cloud so the motion reads as an implosion.
        double rotation = progress * Math.PI * 1.8;
        for (int i = 0; i < 10; i++) {
            double fraction = (i + 1.0) / 10.0;
            double angle = rotation + fraction * Math.PI * 4.0;
            double orbit = radius * (0.25 + 0.65 * fraction);
            double py = y + Math.sin(angle * 1.6) * radius * 0.32;
            world.spawnParticles(DOMAIN_DARK_PARTICLE,
                    x + Math.cos(angle) * orbit, py, z + Math.sin(angle) * orbit,
                    1, 0.015, 0.015, 0.015, 0.002);
        }
    }

    private static void beginPullAnimation(MinecraftServer server, ServerWorld sourceWorld, Charge charge) {
        charge.phase = ChargePhase.PULLING;
        charge.phaseTicks = 0;
        charge.affectedPlayers.clear();
        charge.pullStartPositions.clear();
        Vec3d gate = charge.framePos.toCenterPos();
        double radiusSq = charge.radius * charge.radius;
        for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
            if (!target.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey()) || !target.isAlive()) continue;
            if (target.getEntityPos().squaredDistanceTo(gate) > radiusSq && !target.getUuid().equals(charge.ownerId)) continue;
            charge.affectedPlayers.add(target.getUuid());
            charge.pullStartPositions.put(target.getUuid(), target.getEntityPos());
            target.setVelocity(Vec3d.ZERO);
            ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
        }
        sourceWorld.playSound(null, charge.framePos, SoundEvents.BLOCK_END_PORTAL_SPAWN,
                SoundCategory.PLAYERS, 1.7f, 0.55f);
    }

    private static void animatePull(MinecraftServer server, ServerWorld sourceWorld, Charge charge) {
        if (!charge.gateRemoved) {
            BlockState current = sourceWorld.getBlockState(charge.framePos);
            if (current.isOf(Blocks.END_PORTAL_FRAME) || current.isOf(Blocks.BEACON)) {
                sourceWorld.setBlockState(charge.framePos, Blocks.AIR.getDefaultState(), 3);
            }
            charge.gateRemoved = true;
        }
        double linear = Math.min(1.0, charge.phaseTicks / (double) PULL_ANIMATION_TICKS);
        // Smoothstep easing: slowly begins, accelerates toward the gate, and settles at the end.
        double eased = linear * linear * (3.0 - 2.0 * linear);
        emitPullGateCollapse(sourceWorld, charge.framePos, eased);
        double goalX = charge.framePos.getX() + 0.5;
        double goalY = charge.framePos.getY() - 2.0; // just below the frame, with headroom
        double goalZ = charge.framePos.getZ() + 0.5;
        for (UUID id : charge.affectedPlayers) {
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(id);
            Vec3d startPos = charge.pullStartPositions.get(id);
            if (target == null || startPos == null || !target.isAlive()
                    || !target.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) continue;
            double x = startPos.x + (goalX - startPos.x) * eased;
            double y = startPos.y + (goalY - startPos.y) * eased;
            double z = startPos.z + (goalZ - startPos.z) * eased;
            target.setVelocity(Vec3d.ZERO);
            target.requestTeleport(x, y, z);
            if (charge.phaseTicks % 3 == 0) {
                sourceWorld.spawnParticles(ParticleTypes.PORTAL, x, y + 0.8, z,
                        5, 0.2, 0.35, 0.2, 0.03);
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

    private static void completeCharge(MinecraftServer server, Charge charge, ServerWorld sourceWorld, ServerWorld domainWorld) {
        CHARGES.remove(charge.ownerId);
        restoreFrame(server, charge);

        ServerPlayerEntity caster = server.getPlayerManager().getPlayer(charge.ownerId);
        if (caster == null || !caster.isAlive()
                || !caster.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) {
            stopPullingAffectedPlayers(server, charge, sourceWorld);
            if (caster != null) caster.sendMessage(Text.literal("The Domain could not open because its owner was no longer at the gate."), true);
            return;
        }

        List<ServerPlayerEntity> arrivals = new ArrayList<>();
        arrivals.add(caster); // The owner must transfer first; visitors are only admitted if this succeeds.
        for (UUID id : charge.affectedPlayers) {
            if (id.equals(charge.ownerId)) continue;
            ServerPlayerEntity target = server.getPlayerManager().getPlayer(id);
            if (target == null || !target.isAlive()
                    || !target.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) continue;
            // Membership was captured when the pull animation began; do not re-filter by radius
            // after moving the player to the gate, or small configured radii could cancel arrival.
            arrivals.add(target);
        }

        double preferredRadius = Math.min(24.0, 5.0 + Math.sqrt(Math.max(0, arrivals.size() - 1)) * 2.0);
        Map<UUID, PlayerOrigin> origins = new HashMap<>();
        for (ServerPlayerEntity player : arrivals) origins.put(player.getUuid(), PlayerOrigin.capture(player));

        // Transfer the owner first so a failed owner transfer cannot strand visitors in an
        // ownerless Domain session. Use resetCamera=true for a clean client dimension transition.
        BlockPos ownerSafe = findSafeSpawn(domainWorld, charge.domainCenter,
                charge.domainCenter.getX() + 0.5, charge.domainCenter.getZ() + 0.5);
        // Once the pull animation ends, do one direct cross-dimension teleport. No retry path,
        // intermediate dimension checks, or fallback transfer is used here.
        if (!caster.teleport(domainWorld, ownerSafe.getX() + 0.5, ownerSafe.getY(), ownerSafe.getZ() + 0.5,
                Set.of(), caster.getYaw(), caster.getPitch(), true)) {
            stopPullingAffectedPlayers(server, charge, sourceWorld);
            PowerManager.get(server).setTeleportGambitDomainCooldownTicks(charge.ownerId, 0);
            caster.sendMessage(Text.literal("The Domain transfer failed. You were not trapped and the cooldown was refunded; check the server log for the transfer error."), true);
            return;
        }

        DomainSession session = new DomainSession(charge.ownerId, charge.domainCenter);
        session.origins.put(charge.ownerId, origins.get(charge.ownerId));
        SESSIONS.put(charge.ownerId, session);
        SESSION_OWNER_BY_MEMBER.put(charge.ownerId, charge.ownerId);

        ServerPlayerEntity liveCaster = playerById(server, charge.ownerId);
        if (liveCaster != null) {
            ensureDomainBoost(liveCaster);
            liveCaster.setHealth(liveCaster.getMaxHealth());
        }

        for (int i = 1; i < arrivals.size(); i++) {
            ServerPlayerEntity original = arrivals.get(i);
            UUID memberId = original.getUuid();
            ServerPlayerEntity member = playerById(server, memberId);
            if (member == null || !member.isAlive()
                    || !member.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) continue;
            double angle = i * 2.399963229728653;
            double preferredX = charge.domainCenter.getX() + 0.5 + Math.cos(angle) * preferredRadius;
            double preferredZ = charge.domainCenter.getZ() + 0.5 + Math.sin(angle) * preferredRadius;
            BlockPos safe = findSafeSpawn(domainWorld, charge.domainCenter, preferredX, preferredZ);
            if (!member.teleport(domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                    Set.of(), member.getYaw(), member.getPitch(), true)) {
                ServerPlayerEntity liveMember = playerById(server, memberId);
                if (liveMember != null) {
                    liveMember.setVelocity(Vec3d.ZERO);
                    ((EntityVelocityAccessor) liveMember).invokeYppowersScheduleVelocityUpdate();
                    liveMember.sendMessage(Text.literal("The Domain transfer failed, so you were left outside instead of being trapped."), true);
                }
                LOGGER.error("Could not transfer player {} into Teleport Gambit's Domain ({} -> {}).",
                        memberId, sourceWorld.getRegistryKey().getValue(), DOMAIN_KEY.getValue());
                continue;
            }
            session.origins.put(memberId, origins.get(memberId));
            SESSION_OWNER_BY_MEMBER.put(memberId, charge.ownerId);
            ServerPlayerEntity liveMember = playerById(server, memberId);
            if (liveMember != null) {
                liveMember.setVelocity(Vec3d.ZERO);
                ((EntityVelocityAccessor) liveMember).invokeYppowersScheduleVelocityUpdate();
                domainWorld.spawnParticles(ParticleTypes.PORTAL, liveMember.getX(), liveMember.getY() + 1.0,
                        liveMember.getZ(), 32, 0.7, 1.0, 0.7, 0.16);
                domainWorld.spawnParticles(ParticleTypes.END_ROD, liveMember.getX(), liveMember.getY() + 1.0,
                        liveMember.getZ(), 14, 0.8, 1.0, 0.8, 0.05);
                domainWorld.playSound(null, safe, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.2f, 0.65f);
                liveMember.sendMessage(Text.literal("Teleport Gambit has dragged you into a pocket Domain. You are trapped until its owner dies or leaves."), false);
            }
        }

        // Reacquire the live player after transfer; never continue operating on a stale player instance.
        liveCaster = playerById(server, charge.ownerId);
        if (liveCaster != null && liveCaster.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
            liveCaster.sendMessage(Text.literal("Your Domain is open. Your core stats are multiplied by 5; jump strength is unchanged."), false);
        }
        domainWorld.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, charge.domainCenter.getX() + 0.5,
                FLOOR_Y + 1.0, charge.domainCenter.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    private static ServerPlayerEntity playerById(MinecraftServer server, UUID id) {
        return server.getPlayerManager().getPlayer(id);
    }

    /** Simple direct transfer for existing Domain recovery/return paths. Domain entry during
     * completion uses a direct call at the transfer site, with no fallback chain. */
    private static boolean teleportPlayer(MinecraftServer server, ServerPlayerEntity player, ServerWorld destination,
                                          double x, double y, double z, float yaw, float pitch) {
        return server != null && player != null && destination != null
                && player.teleport(destination, x, y, z, Set.of(), yaw, pitch, true);
    }

    private static void stopPullingAffectedPlayers(MinecraftServer server, Charge charge, ServerWorld sourceWorld) {
        for (UUID id : charge.affectedPlayers) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null && player.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) {
                player.setVelocity(Vec3d.ZERO);
                ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
            }
        }
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
            if (domainWorld == null || !owner.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
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
                if (!member.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY) || !insideDomainRegion(member, session.center)) {
                    BlockPos safe = findSafeSpawn(domainWorld, session.center, session.center.getX() + 0.5, session.center.getZ() + 0.5);
                    if (!teleportPlayer(server, member, domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                            member.getYaw(), member.getPitch())) {
                        LOGGER.error("Failed to pull player {} back into active Domain owned by {}.", memberId, session.ownerId);
                    } else {
                        ServerPlayerEntity liveMember = playerById(server, memberId);
                        if (liveMember != null) {
                            liveMember.sendMessage(Text.literal("The Domain is sealed. You cannot leave while its owner remains inside."), true);
                        }
                    }
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
                if (teleportPlayer(server, player, domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5,
                        player.getYaw(), player.getPitch())) {
                    ServerPlayerEntity livePlayer = playerById(server, id);
                    if (id.equals(active.ownerId) && livePlayer != null) ensureDomainBoost(livePlayer);
                    return;
                }
                LOGGER.error("Could not restore returning Domain member {} to the active Domain.", id);
            }
        }

        if (player.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
            // If the server crashed before the normal shutdown cleanup could run, never leave a
            // player stranded in an orphaned pocket dimension without a live owner/session.
            BlockPos overworldSpawn = server.getOverworld().getSpawnPoint().getPos();
            teleportPlayer(server, player, server.getOverworld(), overworldSpawn.getX() + 0.5,
                    overworldSpawn.getY() + 1.0, overworldSpawn.getZ() + 0.5, player.getYaw(), player.getPitch());
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
        if (source != null) {
            BlockState current = source.getBlockState(charge.framePos);
            boolean gateBlockPresent = current.isOf(Blocks.END_PORTAL_FRAME) || current.isOf(Blocks.BEACON);
            boolean clearedGateSpot = charge.gateRemoved && current.isAir();
            if (gateBlockPresent || clearedGateSpot) {
                // Restore the replaceable state that was there before charging; if it was air,
                // the gate remains gone. Do not overwrite a block another player placed here.
                source.setBlockState(charge.framePos, charge.originalState, 3);
            }
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

    private enum ChargePhase { CHARGING, BEACON_BEAM, PULLING }

    private static final class Charge {
        private final UUID ownerId;
        private final RegistryKey<World> sourceDimension;
        private final BlockPos framePos;
        private final BlockState originalState;
        private final int durationTicks;
        private final double radius;
        private final BlockPos domainCenter;
        private final Set<UUID> affectedPlayers = new HashSet<>();
        private final Map<UUID, Vec3d> pullStartPositions = new HashMap<>();
        private final PlatformBuilder platformBuilder;
        private ChargePhase phase = ChargePhase.CHARGING;
        private int phaseTicks;
        private int elapsedTicks;
        private boolean gateRemoved;

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
            if (!teleportPlayer(server, player, destination, x, y, z, yaw, pitch)) {
                LOGGER.error("Could not restore player {} to previous location {} {} {} in {}.",
                        player.getUuid(), x, y, z, destination.getRegistryKey().getValue());
            }
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
