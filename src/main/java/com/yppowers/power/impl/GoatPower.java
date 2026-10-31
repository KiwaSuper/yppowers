package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import com.yppowers.power.PowerManager;
import com.yppowers.energy.EnergyManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GoatPower extends Power {
    private static final Identifier HEALTH_ID = Identifier.of("yppowers", "goat_health");
    private static final Identifier KB_ID = Identifier.of("yppowers", "goat_knockback_resistance");
    private static final Identifier SPEED_ID = Identifier.of("yppowers", "goat_speed");
    private static final Identifier OVERDRIVE_SPEED_ID = Identifier.of("yppowers", "goat_overdrive_speed");
    private static final Identifier OVERDRIVE_JUMP_ID = Identifier.of("yppowers", "goat_overdrive_jump");
    private static final Identifier OVERDRIVE_KB_ID = Identifier.of("yppowers", "goat_overdrive_knockback");
    private static final DustParticleEffect MAX_SPEED_DUST = new DustParticleEffect(0xF1C40F, 1.0f);
    private final Map<UUID, Integer> sprintTicks = new HashMap<>();
    private final Map<UUID, Integer> sprintStopTicks = new HashMap<>();
    private final Map<UUID, Identifier> lastDimensions = new HashMap<>();
    private final Map<UUID, JumpBoostLease> jumpBoostLeases = new HashMap<>();
    private final Map<UUID, SavedOverdriveEffects> preOverdriveEffects = new HashMap<>();
    private final Map<UUID, Integer> overdriveElapsedTicks = new HashMap<>();

    private record SavedOverdriveEffects(StatusEffectInstance strength, StatusEffectInstance resistance,
                                         StatusEffectInstance regeneration) {}
    private static final class JumpBoostLease {
        final StatusEffectInstance previous;
        final int amplifier;
        int elapsedTicks;
        JumpBoostLease(StatusEffectInstance previous, int amplifier) {
            this.previous = previous == null ? null : new StatusEffectInstance(previous);
            this.amplifier = amplifier;
        }
    }
    /** Players protected from fall damage until they land from a powered GOAT jump. */
    private final Set<UUID> jumpFallProtection = new HashSet<>();
    /** Tracks whether each protected jump has actually left the ground. */
    private final Set<UUID> airborneAfterGoatJump = new HashSet<>();

    @Override public PowerType getType() { return PowerType.GOAT; }

    @Override public void onAssign(ServerPlayerEntity player) {
        resetMovementState(player.getUuid());
        applyModifiers(player);
    }
    @Override public void onPlayerJoin(ServerPlayerEntity player) {
        resetMovementState(player.getUuid());
        applyModifiers(player);
    }
    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        if (player.getEntityWorld().getServer() != null) EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(oldPlayer);
        clearFallTracking(player.getUuid());
        resetMovementState(player.getUuid());
        releaseJumpBoost(player);
        removeSprintModifier(player);
        applyModifiers(player);
    }
    @Override public void onPlayerDeath(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() != null) EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(player);
        clearFallTracking(player.getUuid());
        resetMovementState(player.getUuid());
        releaseJumpBoost(player);
        removeSprintModifier(player);
    }
    @Override public void onPlayerDisconnect(ServerPlayerEntity player) {
        clearFallTracking(player.getUuid());
        resetMovementState(player.getUuid());
        releaseJumpBoost(player);
        removeSprintModifier(player);
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() != null) EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(player);
        removeOverdrive(player);
        removeModifiers(player);
        resetMovementState(player.getUuid());
        clearFallTracking(player.getUuid());
    }

    private void resetMovementState(UUID id) {
        sprintTicks.remove(id);
        sprintStopTicks.remove(id);
        lastDimensions.remove(id);
    }

    private void clearFallTracking(UUID id) {
        jumpFallProtection.remove(id);
        airborneAfterGoatJump.remove(id);
    }

    /** R toggles the passive enhanced-human kit; Overdrive remains a separate active ability. */
    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
        boolean enabled = manager.togglePowerEnabled(player, PowerType.GOAT);
        clearFallTracking(player.getUuid());
        resetMovementState(player.getUuid());
        if (enabled) {
            applyModifiers(player);
            player.sendMessage(Text.literal("GOAT passives enabled.").formatted(Formatting.GREEN), true);
        } else {
            removePassiveModifiers(player);
            player.sendMessage(Text.literal("GOAT passives disabled. Press R to re-enable them.")
                    .formatted(Formatting.YELLOW), true);
        }
    }

    private boolean arePassivesEnabled(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return false;
        return PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT);
    }

    private void applyModifiers(ServerPlayerEntity player) {
        if (!arePassivesEnabled(player)) {
            removePassiveModifiers(player);
            return;
        }
        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (health != null && health.getModifier(HEALTH_ID) == null) {
            health.addPersistentModifier(new EntityAttributeModifier(HEALTH_ID, 10.0, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        EntityAttributeInstance kb = player.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
        if (kb != null && kb.getModifier(KB_ID) == null) {
            kb.addPersistentModifier(new EntityAttributeModifier(KB_ID, 0.20, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        ensureJumpBoost(player);
        updateSpeed(player, player.isSprinting() ? sprintTicks.getOrDefault(player.getUuid(), 1) : 0);
        // Do not refill health on every join; clamp only if the new max health is lower.
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private void removeModifiers(ServerPlayerEntity player) {
        removePassiveModifiers(player);
    }

    /** Removes only the passive kit; active Overdrive modifiers are deliberately preserved. */
    private void removePassiveModifiers(ServerPlayerEntity player) {
        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (health != null) health.removeModifier(HEALTH_ID);
        EntityAttributeInstance kb = player.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(KB_ID);
        // Also recalculate the separate Overdrive speed offset so toggling passives off
        // during Overdrive cannot leave the old sprint-offset modifier applied.
        updateSpeed(player, 0);
        releaseJumpBoost(player);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    @Override public void onTertiaryActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        EnergyManager energy = EnergyManager.get(player.getEntityWorld().getServer());
        if (energy.getOverdriveTicks(player.getUuid()) > 0) {
            player.sendMessage(Text.literal("GOAT Overdrive is already active.").formatted(Formatting.YELLOW), true);
            return;
        }
        if (energy.getGoatCooldownTicks(player.getUuid()) > 0) {
            player.sendMessage(Text.literal("GOAT Overdrive cooldown: "
                    + ((energy.getGoatCooldownTicks(player.getUuid()) + 19) / 20) + "s.").formatted(Formatting.YELLOW), true);
            return;
        }
        energy.startGoatOverdrive(player);
        player.sendMessage(Text.literal("GOAT OVERDRIVE! Move fast; deferred damage will return afterward.").formatted(Formatting.GOLD), true);
        if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, player.getX(), player.getY() + 1.0, player.getZ(), 36, 0.9, 0.8, 0.9, 0.08);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.8, 0.8, 0.8, 0.08);
            EpicVfx.activation(world, player.getX(), player.getY(), player.getZ(), 1.25);
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_HORN_BREAK, SoundCategory.PLAYERS, 1.2f, 0.75f);
        }
    }

    public void applyOverdrive(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        preOverdriveEffects.computeIfAbsent(id, ignored -> new SavedOverdriveEffects(
                copyEffect(player.getStatusEffect(StatusEffects.STRENGTH)),
                copyEffect(player.getStatusEffect(StatusEffects.RESISTANCE)),
                copyEffect(player.getStatusEffect(StatusEffects.REGENERATION))));
        overdriveElapsedTicks.putIfAbsent(id, 0);
        YPConfig config = YPConfig.getInstance();
        // ADD_MULTIPLIED_TOTAL modifiers add together. Offset the GOAT's own speed modifier
        // so the configured Overdrive movement multiplier is the target total, not an accidental
        // 3x stacked on top of the existing GOAT sprint multiplier.
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        EntityAttributeModifier goatSpeed = speed == null ? null : speed.getModifier(SPEED_ID);
        double goatSpeedAdd = goatSpeed == null ? 0.0 : goatSpeed.value();
        ensureMultiplier(player, EntityAttributes.MOVEMENT_SPEED, OVERDRIVE_SPEED_ID,
                config.goat_overdrive_movement_multiplier - 1.0 - goatSpeedAdd);
        ensureMultiplier(player, EntityAttributes.JUMP_STRENGTH, OVERDRIVE_JUMP_ID,
                config.goat_overdrive_jump_multiplier - 1.0);
        ensureAdd(player, EntityAttributes.KNOCKBACK_RESISTANCE, OVERDRIVE_KB_ID, config.goat_overdrive_knockback_resistance_bonus);
        refreshOverdriveEffects(player);
    }

    private static StatusEffectInstance copyEffect(StatusEffectInstance effect) {
        return effect == null ? null : new StatusEffectInstance(effect);
    }

    public void tickOverdrive(ServerPlayerEntity player) {
        applyOverdrive(player);
        overdriveElapsedTicks.merge(player.getUuid(), 1, Integer::sum);
        if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world && world.getTime() % 4 == 0) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.8, player.getZ(), 3, 0.35, 0.5, 0.35, 0.04);
            world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 1, 0.25, 0.05, 0.25, 0.01);
        }
    }

    private void ensureMultiplier(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                                  Identifier id, double amount) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) return;
        EntityAttributeModifier modifier = instance.getModifier(id);
        if (modifier == null || Math.abs(modifier.value() - amount) > 1.0e-6) {
            if (modifier != null) instance.removeModifier(id);
            instance.addTemporaryModifier(new EntityAttributeModifier(id, amount, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void ensureAdd(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute,
                           Identifier id, double amount) {
        EntityAttributeInstance instance = player.getAttributeInstance(attribute);
        if (instance == null) return;
        EntityAttributeModifier modifier = instance.getModifier(id);
        if (modifier == null || Math.abs(modifier.value() - amount) > 1.0e-6) {
            if (modifier != null) instance.removeModifier(id);
            instance.addTemporaryModifier(new EntityAttributeModifier(id, amount, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    private void refreshOverdriveEffects(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 5, 2, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 5, 1, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 5, 1, false, true, true));
    }

    public void removeOverdrive(ServerPlayerEntity player) {
        for (var entry : new Object[][]{
                {EntityAttributes.MOVEMENT_SPEED, OVERDRIVE_SPEED_ID},
                {EntityAttributes.JUMP_STRENGTH, OVERDRIVE_JUMP_ID},
                {EntityAttributes.KNOCKBACK_RESISTANCE, OVERDRIVE_KB_ID}}) {
            @SuppressWarnings("unchecked") net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attribute =
                    (net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute>) entry[0];
            Identifier id = (Identifier) entry[1];
            EntityAttributeInstance instance = player.getAttributeInstance(attribute);
            if (instance != null) instance.removeModifier(id);
        }
        // Restore pre-existing versions of these effects, accounting for time elapsed while
        // Overdrive was active. This avoids deleting unrelated buffs or extending them forever.
        SavedOverdriveEffects saved = preOverdriveEffects.remove(player.getUuid());
        int elapsed = overdriveElapsedTicks.getOrDefault(player.getUuid(), 0);
        overdriveElapsedTicks.remove(player.getUuid());
        if (saved != null) {
            restoreEffect(player, StatusEffects.STRENGTH, saved.strength(), elapsed);
            restoreEffect(player, StatusEffects.RESISTANCE, saved.resistance(), elapsed);
            restoreEffect(player, StatusEffects.REGENERATION, saved.regeneration(), elapsed);
        } else {
            // No snapshot means there is no evidence that Overdrive owned the current effects.
            // Leave them alone rather than accidentally deleting a potion applied by another source.
        }
    }

    private static void restoreEffect(ServerPlayerEntity player,
                                      net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effectType,
                                      StatusEffectInstance saved, int elapsedTicks) {
        if (saved == null) return;
        player.removeStatusEffect(effectType);
        int originalDuration = saved.getDuration();
        int remaining = originalDuration < 0 ? originalDuration : originalDuration - Math.max(0, elapsedTicks);
        if (remaining == 0 || remaining < -1) return;
        player.addStatusEffect(new StatusEffectInstance(saved.getEffectType(), remaining, saved.getAmplifier(),
                saved.isAmbient(), saved.shouldShowParticles(), saved.shouldShowIcon()));
    }

    @Override public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        // Deferred damage is captured by LivingEntityMixin at applyDamage, after vanilla
        // damage mitigation. Keep this hook passive to prevent recording the same hit twice.
        return amount;
    }

    private void updateSpeed(ServerPlayerEntity player, int ticksSprinting) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_ID);
        YPConfig config = YPConfig.getInstance();
        double goatSpeedModifier = 0.0;
        if (config.goat_sprint_enabled && player.isSprinting() && ticksSprinting > 0) {
            double start = Math.min(config.goat_sprint_start_multiplier, config.goat_max_speed);
            double max = Math.max(start, config.goat_max_speed);
            int accelerationTicks = Math.max(1, config.goat_sprint_acceleration_ticks_to_max);
            double progress = accelerationTicks <= 1 ? 1.0
                    : Math.min(1.0, Math.max(0, ticksSprinting - 1) / (double) (accelerationTicks - 1));
            double effectiveMultiplier = start + (max - start) * progress;
            goatSpeedModifier = effectiveMultiplier - 1.0;
            speed.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, goatSpeedModifier,
                    EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        boolean overdrive = player.getEntityWorld().getServer() != null
                && EnergyManager.get(player.getEntityWorld().getServer()).isOverdriveActive(player.getUuid());
        if (overdrive) {
            // Overdrive defines a total target multiplier; offset this sprint modifier rather than stack on it.
            ensureMultiplier(player, EntityAttributes.MOVEMENT_SPEED, OVERDRIVE_SPEED_ID,
                    config.goat_overdrive_movement_multiplier - 1.0 - goatSpeedModifier);
        } else {
            EntityAttributeInstance movement = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            if (movement != null) movement.removeModifier(OVERDRIVE_SPEED_ID);
        }
    }

    private void ensureJumpBoost(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int amplifier = YPConfig.getInstance().goat_jump_boost_amplifier;
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.JUMP_BOOST);

        // The previous GOAT implementation applied infinite Jump Boost II itself. Upgrade that
        // exact old signature so it cannot keep the excessive jump after the new config is loaded.
        if (jumpBoostLeases.get(id) == null && current != null && current.getAmplifier() == 1
                && current.getDuration() == StatusEffectInstance.INFINITE && amplifier < 1) {
            player.removeStatusEffect(StatusEffects.JUMP_BOOST);
            current = null;
        }

        JumpBoostLease lease = jumpBoostLeases.get(id);
        if (lease == null) {
            // Do not overwrite a stronger independent potion/power effect. If a weak or absent
            // effect needs a GOAT boost, keep its original duration so it can be restored later.
            if (current != null && current.getAmplifier() >= amplifier) return;
            lease = new JumpBoostLease(current, amplifier);
            jumpBoostLeases.put(id, lease);
        }
        StatusEffectInstance latest = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        if (latest == null || latest.getAmplifier() < lease.amplifier) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST,
                    StatusEffectInstance.INFINITE, lease.amplifier, false, false, false));
        }
        lease.elapsedTicks++;
    }

    private void releaseJumpBoost(ServerPlayerEntity player) {
        JumpBoostLease lease = jumpBoostLeases.remove(player.getUuid());
        if (lease == null) return;
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.JUMP_BOOST);
        // Remove only the exact managed amplifier. Stronger or independently renewed effects survive.
        if (current != null && (current.getAmplifier() != lease.amplifier
                || current.getDuration() != StatusEffectInstance.INFINITE)) return;
        if (current != null) player.removeStatusEffect(StatusEffects.JUMP_BOOST);
        if (lease.previous != null) {
            int duration = lease.previous.getDuration();
            if (duration >= 0) duration -= lease.elapsedTicks;
            if (duration > 0 || duration == StatusEffectInstance.INFINITE) {
                player.addStatusEffect(new StatusEffectInstance(lease.previous.getEffectType(), duration,
                        lease.previous.getAmplifier(), lease.previous.isAmbient(),
                        lease.previous.shouldShowParticles(), lease.previous.shouldShowIcon()));
            }
        }
    }

    private void removeSprintModifier(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
    }

    @Override public void onJump(ServerPlayerEntity player) {
        if (!arePassivesEnabled(player)) return;
        YPConfig config = YPConfig.getInstance();
        UUID id = player.getUuid();
        if (config.goat_jump_fall_protection_enabled) {
            jumpFallProtection.add(id);
            airborneAfterGoatJump.remove(id);
        }
        Vec3d velocity = player.getVelocity();
        double multiplier = Math.max(1.0, config.goat_jump_multiplier);
        // Vanilla jump logic has already applied the configured Jump Boost. The lower multiplier
        // keeps the leap enhanced without compounding a second 2.2x multiplier.
        player.setVelocity(velocity.x, Math.max(0.0, velocity.y) * multiplier, velocity.z);
        ((com.yppowers.mixin.EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_LONG_JUMP,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
        if (player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world) {
            EpicVfx.impact(world, player.getX(), player.getY(), player.getZ(), 0.65);
        }
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (player.getEntityWorld().getServer() == null) return;
        if (!player.isAlive()) {
            clearFallTracking(player.getUuid());
            resetMovementState(player.getUuid());
            releaseJumpBoost(player);
            removeSprintModifier(player);
            return;
        }
        if (!arePassivesEnabled(player)) return;
        Identifier dimension = player.getEntityWorld().getRegistryKey().getValue();
        Identifier previousDimension = lastDimensions.put(uuid, dimension);
        if (previousDimension != null && !previousDimension.equals(dimension)) {
            clearFallTracking(uuid);
            sprintTicks.remove(uuid);
            sprintStopTicks.remove(uuid);
            removeSprintModifier(player);
        }
        ensureJumpBoost(player);
        if (jumpFallProtection.contains(uuid)) {
            if (!player.isOnGround() || player.getVelocity().y > 0.0) {
                airborneAfterGoatJump.add(uuid);
            } else if (airborneAfterGoatJump.remove(uuid)) {
                // The landing damage check happened in the entity tick. Clear protection before any later fall.
                jumpFallProtection.remove(uuid);
            }
        }
        YPConfig config = YPConfig.getInstance();
        int ticks = sprintTicks.getOrDefault(uuid, 0);
        if (player.isSprinting() && config.goat_sprint_enabled) {
            sprintStopTicks.put(uuid, 0);
            if (player.isOnGround() || config.goat_sprint_preserve_acceleration_while_jumping) ticks++;
        } else {
            int stopped = sprintStopTicks.getOrDefault(uuid, 0) + 1;
            sprintStopTicks.put(uuid, stopped);
            if (stopped >= Math.max(1, config.goat_sprint_reset_delay_ticks)) ticks = 0;
        }
        sprintTicks.put(uuid, ticks);
        updateSpeed(player, ticks);

        if (player.isSprinting() && config.goat_sprint_enabled && ticks == 1
                && config.goat_sprint_sounds_enabled && player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_STEP, SoundCategory.PLAYERS, 0.45f, 1.1f);
        }
        if (player.isSprinting() && config.goat_sprint_enabled && config.goat_sprint_particles_enabled
                && player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world && world.getTime() % 5 == 0) {
            world.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, player.getX(), player.getY() + 0.1,
                    player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
        }
        // A low-rate yellow wake appears only after the configured sprint ramp has actually
        // reached its cap and the player is still moving horizontally.
        double startMultiplier = Math.min(config.goat_sprint_start_multiplier, config.goat_max_speed);
        double maxMultiplier = Math.max(startMultiplier, config.goat_max_speed);
        boolean capReached = maxMultiplier <= startMultiplier + 1.0e-6
                || ticks >= Math.max(1, config.goat_sprint_acceleration_ticks_to_max);
        Vec3d velocity = player.getVelocity();
        double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        double horizontalSpeed = Math.sqrt(horizontalSpeedSquared);
        double maxSpeedThreshold = Math.max(0.08,
                player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 0.30);
        if (player.isSprinting() && config.goat_sprint_enabled && capReached
                && horizontalSpeed >= maxSpeedThreshold
                && player.age % 2 == 0
                && player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld trailWorld) {
            double backX = -velocity.x / horizontalSpeed;
            double backZ = -velocity.z / horizontalSpeed;
            double trailX = player.getX() + backX * 0.85;
            double trailZ = player.getZ() + backZ * 0.85;
            trailWorld.spawnParticles(MAX_SPEED_DUST, trailX, player.getY() + 0.18, trailZ,
                    3, 0.07, 0.05, 0.07, 0.005);
            trailWorld.spawnParticles(ParticleTypes.CLOUD, trailX, player.getY() + 0.12, trailZ,
                    1, 0.04, 0.025, 0.04, 0.005);
        }
    }

    @Override public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) {
        if (!arePassivesEnabled(player)) return damageMultiplier;
        YPConfig config = YPConfig.getInstance();
        if (config.goat_jump_fall_protection_enabled && jumpFallProtection.contains(player.getUuid())) {
            return damageMultiplier * (float) config.goat_jump_fall_damage_multiplier;
        }
        if (!config.goat_passive_fall_protection_enabled) return damageMultiplier;
        return damageMultiplier * (float) config.goat_passive_fall_damage_multiplier;
    }

    @Override public float modifyOutgoingDamage(ServerPlayerEntity player, net.minecraft.entity.Entity target, DamageSource source, float amount) {
        if (!arePassivesEnabled(player)) return amount;
        return source.isOf(DamageTypes.PLAYER_ATTACK) ? amount * 0.9f : amount;
    }
}
