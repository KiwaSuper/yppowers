package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import com.yppowers.power.PowerManager;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.VanillaMeleeTracker;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.DamageTypeTags;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IceEmperorPower extends Power {
    private final Map<UUID, Integer> absoluteZeroCooldowns = new HashMap<>();
    private final Map<UUID, Integer> meteorCooldowns = new HashMap<>();
    private final Map<UUID, Integer> guardSummonCooldowns = new HashMap<>();
    private final Map<UUID, java.util.Set<UUID>> guardBearIds = new HashMap<>();
    private final Map<UUID, Integer> guardRecoveryTicks = new HashMap<>();
    private final Map<UUID, Integer> playerAuraTicks = new HashMap<>();
    private final Map<UUID, IceMeteor> activeMeteors = new HashMap<>();
    private final Map<UUID, Snowstorm> activeSnowstorms = new HashMap<>();
    private final Map<UUID, HeatBlast> activeHeatBlasts = new HashMap<>();
    private static final DustParticleEffect HEAT_ORANGE = new DustParticleEffect(0xFF7A18, 1.25f);
    private static final DustParticleEffect HEAT_RED = new DustParticleEffect(0xD93420, 1.05f);
    private static final DustParticleEffect HEAT_WHITE = new DustParticleEffect(0xFFF3D6, 0.85f);

    private static final class IceMeteor {
        final ServerWorld world;
        final UUID caster;
        final double x, z;
        double y;
        final double impactY;
        final java.util.List<Entity> coreEntities = new java.util.ArrayList<>();
        IceMeteor(ServerWorld world, UUID caster, double x, double y, double z, double impactY) {
            this.world = world; this.caster = caster; this.x = x; this.y = y; this.z = z; this.impactY = impactY;
        }
    }
    private static final class Snowstorm {
        final ServerWorld world;
        final UUID caster;
        final double x, y, z;
        int ticks;
        Snowstorm(ServerWorld world, UUID caster, double x, double y, double z, int ticks) {
            this.world = world; this.caster = caster; this.x = x; this.y = y; this.z = z; this.ticks = ticks;
        }
    }
    private static final class HeatBlast {
        final ServerWorld world;
        final UUID caster;
        final Vec3d center;
        final Set<UUID> affected = new HashSet<>();
        int ticks;
        HeatBlast(ServerWorld world, UUID caster, Vec3d center) {
            this.world = world; this.caster = caster; this.center = center;
        }
    }
    /** Last server tick on which Glacial Aura dealt freeze damage to each hostile entity. */
    private final Map<UUID, Long> lastAuraFreezeDamageTicks = new HashMap<>();
    private long lastCleanupTick = Long.MIN_VALUE;

    private static final class IceFrozenEntity {
        final Vec3d position;
        final Vec3d velocity;
        final float yaw;
        final float pitch;
        long expiresAt;
        IceFrozenEntity(Entity entity, Vec3d originalVelocity, long expiresAt) {
            this.position = entity.getEntityPos();
            this.velocity = originalVelocity;
            this.yaw = entity.getYaw();
            this.pitch = entity.getPitch();
            this.expiresAt = expiresAt;
        }
    }
    private final Map<Entity, IceFrozenEntity> iceFrozenEntities = new IdentityHashMap<>();

    private static final class TemporaryBlockKey {
        final RegistryKey<World> dimension;
        final BlockPos pos;
        TemporaryBlockKey(RegistryKey<World> dimension, BlockPos pos) { this.dimension = dimension; this.pos = pos.toImmutable(); }
        @Override public boolean equals(Object other) {
            return other instanceof TemporaryBlockKey key && dimension.equals(key.dimension) && pos.equals(key.pos);
        }
        @Override public int hashCode() { return 31 * dimension.hashCode() + pos.hashCode(); }
    }
    private static final class TemporaryBlock {
        final ServerWorld world;
        final BlockPos pos;
        final BlockState original;
        BlockState placed;
        int ticks;
        TemporaryBlock(ServerWorld world, BlockPos pos, BlockState original, BlockState placed, int ticks) {
            this.world = world; this.pos = pos.toImmutable(); this.original = original; this.placed = placed; this.ticks = ticks;
        }
    }
    private final Map<TemporaryBlockKey, TemporaryBlock> temporaryBlocks = new HashMap<>();

    // Form-specific modifiers use stable identifiers so repeated switching cannot stack them.
    private static final Identifier ARMOURED_SPEED_ID = Identifier.of("yppowers", "ice_emperor_armoured_speed");
    private static final Identifier ASSASSIN_SPEED_ID = Identifier.of("yppowers", "ice_emperor_assassin_speed");
    private static final Identifier ASSASSIN_HEALTH_ID = Identifier.of("yppowers", "ice_emperor_assassin_health");
    private final Map<UUID, StatusEffectInstance> formResistanceBaseline = new HashMap<>();
    private final Set<UUID> formResistanceSnapshotTaken = new HashSet<>();
    private final Map<UUID, Integer> formResistanceElapsedTicks = new HashMap<>();
    private final Map<UUID, Integer> formResistanceAppliedAmplifier = new HashMap<>();
    private final Set<UUID> formOwnedInvisibility = new HashSet<>();
    private final Map<UUID, Boolean> stealthLastDetected = new HashMap<>();
    private final Map<UUID, Integer> stealthUpdateTicks = new HashMap<>();
    private final Map<UUID, RegistryKey<World>> formLastDimension = new HashMap<>();
    private final Map<UUID, Boolean> formActiveSyncState = new HashMap<>();

    public enum IceForm {
        ARMOURED("Armoured"), STEALTH("Stealth"), ASSASSIN("Assassin");
        private final String displayName;
        IceForm(String displayName) { this.displayName = displayName; }
        public String displayName() { return displayName; }
        public static IceForm parse(String value) {
            if (value == null) return ARMOURED;
            try { return IceForm.valueOf(value); }
            catch (IllegalArgumentException ignored) { return ARMOURED; }
        }
    }

    @Override public PowerType getType() { return PowerType.ICE_EMPEROR; }

    public static void serverTick(MinecraftServer server) {
        Power registered = PowerManager.get(server).getPowerInstance(PowerType.ICE_EMPEROR);
        if (registered instanceof IceEmperorPower ice && ice.lastCleanupTick != server.getTicks()) {
            ice.lastCleanupTick = server.getTicks();
            ice.tickTemporaryBlocks();
            ice.tickFrozenEntities(server);
            ice.tickIceMeteors(server);
            ice.tickHeatBlasts(server);
            if (server.getTicks() % 200 == 0) {
                ice.cleanupAuraFreezeDamageTicks(server.getTicks());
            }
        }
    }

    /** True while an entity is held by Absolute Zero's ice prison. */
    public static boolean isEntityFrozen(Entity entity) {
        if (entity == null || !(entity.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return false;
        Power registered = PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR);
        return registered instanceof IceEmperorPower ice && ice.iceFrozenEntities.containsKey(entity);
    }

    /** Resolve the original velocity across overlapping time stop / punch / ice freezes. */
    public static Vec3d getFrozenVelocity(Entity entity) {
        if (entity == null || !(entity.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return null;
        Power registered = PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR);
        if (!(registered instanceof IceEmperorPower ice)) return null;
        IceFrozenEntity frozen = ice.iceFrozenEntities.get(entity);
        return frozen == null ? null : frozen.velocity;
    }

    private void freezeEntity(Entity entity, long durationTicks, MinecraftServer server) {
        long expiresAt = server.getTicks() + durationTicks;
        IceFrozenEntity existing = iceFrozenEntities.get(entity);
        if (existing != null) {
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
        } else {
            Vec3d originalVelocity = TimeWarperPower.getFrozenVelocity(entity);
            if (originalVelocity == null) originalVelocity = SeriousPunchPower.getFrozenVelocity(entity);
            if (originalVelocity == null) originalVelocity = entity.getVelocity();
            iceFrozenEntities.put(entity, new IceFrozenEntity(entity, originalVelocity, expiresAt));
        }
        entity.setVelocity(Vec3d.ZERO);
        ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
    }

    private void tickFrozenEntities(MinecraftServer server) {
        long now = server.getTicks();
        Iterator<Map.Entry<Entity, IceFrozenEntity>> iterator = iceFrozenEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, IceFrozenEntity> entry = iterator.next();
            Entity entity = entry.getKey();
            IceFrozenEntity frozen = entry.getValue();
            if (entity.isRemoved() || !entity.isAlive()) {
                iterator.remove();
                continue;
            }
            if (now >= frozen.expiresAt) {
                iterator.remove();
                // Another freeze may still own the entity. Let that power restore its own
                // snapshot; otherwise restore the velocity captured before the first freeze.
                if (!TimeWarperPower.isEntityFrozen(entity) && !SeriousPunchPower.isEntityFrozen(entity)) {
                    entity.setVelocity(frozen.velocity);
                    ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
                }
                continue;
            }
            entity.setPosition(frozen.position);
            entity.setVelocity(Vec3d.ZERO);
            entity.setYaw(frozen.yaw);
            entity.setPitch(frozen.pitch);
            ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
        }
    }

    private static IceForm configuredDefaultForm(YPConfig config) {
        IceForm requested = IceForm.parse(config.ice_emperor_default_form);
        if (isFormEnabled(requested, config)) return requested;
        for (IceForm form : IceForm.values()) if (isFormEnabled(form, config)) return form;
        return IceForm.ARMOURED;
    }

    private static boolean isFormEnabled(IceForm form, YPConfig config) {
        return switch (form) {
            case ARMOURED -> config.ice_emperor_form_armoured_enabled;
            case STEALTH -> config.ice_emperor_form_stealth_enabled;
            case ASSASSIN -> config.ice_emperor_form_assassin_enabled;
        };
    }

    private IceForm selectedForm(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return IceForm.ARMOURED;
        return IceForm.parse(PowerManager.get(player.getEntityWorld().getServer()).getIceEmperorForm(player.getUuid()));
    }

    private void syncFormState(ServerPlayerEntity player, boolean active) {
        if (player.getEntityWorld().getServer() == null) return;
        formActiveSyncState.put(player.getUuid(), active);
        YPNetworking.syncIceEmperorForm(player.getEntityWorld().getServer(), player.getUuid(), selectedForm(player).name(), active);
    }

    /** Starts the visual/targeting wave after EnergyManager has atomically spent the reserve. */
    public void activateHeatBlast(ServerPlayerEntity caster) {
        if (caster == null || caster.isRemoved() || !caster.isAlive()
                || !(caster.getEntityWorld() instanceof ServerWorld world)
                || world.getServer() == null || GraceManager.isActive(world.getServer())) return;
        if (PowerManager.get(world.getServer()).getPowerType(caster.getUuid()) != PowerType.ICE_EMPEROR) return;
        HeatBlast blast = new HeatBlast(world, caster.getUuid(), caster.getEntityPos().add(0.0, 0.8, 0.0));
        activeHeatBlasts.put(caster.getUuid(), blast);
        world.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT,
                SoundCategory.PLAYERS, 1.7f, 0.55f);
        world.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                SoundCategory.PLAYERS, 1.0f, 0.65f);
        EpicVfx.impact(world, caster.getX(), caster.getY() + 0.7, caster.getZ(), 1.8);
        world.spawnParticles(HEAT_WHITE, caster.getX(), caster.getY() + 0.8, caster.getZ(),
                18, 0.45, 0.35, 0.45, 0.08);
        world.spawnParticles(ParticleTypes.FLAME, caster.getX(), caster.getY() + 0.8, caster.getZ(),
                24, 0.4, 0.3, 0.4, 0.06);
    }

    /** Expands one controlled ring per tick; each eligible target is affected once per cast. */
    private void tickHeatBlasts(MinecraftServer server) {
        if (activeHeatBlasts.isEmpty()) return;
        Iterator<Map.Entry<UUID, HeatBlast>> iterator = activeHeatBlasts.entrySet().iterator();
        while (iterator.hasNext()) {
            HeatBlast blast = iterator.next().getValue();
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(blast.caster);
            if (caster == null || !caster.isAlive() || caster.isRemoved()
                    || caster.getEntityWorld() != blast.world || GraceManager.isActive(server)) {
                iterator.remove();
                continue;
            }
            blast.ticks++;
            YPConfig config = YPConfig.getInstance();
            double maxRadius = config.ice_emperor_heat_blast_radius;
            double progress = Math.min(1.0, blast.ticks / 12.0);
            double radius = maxRadius * progress;
            double y = blast.center.y;
            Box bounds = new Box(blast.center.x - radius, y - 3.5, blast.center.z - radius,
                    blast.center.x + radius, y + 3.5, blast.center.z + radius);
            double radiusSquared = radius * radius;
            for (LivingEntity target : blast.world.getEntitiesByClass(LivingEntity.class, bounds,
                    entity -> entity.isAlive() && !entity.isRemoved() && !blast.affected.contains(entity.getUuid())
                            && PowerCombatUtil.canHarm(caster, entity))) {
                double dx = target.getX() - blast.center.x;
                double dz = target.getZ() - blast.center.z;
                if (dx * dx + dz * dz > radiusSquared) continue;
                blast.affected.add(target.getUuid());
                target.setOnFireFor(config.ice_emperor_heat_blast_fire_seconds);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS,
                        config.ice_emperor_heat_blast_slowness_ticks,
                        config.ice_emperor_heat_blast_slowness_amplifier, false, true, true));
            }

            // Evenly spaced samples form a smooth expanding circle with a strict particle budget.
            int samples = 24;
            for (int i = 0; i < samples; i++) {
                double angle = (Math.PI * 2.0 * i) / samples;
                double x = blast.center.x + Math.cos(angle) * radius;
                double z = blast.center.z + Math.sin(angle) * radius;
                double waveY = y + 0.10 + Math.sin(angle * 2.0 + blast.ticks * 0.35) * 0.18;
                blast.world.spawnParticles((i % 3 == 0) ? HEAT_RED : HEAT_ORANGE,
                        x, waveY, z, 1, 0.025, 0.04, 0.025, 0.004);
                if (i % 4 == 0 && blast.ticks % 2 == 0) {
                    blast.world.spawnParticles(HEAT_WHITE, x, waveY + 0.04, z,
                            1, 0.015, 0.025, 0.015, 0.002);
                }
            }
            if (blast.ticks % 3 == 0) {
                blast.world.spawnParticles(ParticleTypes.FLAME, blast.center.x, y, blast.center.z,
                        8, 0.35 + radius * 0.025, 0.18, 0.35 + radius * 0.025, 0.035);
            }
            if (blast.ticks >= 12) iterator.remove();
        }
    }

    /** Keybind entry point; the server owns form selection and checks Grace again defensively. */
    public void cycleForm(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        MinecraftServer server = player.getEntityWorld().getServer();
        PowerManager manager = PowerManager.get(server);
        YPConfig config = YPConfig.getInstance();
        if (manager.getPowerType(player.getUuid()) != PowerType.ICE_EMPEROR) return;
        if (GraceManager.blockAbility(player)) return;
        if (!config.ice_emperor_forms_enabled || !manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.sendMessage(Text.literal("Ice Emperor Forms are currently disabled.").formatted(Formatting.YELLOW), true);
            return;
        }

        IceForm current = selectedForm(player);
        IceForm next = current;
        for (int step = 1; step <= IceForm.values().length; step++) {
            IceForm candidate = IceForm.values()[(current.ordinal() + step) % IceForm.values().length];
            if (isFormEnabled(candidate, config)) { next = candidate; break; }
        }
        if (next == current) {
            player.sendMessage(Text.literal("No other Ice Emperor forms are enabled.").formatted(Formatting.YELLOW), true);
            return;
        }

        clearFormEffects(player);
        manager.setIceEmperorForm(player.getUuid(), next.name());
        formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
        if (config.ice_emperor_forms_action_bar_enabled) {
            player.sendMessage(Text.literal("Ice Emperor Form: ").formatted(Formatting.AQUA)
                    .append(Text.literal(next.displayName()).formatted(next == IceForm.ASSASSIN ? Formatting.RED
                            : next == IceForm.STEALTH ? Formatting.GRAY : Formatting.BLUE)), true);
        }
        if (player.getEntityWorld() instanceof ServerWorld world) {
            if (config.ice_emperor_forms_particles_enabled) {
                world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(),
                        24, 0.55, 0.8, 0.55, 0.02);
                world.spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.9, player.getZ(),
                        5, 0.25, 0.35, 0.25, 0.01);
            }
            if (config.ice_emperor_forms_sound_enabled) {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK,
                        SoundCategory.PLAYERS, 0.55f, next == IceForm.ASSASSIN ? 1.35f : 0.9f);
            }
        }
        syncFormState(player, true);
    }

    private void tickForms(ServerPlayerEntity player, ServerWorld world) {
        UUID id = player.getUuid();
        YPConfig config = YPConfig.getInstance();
        PowerManager manager = PowerManager.get(world.getServer());
        RegistryKey<World> priorDimension = formLastDimension.put(id, world.getRegistryKey());
        if (priorDimension != null && !priorDimension.equals(world.getRegistryKey())) {
            clearFormEffects(player);
        }

        boolean formsActive = config.ice_emperor_forms_enabled
                && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
                && !GraceManager.isActive(world.getServer());
        Boolean previouslyActive = formActiveSyncState.put(id, formsActive);
        if (previouslyActive == null || previouslyActive != formsActive) {
            YPNetworking.syncIceEmperorForm(world.getServer(), id, selectedForm(player).name(), formsActive);
        }
        if (!formsActive) {
            clearFormEffects(player);
            return;
        }

        IceForm form = selectedForm(player);
        if (!isFormEnabled(form, config)) {
            IceForm replacement = configuredDefaultForm(config);
            if (!isFormEnabled(replacement, config)) {
                clearFormEffects(player);
                return;
            }
            clearFormEffects(player);
            manager.setIceEmperorForm(id, replacement.name());
            form = replacement;
            if (config.ice_emperor_forms_action_bar_enabled) {
                player.sendMessage(Text.literal("Ice Emperor Form changed to: " + form.displayName()).formatted(Formatting.AQUA), true);
            }
            YPNetworking.syncIceEmperorForm(world.getServer(), id, form.name(), true);
        }

        ensureMultiplier(player, EntityAttributes.MOVEMENT_SPEED, ARMOURED_SPEED_ID,
                form == IceForm.ARMOURED ? config.ice_emperor_armoured_movement_multiplier - 1.0 : 0.0);
        ensureMultiplier(player, EntityAttributes.MOVEMENT_SPEED, ASSASSIN_SPEED_ID,
                form == IceForm.ASSASSIN ? config.ice_emperor_assassin_movement_multiplier - 1.0 : 0.0);
        ensureMultiplier(player, EntityAttributes.MAX_HEALTH, ASSASSIN_HEALTH_ID,
                form == IceForm.ASSASSIN ? config.ice_emperor_assassin_max_health_multiplier - 1.0 : 0.0);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));

        if (form == IceForm.ARMOURED) tickArmouredResistance(player, config);
        else clearArmouredResistance(player);

        if (form == IceForm.STEALTH) tickStealth(player, world, config);
        else clearStealth(player);

        if (config.ice_emperor_forms_particles_enabled && form == IceForm.ARMOURED && player.age % 12 == 0) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.9, player.getZ(),
                    2, 0.35, 0.45, 0.35, 0.005);
        }
        if (config.ice_emperor_forms_particles_enabled && form == IceForm.ASSASSIN
                && config.ice_emperor_assassin_ice_trail_enabled && player.isSprinting()
                && (player.getVelocity().x * player.getVelocity().x + player.getVelocity().z * player.getVelocity().z) > 0.0025 && player.age % 4 == 0) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.08, player.getZ(),
                    4, 0.18, 0.025, 0.18, 0.01);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                    player.getX(), player.getY() + 0.04, player.getZ(), 1, 0.12, 0.01, 0.12, 0.01);
        }
    }

    private void tickArmouredResistance(ServerPlayerEntity player, YPConfig config) {
        UUID id = player.getUuid();
        if (formResistanceSnapshotTaken.add(id)) {
            StatusEffectInstance previous = player.getStatusEffect(StatusEffects.RESISTANCE);
            formResistanceBaseline.put(id, previous == null ? null : new StatusEffectInstance(previous));
            formResistanceElapsedTicks.put(id, 0);
        } else {
            formResistanceElapsedTicks.merge(id, 1, Integer::sum);
        }

        int targetAmplifier = config.ice_emperor_armoured_resistance_amplifier;
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.RESISTANCE);
        Integer previouslyAppliedAmplifier = formResistanceAppliedAmplifier.get(id);
        boolean looksLikeOurShortRefresh = current != null && previouslyAppliedAmplifier != null
                && current.getAmplifier() == previouslyAppliedAmplifier && current.getDuration() <= 8;
        if (formResistanceSnapshotTaken.contains(id) && current != null && !looksLikeOurShortRefresh) {
            // Another system changed Resistance while the form was active. Preserve that latest
            // independent instance as the effect restored when leaving the form, including its
            // remaining duration as of this observation.
            formResistanceBaseline.put(id, new StatusEffectInstance(current));
            formResistanceElapsedTicks.put(id, 0);
        }
        if (current != null && current.getAmplifier() > targetAmplifier) {
            // A stronger independent Resistance effect already provides at least the form's benefit.
            return;
        }
        if (current == null || current.getAmplifier() < targetAmplifier
                || (current.getAmplifier() == targetAmplifier && current.getDuration() <= 2)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 8, targetAmplifier,
                    false, false, true));
            formResistanceAppliedAmplifier.put(id, targetAmplifier);
        }
    }

    private void clearArmouredResistance(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        boolean hadSnapshot = formResistanceSnapshotTaken.remove(id);
        int elapsed = formResistanceElapsedTicks.getOrDefault(id, 0);
        Integer appliedAmplifier = formResistanceAppliedAmplifier.remove(id);
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.RESISTANCE);
        // Only remove the short-duration effect this form refreshes. A longer or stronger
        // resistance effect is presumed to have been supplied independently and is preserved.
        boolean currentLooksOwned = current != null && appliedAmplifier != null
                && current.getAmplifier() == appliedAmplifier && current.getDuration() <= 8;
        if (currentLooksOwned) player.removeStatusEffect(StatusEffects.RESISTANCE);
        if (hadSnapshot && (currentLooksOwned || current == null)) {
            StatusEffectInstance baseline = formResistanceBaseline.get(id);
            restoreResistance(player, baseline, elapsed);
        }
        formResistanceBaseline.remove(id);
        formResistanceElapsedTicks.remove(id);
    }

    private static void restoreResistance(ServerPlayerEntity player, StatusEffectInstance saved, int elapsedTicks) {
        if (saved == null) return;
        int duration = saved.getDuration();
        int remaining = duration < 0 ? duration : duration - Math.max(0, elapsedTicks);
        if (remaining == 0 || remaining < -1) return;
        player.addStatusEffect(new StatusEffectInstance(saved.getEffectType(), remaining, saved.getAmplifier(),
                saved.isAmbient(), saved.shouldShowParticles(), saved.shouldShowIcon()));
    }

    private void tickStealth(ServerPlayerEntity player, ServerWorld world, YPConfig config) {
        UUID id = player.getUuid();
        int elapsed = stealthUpdateTicks.getOrDefault(id, 0) + 1;
        int interval = Math.max(1, config.ice_emperor_stealth_update_interval_ticks);
        boolean hasPriorDetection = stealthLastDetected.containsKey(id);
        if (elapsed < interval && hasPriorDetection) {
            stealthUpdateTicks.put(id, elapsed);
            return;
        }
        stealthUpdateTicks.put(id, 0);

        boolean detected = false;
        double radius = Math.max(1.0, config.ice_emperor_stealth_detection_radius);
        double radiusSquared = radius * radius;
        AllianceManager alliances = AllianceManager.get(world.getServer());
        for (ServerPlayerEntity other : world.getPlayers()) {
            if (other == player || !other.isAlive() || other.isSpectator()) continue;
            if (other.squaredDistanceTo(player) > radiusSquared) continue;
            if (!config.ice_emperor_stealth_allies_count && alliances.isAlly(player, other)) continue;
            detected = true;
            break;
        }

        Boolean oldDetected = stealthLastDetected.put(id, detected);
        if (!detected && !player.isInvisible()) {
            player.setInvisible(true);
            formOwnedInvisibility.add(id);
        } else if (detected && formOwnedInvisibility.remove(id)) {
            // setInvisible(false) only clears the entity flag; potion-sourced invisibility remains.
            player.setInvisible(false);
        }

        if (oldDetected == null || oldDetected != detected) {
            if (config.ice_emperor_forms_particles_enabled) {
                world.spawnParticles(detected ? ParticleTypes.POOF : ParticleTypes.SNOWFLAKE,
                        player.getX(), player.getY() + 1.0, player.getZ(), detected ? 8 : 12,
                        0.35, 0.6, 0.35, 0.01);
            }
        }
    }

    private void clearStealth(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (formOwnedInvisibility.remove(id)) {
            // Do not remove invisibility from an effect such as a potion; only clear our entity flag.
            player.setInvisible(false);
        }
        stealthLastDetected.remove(id);
        stealthUpdateTicks.remove(id);
    }

    private void ensureMultiplier(ServerPlayerEntity player,
                                  net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                                  Identifier id, double amount) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) return;
        EntityAttributeModifier modifier = instance.getModifier(id);
        if (Math.abs(amount) < 1.0e-6) {
            if (modifier != null) instance.removeModifier(id);
            return;
        }
        if (modifier == null || Math.abs(modifier.value() - amount) > 1.0e-6
                || modifier.operation() != EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) {
            if (modifier != null) instance.removeModifier(id);
            instance.addTemporaryModifier(new EntityAttributeModifier(id, amount,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void removeFormModifiers(ServerPlayerEntity player) {
        for (var pair : new Object[][]{
                {EntityAttributes.MOVEMENT_SPEED, ARMOURED_SPEED_ID},
                {EntityAttributes.MOVEMENT_SPEED, ASSASSIN_SPEED_ID},
                {EntityAttributes.MAX_HEALTH, ASSASSIN_HEALTH_ID}}) {
            @SuppressWarnings("unchecked") net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute =
                    (net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>) pair[0];
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance != null) instance.removeModifier((Identifier) pair[1]);
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private void clearFormEffects(ServerPlayerEntity player) {
        removeFormModifiers(player);
        clearArmouredResistance(player);
        clearStealth(player);
    }

    private void setConfiguredDefault(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        PowerManager.get(player.getEntityWorld().getServer()).setIceEmperorForm(player.getUuid(),
                configuredDefaultForm(YPConfig.getInstance()).name());
    }

    @Override public void onAssign(ServerPlayerEntity player) {
        clearFormEffects(player);
        formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
        setConfiguredDefault(player);
        if (player.getEntityWorld().getServer() != null) {
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            boolean active = YPConfig.getInstance().ice_emperor_forms_enabled
                    && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
                    && !GraceManager.isActive(player.getEntityWorld().getServer());
            syncFormState(player, active);
        }
    }

    @Override public void onPlayerJoin(ServerPlayerEntity player) {
        clearFormEffects(player);
        formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
        if (player.getEntityWorld().getServer() != null) {
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            if (!YPConfig.getInstance().ice_emperor_form_persist_through_reconnect
                    || !manager.hasIceEmperorForm(player.getUuid())) setConfiguredDefault(player);
            boolean active = YPConfig.getInstance().ice_emperor_forms_enabled
                    && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
                    && manager.getPowerType(player.getUuid()) == PowerType.ICE_EMPEROR
                    && !GraceManager.isActive(player.getEntityWorld().getServer());
            syncFormState(player, active);
        }
        if (player.getEntityWorld().getServer() != null
                && !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.removeStatusEffect(StatusEffects.SPEED);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
        }
    }

    @Override public void onPlayerDisconnect(ServerPlayerEntity player) {
        clearFormEffects(player);
        formLastDimension.remove(player.getUuid());
        playerAuraTicks.remove(player.getUuid());
        if (!YPConfig.getInstance().ice_emperor_form_persist_through_reconnect) setConfiguredDefault(player);
        if (player.getEntityWorld().getServer() != null) {
            syncFormState(player, false);
        }
        formActiveSyncState.remove(player.getUuid());
    }

    @Override public void onPlayerDeath(ServerPlayerEntity player) {
        clearFormEffects(player);
        formLastDimension.remove(player.getUuid());
        if (!YPConfig.getInstance().ice_emperor_form_persist_through_death) setConfiguredDefault(player);
        if (player.getEntityWorld().getServer() != null) {
            syncFormState(player, false);
        }
    }

    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        clearFormEffects(player);
        formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
        if (!YPConfig.getInstance().ice_emperor_form_persist_through_death) setConfiguredDefault(player);
        if (player.getEntityWorld().getServer() != null) {
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            boolean active = YPConfig.getInstance().ice_emperor_forms_enabled
                    && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
                    && !GraceManager.isActive(player.getEntityWorld().getServer());
            syncFormState(player, active);
        }
        if (player.getEntityWorld().getServer() != null
                && !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.removeStatusEffect(StatusEffects.SPEED);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
        }
    }

    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        boolean enabled = PowerManager.get(player.getEntityWorld().getServer()).togglePowerEnabled(player, PowerType.ICE_EMPEROR);
        if (!enabled) {
            playerAuraTicks.remove(player.getUuid());
            clearFormEffects(player);
            player.removeStatusEffect(StatusEffects.SPEED);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
            player.sendMessage(Text.literal("Ice Emperor's powers and Glacial Field are disabled. Press R to re-enable them.").formatted(Formatting.YELLOW), true);
        } else {
            player.sendMessage(Text.literal("Ice Emperor's powers and Glacial Field are enabled.").formatted(Formatting.AQUA), true);
        }
        syncFormState(player, enabled && YPConfig.getInstance().ice_emperor_forms_enabled
                && !GraceManager.isActive(player.getEntityWorld().getServer()));
    }

    /** V: summon a configurable number of persistent polar-bear bodyguards. */
    @Override public void onTertiaryActivate(ServerPlayerEntity player) {
        summonPolarBearGuards(player, false);
    }

    /** Called by the shared Energy layer with the result of the special Frost Guard payment. */
    public void onTertiaryActivateWithEnergyState(ServerPlayerEntity player, boolean lowEnergy) {
        summonPolarBearGuards(player, lowEnergy);
    }

    private void summonPolarBearGuards(ServerPlayerEntity player, boolean lowEnergy) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        if (!PowerManager.get(world.getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
            return;
        }
        UUID ownerId = player.getUuid();
        int cooldown = guardSummonCooldowns.getOrDefault(ownerId, 0);
        if (cooldown > 0) {
            player.sendMessage(Text.literal("Ice Bear Guards cooldown: " + ((cooldown + 19) / 20) + "s.")
                    .formatted(Formatting.YELLOW), true);
            return;
        }

        java.util.Set<UUID> ids = guardBearIds.computeIfAbsent(ownerId, ignored -> new java.util.HashSet<>());
        recoverNearbyGuards(player, world, ids);
        int desired = Math.max(1, YPConfig.getInstance().ice_emperor_guard_count);
        int active = countLiveGuards(world, ids);
        if (active >= desired) {
            player.sendMessage(Text.literal("Your polar-bear guards are already at full strength.")
                    .formatted(Formatting.AQUA), true);
            return;
        }

        int spawned = 0;
        int hostileSpawned = 0;
        for (int i = active; i < desired; i++) {
            double angle = ((double) i / desired) * Math.PI * 2.0 + Math.toRadians(player.getYaw());
            double x = player.getX() + Math.cos(angle) * 1.8;
            double z = player.getZ() + Math.sin(angle) * 1.8;
            BlockPos spawnPos = BlockPos.ofFloored(x, player.getY(), z);
            // EntityType.spawn performs mob initialization; create()+spawnEntity alone would
            // skip the normal spawn initialization used by mobs with AI/attributes.
            PolarBearEntity bear = EntityType.POLAR_BEAR.spawn(world, spawnPos, SpawnReason.MOB_SUMMONED);
            if (bear == null) continue;
            bear.refreshPositionAndAngles(x, player.getY(), z, player.getYaw(), 0.0f);
            bear.setPersistent();
            bear.addCommandTag(guardOwnerTag(ownerId));
            boolean rogue = lowEnergy
                    && world.getRandom().nextDouble() * 100.0 < YPConfig.getInstance().ice_emperor_guard_hostile_chance_percent;
            if (lowEnergy) weakenLowEnergyGuard(bear);
            if (rogue) {
                bear.addCommandTag(guardHostileTag());
                bear.setCustomName(Text.literal("Rogue Frost Guard").formatted(Formatting.RED, Formatting.BOLD));
                // It is genuinely hostile, not just a red-named neutral bear: the per-tick guard
                // controller keeps its target fixed on the summoner and the damage filter allows
                // this marked rogue (and only this marked rogue) to hurt its owner.
                bear.setTarget(player);
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, x, player.getY() + 1.1, z,
                        8, 0.45, 0.5, 0.45, 0.01);
            } else {
                bear.setCustomName(Text.literal("Ice Emperor's Guard").formatted(Formatting.AQUA, Formatting.BOLD));
            }
            bear.setCustomNameVisible(rogue);
            // EntityType.spawn already inserted the bear into the world.
            ids.add(bear.getUuid());
            spawned++;
            if (rogue) hostileSpawned++;
        }

        if (spawned > 0) {
            guardSummonCooldowns.put(ownerId, Math.max(0, YPConfig.getInstance().ice_emperor_guard_cooldown_ticks));
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.PLAYERS, 1.4f, 0.75f);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(),
                    65, 1.5, 1.0, 1.5, 0.045);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                    player.getX(), player.getY() + 0.8, player.getZ(), 24, 1.0, 0.6, 1.0, 0.08);
            if (lowEnergy) {
                String suffix = hostileSpawned > 0
                        ? " " + hostileSpawned + " ROGUE BEAR(S) HAVE TURNED ON YOU!"
                        : " The guards are weakened by your depleted Energy.";
                player.sendMessage(Text.literal("THE FROST GUARD ANSWERS IN WEAKNESS — " + spawned + " bear(s)." + suffix)
                        .formatted(hostileSpawned > 0 ? Formatting.RED : Formatting.YELLOW, Formatting.BOLD), true);
            } else {
                player.sendMessage(Text.literal("THE FROST GUARD ANSWERS — " + spawned + " polar bear(s) bound to you.")
                        .formatted(Formatting.AQUA, Formatting.BOLD), true);
            }
        } else {
            player.sendMessage(Text.literal("The Frost Guard could not find room to manifest.").formatted(Formatting.RED), true);
        }
    }

    private static String guardOwnerTag(UUID ownerId) {
        return "yppowers_ice_guard_owner:" + ownerId;
    }

    private static String guardHostileTag() {
        return "yppowers_ice_guard_rogue";
    }

    private static void weakenLowEnergyGuard(PolarBearEntity bear) {
        YPConfig config = YPConfig.getInstance();
        EntityAttributeInstance speed = bear.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(Math.max(0.01, speed.getBaseValue() * config.ice_emperor_guard_low_energy_speed_multiplier));
        }
        EntityAttributeInstance attack = bear.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(Math.max(0.1, attack.getBaseValue() * config.ice_emperor_guard_low_energy_damage_multiplier));
        }
    }

    private void recoverNearbyGuards(ServerPlayerEntity owner, ServerWorld world, java.util.Set<UUID> ids) {
        int recovery = guardRecoveryTicks.getOrDefault(owner.getUuid(), 0);
        if (recovery > 0) {
            guardRecoveryTicks.put(owner.getUuid(), recovery - 1);
            return;
        }
        guardRecoveryTicks.put(owner.getUuid(), 100);
        Box scan = owner.getBoundingBox().expand(128.0);
        for (PolarBearEntity bear : world.getEntitiesByClass(PolarBearEntity.class, scan,
                candidate -> candidate.getCommandTags().contains(guardOwnerTag(owner.getUuid())) && candidate.isAlive())) {
            ids.add(bear.getUuid());
        }
    }

    private int countLiveGuards(ServerWorld world, java.util.Set<UUID> ids) {
        int alive = 0;
        Iterator<UUID> iterator = ids.iterator();
        while (iterator.hasNext()) {
            UUID id = iterator.next();
            Entity entity = world.getEntity(id);
            if (entity == null) {
                // A null lookup can mean the bear is in an unloaded chunk. Keep its slot so
                // repeated summoning cannot stack extra guards while a bonded bear is unloaded.
                alive++;
            } else if (entity instanceof PolarBearEntity bear && bear.isAlive()
                    && bear.getCommandTags().stream().anyMatch(tag -> tag.startsWith("yppowers_ice_guard_owner:"))) {
                alive++;
            } else {
                iterator.remove();
            }
        }
        return alive;
    }

    private void tickIceGuards(ServerPlayerEntity owner, ServerWorld world) {
        if (world.getServer() == null) return;
        java.util.Set<UUID> ids = guardBearIds.computeIfAbsent(owner.getUuid(), ignored -> new java.util.HashSet<>());
        recoverNearbyGuards(owner, world, ids);
        for (UUID id : new java.util.ArrayList<>(ids)) {
            Entity entity = world.getEntity(id);
            if (!(entity instanceof PolarBearEntity bear) || bear.isRemoved() || !bear.isAlive()) continue;
            if (bear.getCommandTags().contains(guardHostileTag())) {
                // Rogue low-Energy spawns remain hostile to their summoner until they die.
                if (owner.isAlive() && bear.getTarget() != owner) bear.setTarget(owner);
                continue;
            }
            LivingEntity target = owner.getAttacker();
            if (target == null || !target.isAlive() || isGuardAlly(owner, target)) {
                target = owner.getAttacking();
            }
            if (target != null && (!target.isAlive() || target == owner || isGuardAlly(owner, target))) target = null;

            LivingEntity current = bear.getTarget();
            if (current != null && (!current.isAlive() || current == owner || isGuardAlly(owner, current))) {
                bear.setTarget(null);
                current = null;
            }
            if (target != null && bear.getTarget() != target) bear.setTarget(target);

            if (bear.getTarget() == null) {
                double distance = bear.squaredDistanceTo(owner);
                if (distance > 16.0) {
                    bear.getNavigation().startMovingTo(owner, 1.25);
                } else {
                    bear.getNavigation().stop();
                }
            }
        }
    }

    private boolean isGuardAlly(ServerPlayerEntity owner, LivingEntity target) {
        return target instanceof ServerPlayerEntity other
                && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other);
    }

    /** Called from LivingEntityMixin after an entity death. A fallen guard wounds its master. */
    public static void onIceGuardDeath(LivingEntity deadEntity) {
        if (!(deadEntity instanceof PolarBearEntity bear)
                || !(bear.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        String prefix = "yppowers_ice_guard_owner:";
        UUID ownerId = null;
        for (String tag : bear.getCommandTags()) {
            if (!tag.startsWith(prefix)) continue;
            try { ownerId = UUID.fromString(tag.substring(prefix.length())); }
            catch (IllegalArgumentException ignored) { }
            break;
        }
        if (ownerId == null) return;

        Power instance = PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR);
        if (instance instanceof IceEmperorPower ice) {
            java.util.Set<UUID> ids = ice.guardBearIds.get(ownerId);
            if (ids != null) ids.remove(bear.getUuid());
        }
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        if (owner == null || !owner.isAlive() || !(owner.getEntityWorld() instanceof ServerWorld ownerWorld)) return;
        float damage = (float) Math.max(0.0, YPConfig.getInstance().ice_emperor_guard_death_damage);
        if (damage > 0.0f) {
            owner.damage(ownerWorld, ownerWorld.getDamageSources().magic(), damage);
            owner.sendMessage(Text.literal("A Frost Guard has fallen. The bond tears through you for "
                    + damage + " damage.").formatted(Formatting.AQUA, Formatting.RED), true);
            ownerWorld.spawnParticles(ParticleTypes.SNOWFLAKE, owner.getX(), owner.getY() + 1.0, owner.getZ(),
                    30, 0.7, 0.8, 0.7, 0.03);
        }
    }

    /** True when a guard's attack must not damage its owner or their allies. */
    public static boolean shouldGuardIgnore(Entity guard, ServerPlayerEntity target) {
        if (!(guard instanceof PolarBearEntity bear) || !(bear.getEntityWorld() instanceof ServerWorld world)
                || world.getServer() == null) return false;
        String prefix = "yppowers_ice_guard_owner:";
        for (String tag : bear.getCommandTags()) {
            if (!tag.startsWith(prefix)) continue;
            try {
                UUID ownerId = UUID.fromString(tag.substring(prefix.length()));
                if (ownerId.equals(target.getUuid())) {
                    // Only a hostile low-Energy rogue may damage its own summoner.
                    return !bear.getCommandTags().contains(guardHostileTag());
                }
                ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
                return owner != null && AllianceManager.get(world.getServer()).isAlly(owner, target);
            } catch (IllegalArgumentException ignored) { return false; }
        }
        return false;
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        tickForms(player, world);

        int cd = absoluteZeroCooldowns.getOrDefault(uuid, 0);
        if (cd > 0) absoluteZeroCooldowns.put(uuid, cd - 1);
        int meteorCd = meteorCooldowns.getOrDefault(uuid, 0);
        if (meteorCd > 0) meteorCooldowns.put(uuid, meteorCd - 1);
        int guardCd = guardSummonCooldowns.getOrDefault(uuid, 0);
        if (guardCd > 0) guardSummonCooldowns.put(uuid, guardCd - 1);

        // Summoned guards remain bonded to their owner and keep defending/following even while
        // Ice Emperor's passive field is toggled off. This is entity upkeep, not Energy drain.
        tickIceGuards(player, world);

        if (world.getServer() == null
                || !PowerManager.get(world.getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) return;

        int radius = Math.max(1, (int) Math.ceil(YPConfig.getInstance().ice_emperor_aura_radius));
        if (player.getFrozenTicks() > 0) player.setFrozenTicks(0);

        int auraTicks = playerAuraTicks.merge(uuid, 1, Integer::sum);
        if (auraTicks >= 5) {
            playerAuraTicks.put(uuid, 0);
            applyGlacialAura(player, world, radius);
            spawnVisibleGlacialField(player, world, radius);
        }

        // Build a continuous slippery ribbon while the player sprint-glides. The previous
        // implementation only refreshed every fourth tick, leaving gaps at normal sprint speed.
        Vec3d velocity = player.getVelocity();
        double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        boolean sprintGliding = player.isSprinting() && horizontalSpeedSquared > 0.0025;
        if (sprintGliding) {
            double trailX = player.getX() - velocity.x * 1.15;
            double trailZ = player.getZ() - velocity.z * 1.15;
            double trailY = player.getY() + 0.06;

            // Visual trail is refreshed every server tick, including airborne sprints.
            world.spawnParticles(ParticleTypes.SNOWFLAKE, trailX, trailY, trailZ,
                    3, 0.09, 0.025, 0.09, 0.002);
            if (player.age % 2 == 0) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                        trailX, trailY, trailZ, 1, 0.06, 0.015, 0.06, 0.015);
            }

            // Place ice at the current footfall and slightly behind it. Temporary-block
            // bookkeeping preserves the original ground and refreshes the trail without gaps.
            if (player.isOnGround()) {
                BlockPos underfoot = player.getBlockPos().down();
                placeTrailIceIfSafe(world, underfoot);
                BlockPos behind = BlockPos.ofFloored(trailX, player.getY(), trailZ).down();
                if (!behind.equals(underfoot)) placeTrailIceIfSafe(world, behind);
            }
        }

        BlockState under = world.getBlockState(player.getBlockPos().down());
        if (isIce(under) || sprintGliding) {
            // Refresh every tick so the speed aid does not flicker as the player crosses
            // between freshly-created ice blocks.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 0, false, false, true));
        }
        if (world.getRegistryKey() == World.NETHER) player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false, true));

        // Temporary blocks are aged by serverTick(), even after the power is unassigned.
    }

    private void placeTrailIceIfSafe(ServerWorld world, BlockPos pos) {
        TemporaryBlockKey key = new TemporaryBlockKey(world.getRegistryKey(), pos);
        if (temporaryBlocks.containsKey(key)) {
            // Refresh only blocks this trail already owns; natural ice elsewhere is left intact.
            placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), 60);
            return;
        }
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.HEAVY_CORE) && ThorPower.isProtectedHeavyCore(world, pos)) return;
        if (isIce(state) || !state.isOpaque() || state.hasBlockEntity()
                || state.getHardness(world, pos) < 0 || state.isOf(Blocks.BEDROCK)) return;
        placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), 60);
    }

    /** A persistent snowflake cylinder makes the passive field visible, not just its effects. */
    private void spawnVisibleGlacialField(ServerPlayerEntity player, ServerWorld world, int radius) {
        double x = player.getX();
        double z = player.getZ();
        double[] heights = {0.15, 1.0, 1.85};
        for (double height : heights) {
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle + (player.age * 3 % 360));
                double px = x + Math.cos(radians) * radius;
                double pz = z + Math.sin(radians) * radius;
                world.spawnParticles(ParticleTypes.SNOWFLAKE, px, player.getY() + height, pz,
                        1, 0.025, 0.04, 0.025, 0.005);
            }
        }
        // A smaller, slowly rotating inner ring gives the field depth without flooding the server.
        for (int angle = 0; angle < 360; angle += 45) {
            double radians = Math.toRadians(-angle + player.age * 2 % 360);
            double innerRadius = radius * 0.58;
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    x + Math.cos(radians) * innerRadius, player.getY() + 1.0,
                    z + Math.sin(radians) * innerRadius, 1, 0.02, 0.03, 0.02, 0.002);
        }
    }

    private void applyGlacialAura(ServerPlayerEntity player, ServerWorld world, int radius) {
        BlockPos center = player.getBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (pos.getSquaredDistance(center) > radius * radius) continue;
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(Blocks.WATER)) {
                        placeTemporary(world, pos, Blocks.FROSTED_ICE.getDefaultState(), 100);
                    } else if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }

        Box box = player.getBoundingBox().expand(radius);
        long now = world.getServer().getTicks();
        YPConfig config = YPConfig.getInstance();
        int damageInterval = Math.max(1, config.ice_emperor_aura_freeze_damage_interval_ticks);
        float freezeDamage = (float) Math.max(0.0, config.ice_emperor_aura_freeze_damage);
        for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, box,
                e -> e.isAlive() && e.squaredDistanceTo(player) <= radius * radius)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, false, true));

            // Glacial Aura deals vanilla freeze-type damage, at a controlled interval, while
            // a hostile remains inside the field. The shared per-entity timestamp prevents
            // overlapping Ice Emperor fields from multiplying the damage rate.
            long lastDamageTick = lastAuraFreezeDamageTicks.computeIfAbsent(mob.getUuid(), ignored -> now);
            if (freezeDamage > 0.0f && now - lastDamageTick >= damageInterval) {
                mob.damage(world, world.getDamageSources().freeze(), freezeDamage);
                lastAuraFreezeDamageTicks.put(mob.getUuid(), now);
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        mob.getX(), mob.getY() + 1, mob.getZ(), 6, 0.22, 0.24, 0.22, 0.04);
                world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_GLASS_HIT,
                        SoundCategory.HOSTILE, 0.35f, 1.8f);
            }
            world.spawnParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + 1, mob.getZ(),
                    2, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private void cleanupAuraFreezeDamageTicks(long now) {
        // Hostile UUIDs can accumulate as mobs die or despawn. Entries idle for a minute
        // are no longer needed for the field's damage cadence.
        lastAuraFreezeDamageTicks.entrySet().removeIf(entry -> now - entry.getValue() > 1200);
    }

    private static boolean isIce(BlockState state) {
        return state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.ICE) || state.isOf(Blocks.BLUE_ICE) || state.isOf(Blocks.FROSTED_ICE);
    }

    private void placeTemporary(ServerWorld world, BlockPos pos, BlockState placed, int ticks) {
        TemporaryBlockKey key = new TemporaryBlockKey(world.getRegistryKey(), pos);
        TemporaryBlock active = temporaryBlocks.get(key);
        if (active != null) {
            active.ticks = Math.max(active.ticks, ticks);
            if (world.getBlockState(pos).isOf(active.placed.getBlock())) {
                world.setBlockState(pos, placed, 3);
                active.placed = placed;
            }
            return;
        }
        BlockState original = world.getBlockState(pos);
        temporaryBlocks.put(key, new TemporaryBlock(world, pos, original, placed, ticks));
        world.setBlockState(pos, placed, 3);
    }

    private void tickTemporaryBlocks() {
        Iterator<Map.Entry<TemporaryBlockKey, TemporaryBlock>> iterator = temporaryBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            TemporaryBlock block = iterator.next().getValue();
            if (--block.ticks > 0) continue;
            // Do not overwrite a block another system/player changed after we placed the ice.
            if (block.world.getBlockState(block.pos).isOf(block.placed.getBlock())) {
                block.world.setBlockState(block.pos, block.original, 3);
            }
            iterator.remove();
        }
    }

    @Override public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, float amount) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) return amount;
        return isIce(player.getEntityWorld().getBlockState(player.getBlockPos().down())) ? amount * 1.2f : amount;
    }

    @Override public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
        // Preserve Ice Emperor's existing 20% bonus while standing on ice, then apply only
        // the selected form's modifier to direct melee attacks (not projectiles/reflected damage).
        amount = modifyOutgoingDamage(player, target, amount);
        if (source == null || !source.isOf(DamageTypes.PLAYER_ATTACK)
                || source.getAttacker() != player || !VanillaMeleeTracker.isActive(player)
                || player.getEntityWorld().getServer() == null
                || GraceManager.isActive(player.getEntityWorld().getServer())
                || !YPConfig.getInstance().ice_emperor_forms_enabled
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) return amount;
        YPConfig config = YPConfig.getInstance();
        return switch (selectedForm(player)) {
            case ARMOURED -> amount * (float) config.ice_emperor_armoured_outgoing_damage_multiplier;
            case ASSASSIN -> amount * (float) config.ice_emperor_assassin_melee_damage_multiplier;
            case STEALTH -> amount;
        };
    }

    @Override public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) return amount;
        if (source.isOf(DamageTypes.FREEZE)) return 0.0f;
        if (source.isIn(DamageTypeTags.IS_FIRE) || source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE)) return amount * 1.3f;
        return amount;
    }

    /** H: call down an enormous ice meteor; impact radius and cooldown are configurable. */
    @Override public void onSecondaryActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
            return;
        }
        UUID uuid = player.getUuid();
        int cooldown = meteorCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            player.sendMessage(Text.literal("Ice Meteor on cooldown: " + ((cooldown + 19) / 20) + "s"), true);
            return;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos target = player.getBlockPos().offset(player.getHorizontalFacing(), 18);
        // Aim forward, but never spawn the meteor below the target's surface.
        double x = target.getX() + 0.5;
        double z = target.getZ() + 0.5;
        int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, target.getX(), target.getZ());
        double impactY = Math.max(world.getBottomY() + 2, surfaceY + 1.0);
        IceMeteor meteor = new IceMeteor(world, uuid, x, Math.max(impactY + 36, player.getY() + 36), z, impactY);

        // A dense sphere of real snowball entities forms the meteor's core. They are
        // controlled as one cluster so the impact is synchronized rather than scattered.
        for (int i = 0; i < 64; i++) {
            double yy = 1.0 - 2.0 * (i + 0.5) / 64.0;
            double ring = Math.sqrt(Math.max(0, 1.0 - yy * yy));
            double angle = i * Math.PI * (3.0 - Math.sqrt(5.0));
            Entity core = EntityType.SNOWBALL.create(world, SpawnReason.TRIGGERED);
            if (core == null) continue;
            core.setNoGravity(true);
            core.setPosition(x + Math.cos(angle) * ring * 3.0, meteor.y + yy * 3.0, z + Math.sin(angle) * ring * 3.0);
            if (world.spawnEntity(core)) meteor.coreEntities.add(core);
        }
        activeMeteors.put(uuid, meteor);
        meteorCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().ice_emperor_meteor_cooldown_ticks));
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.2f, 0.55f);
        player.sendMessage(Text.literal("ICE METEOR INCOMING!").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    private void tickIceMeteors(MinecraftServer server) {
        Iterator<Map.Entry<UUID, IceMeteor>> meteorIterator = activeMeteors.entrySet().iterator();
        while (meteorIterator.hasNext()) {
            IceMeteor meteor = meteorIterator.next().getValue();
            meteor.y -= 1.35;
            for (Entity entity : meteor.coreEntities) {
                if (entity.isRemoved()) continue;
                double dx = entity.getX() - meteor.x;
                double dy = entity.getY() - (meteor.y + (entity.getY() - meteor.y));
                double dz = entity.getZ() - meteor.z;
                entity.setPosition(meteor.x + dx, meteor.y + (entity.getY() - (meteor.y + 1.35)), meteor.z + dz);
            }
            // Massive spherical particle shell surrounding the entity core.
            for (int i = 0; i < 100; i++) {
                double yy = 1.0 - 2.0 * (i + 0.5) / 100.0;
                double ring = Math.sqrt(Math.max(0, 1.0 - yy * yy));
                double angle = i * Math.PI * (3.0 - Math.sqrt(5.0)) + server.getTicks() * 0.045;
                meteor.world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        meteor.x + Math.cos(angle) * ring * 4.1, meteor.y + yy * 4.1,
                        meteor.z + Math.sin(angle) * ring * 4.1, 1, 0.04, 0.04, 0.04, 0.01);
            }
            meteor.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                    meteor.x, meteor.y, meteor.z, 35, 1.2, 1.2, 1.2, 0.08);
            if (meteor.y <= meteor.impactY) {
                for (Entity entity : meteor.coreEntities) if (!entity.isRemoved()) entity.discard();
                impactIceMeteor(meteor);
                meteorIterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, Snowstorm>> stormIterator = activeSnowstorms.entrySet().iterator();
        while (stormIterator.hasNext()) {
            Snowstorm storm = stormIterator.next().getValue();
            if (--storm.ticks <= 0) { stormIterator.remove(); continue; }
            for (int i = 0; i < 30; i++) {
                double angle = Math.random() * Math.PI * 2;
                double radius = Math.random() * 12.0;
                storm.world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        storm.x + Math.cos(angle) * radius, storm.y + Math.random() * 8.0,
                        storm.z + Math.sin(angle) * radius, 1, 0.12, 0.08, 0.12, 0.02);
            }
        }
    }

    private void impactIceMeteor(IceMeteor meteor) {
        YPConfig config = YPConfig.getInstance();
        double radius = Math.max(1.0, Math.min(128.0, config.ice_emperor_meteor_radius));
        int blockRadius = (int) Math.ceil(radius);
        BlockPos center = BlockPos.ofFloored(meteor.x, meteor.impactY, meteor.z);
        // Cover the circular impact zone with temporary packed ice, without replacing
        // containers, bedrock, or blocks that are not safe to overwrite.
        for (int dx = -blockRadius; dx <= blockRadius; dx++) {
            for (int dz = -blockRadius; dz <= blockRadius; dz++) {
                if ((double) dx * dx + (double) dz * dz > radius * radius) continue;
                int topY = meteor.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, center.getX() + dx, center.getZ() + dz);
                BlockPos ground = new BlockPos(center.getX() + dx, topY - 1, center.getZ() + dz);
                BlockPos cover = ground.up();
                BlockState state = meteor.world.getBlockState(cover);
                if (state.isReplaceable() && !state.hasBlockEntity() && meteor.world.getBlockState(ground).isOpaque()) {
                    placeTemporary(meteor.world, cover, Blocks.PACKED_ICE.getDefaultState(), Math.max(20, config.ice_emperor_meteor_ice_duration_ticks));
                }
            }
        }
        ServerPlayerEntity caster = meteor.world.getServer().getPlayerManager().getPlayer(meteor.caster);
        AllianceManager alliances = AllianceManager.get(meteor.world.getServer());
        Box affected = new Box(meteor.x - radius, meteor.impactY - 8, meteor.z - radius,
                meteor.x + radius, meteor.impactY + 12, meteor.z + radius);
        int effectTicks = Math.max(20, config.ice_emperor_meteor_effect_duration_ticks);
        for (LivingEntity target : meteor.world.getEntitiesByClass(LivingEntity.class, affected,
                e -> e.isAlive() && e.squaredDistanceTo(meteor.x, meteor.impactY, meteor.z) <= radius * radius
                        && e != caster && (caster == null || !alliances.isAlly(caster, e)))) {
            target.damage(meteor.world, meteor.world.getDamageSources().freeze(), (float) Math.max(0, config.ice_emperor_meteor_damage));
            target.setFrozenTicks(Math.max(target.getFrozenTicks(), effectTicks));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, effectTicks, 3, false, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, effectTicks, 0, false, true, true));
        }
        if (caster != null) {
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, effectTicks, 1, false, true, true));
        }
        activeSnowstorms.put(meteor.caster, new Snowstorm(meteor.world, meteor.caster, meteor.x, meteor.impactY, meteor.z, effectTicks));
        meteor.world.spawnParticles(ParticleTypes.EXPLOSION, meteor.x, meteor.impactY + 1, meteor.z, 8, 2.5, 1.0, 2.5, 0.05);
        EpicVfx.impact(meteor.world, meteor.x, meteor.impactY + 1, meteor.z, 1.7);
        meteor.world.spawnParticles(ParticleTypes.SNOWFLAKE, meteor.x, meteor.impactY + 1, meteor.z, 500, radius / 3.0, 1.8, radius / 3.0, 0.12);
        meteor.world.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
    }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
            return;
        }
        UUID uuid = player.getUuid();
        int cooldown = absoluteZeroCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            player.sendMessage(net.minecraft.text.Text.literal("Absolute Zero on cooldown: " + cooldown / 20 + "s"), true);
            return;
        }
        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        AllianceManager alliances = AllianceManager.get(world.getServer());
        Box box = player.getBoundingBox().expand(10.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.squaredDistanceTo(player) <= 100.0 && !alliances.isAlly(player, e))) {
            target.damage(world, world.getDamageSources().magic(), 4.0f);
            if (target.isRemoved() || !target.isAlive()) continue;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3, false, false, true));
            freezeEntity(target, Math.max(1, YPConfig.getInstance().ice_emperor_freeze_duration_ticks), world.getServer());
            BlockPos center = target.getBlockPos();
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        boolean shell = x == -1 || x == 1 || z == -1 || z == 1 || y == 0 || y == 2;
                        if (!shell) continue;
                        BlockPos pos = center.add(x, y, z);
                        if (world.getBlockState(pos).isReplaceable() && !world.getBlockState(pos).hasBlockEntity()) {
                            placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), YPConfig.getInstance().ice_emperor_freeze_duration_ticks);
                        }
                    }
                }
            }
            world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
        for (int angle = 0; angle < 360; angle += 10) {
            double radians = Math.toRadians(angle);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX() + Math.cos(radians) * 10, player.getY() + 0.5, player.getZ() + Math.sin(radians) * 10, 1, 0, 0, 0, 0.1);
        }
        absoluteZeroCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().ice_emperor_cooldown_ticks));
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        activeHeatBlasts.remove(player.getUuid());
        clearFormEffects(player);
        formLastDimension.remove(player.getUuid());
        if (player.getEntityWorld().getServer() != null) {
            PowerManager.get(player.getEntityWorld().getServer()).clearIceEmperorForm(player.getUuid());
            YPNetworking.syncIceEmperorForm(player.getEntityWorld().getServer(), player.getUuid(), "ARMOURED", false);
        }
        formActiveSyncState.remove(player.getUuid());
        absoluteZeroCooldowns.remove(player.getUuid());
        meteorCooldowns.remove(player.getUuid());
        guardSummonCooldowns.remove(player.getUuid());
        java.util.Set<UUID> guards = guardBearIds.remove(player.getUuid());
        MinecraftServer server = player.getEntityWorld().getServer();
        if (guards != null && server != null) {
            // Search all dimensions so a guard is still cleaned up if its owner changed dimensions.
            for (ServerWorld guardWorld : server.getWorlds()) {
                for (UUID guardId : guards) {
                    Entity guard = guardWorld.getEntity(guardId);
                    if (guard instanceof PolarBearEntity && !guard.isRemoved()) guard.discard();
                }
            }
        }
        guardRecoveryTicks.remove(player.getUuid());
        IceMeteor meteor = activeMeteors.remove(player.getUuid());
        if (meteor != null) for (Entity entity : meteor.coreEntities) if (!entity.isRemoved()) entity.discard();
        activeSnowstorms.remove(player.getUuid());
        playerAuraTicks.remove(player.getUuid());
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.WEAKNESS);
    }
}
