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
    private final Map<UUID, Integer> sprintTicks = new HashMap<>();
    private final Map<UUID, SavedOverdriveEffects> preOverdriveEffects = new HashMap<>();
    private final Map<UUID, Integer> overdriveElapsedTicks = new HashMap<>();

    private record SavedOverdriveEffects(StatusEffectInstance strength, StatusEffectInstance resistance,
                                         StatusEffectInstance regeneration) {}
    /** Players protected from fall damage until they land from a powered GOAT jump. */
    private final Set<UUID> jumpFallProtection = new HashSet<>();
    /** Tracks whether each protected jump has actually left the ground. */
    private final Set<UUID> airborneAfterGoatJump = new HashSet<>();

    @Override public PowerType getType() { return PowerType.GOAT; }

    @Override public void onAssign(ServerPlayerEntity player) { applyModifiers(player); }
    @Override public void onPlayerJoin(ServerPlayerEntity player) { applyModifiers(player); }
    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(oldPlayer);
        jumpFallProtection.remove(player.getUuid());
        airborneAfterGoatJump.remove(player.getUuid());
        sprintTicks.remove(player.getUuid());
        applyModifiers(player);
    }
    @Override public void onPlayerDeath(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() != null) EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(player);
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() != null) EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(player);
        removeOverdrive(player);
        removeModifiers(player);
        sprintTicks.remove(player.getUuid());
        jumpFallProtection.remove(player.getUuid());
        airborneAfterGoatJump.remove(player.getUuid());
    }

    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        boolean enabled = PowerManager.get(player.getEntityWorld().getServer()).togglePowerEnabled(player, PowerType.GOAT);
        if (enabled) {
            applyModifiers(player);
            player.sendMessage(Text.literal("The GOAT's passives are enabled.").formatted(Formatting.GREEN), true);
        } else {
            removeModifiers(player);
            sprintTicks.remove(player.getUuid());
            jumpFallProtection.remove(player.getUuid());
            airborneAfterGoatJump.remove(player.getUuid());
            player.sendMessage(Text.literal("The GOAT's passives are disabled. Press R to re-enable them.").formatted(Formatting.YELLOW), true);
        }
    }

    private void applyModifiers(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() != null
                && !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) {
            removeModifiers(player);
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
        if (!player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, StatusEffectInstance.INFINITE, 1, false, false, false));
        }
        updateSpeed(player, sprintTicks.getOrDefault(player.getUuid(), 0));
        // Do not refill health on every join; clamp only if the new max health is lower.
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private void removeModifiers(ServerPlayerEntity player) {
        EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (health != null) health.removeModifier(HEALTH_ID);
        EntityAttributeInstance kb = player.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.removeModifier(KB_ID);
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
        player.removeStatusEffect(StatusEffects.JUMP_BOOST);
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
        double maxSpeed = Math.max(1.5, YPConfig.getInstance().goat_max_speed);
        double progress = Math.min(1.0, Math.max(0, ticksSprinting) / 100.0);
        // MULTIPLY_TOTAL yields total speed of 1.5x from rest and ramps to the configured cap.
        double multiplier = 0.5 + (maxSpeed - 1.5) * progress;
        speed.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, multiplier, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (player.getEntityWorld().getServer() != null
                && EnergyManager.get(player.getEntityWorld().getServer()).isOverdriveActive(player.getUuid())) {
            ensureMultiplier(player, EntityAttributes.MOVEMENT_SPEED, OVERDRIVE_SPEED_ID,
                    YPConfig.getInstance().goat_overdrive_movement_multiplier - 1.0 - multiplier);
        }
    }

    @Override public void onJump(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) return;
        // A GOAT-powered jump grants complete fall safety until the player lands. Tracking
        // the actual airborne-to-ground transition prevents the protection from lingering
        // and accidentally covering a later, unrelated fall.
        jumpFallProtection.add(player.getUuid());
        airborneAfterGoatJump.remove(player.getUuid());
        Vec3d velocity = player.getVelocity();
        double multiplier = Math.max(1.0, YPConfig.getInstance().goat_jump_multiplier);
        // This is a true passive: apply on every server-side jump, including vertical/stationary jumps.
        // There is deliberately no per-jump cooldown or movement check.
        player.setVelocity(velocity.x, Math.max(0.0, velocity.y) * multiplier, velocity.z);
        ((com.yppowers.mixin.EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) return;
        // Reapply Jump Boost if another effect (for example milk) removed the passive.
        if (!player.hasStatusEffect(StatusEffects.JUMP_BOOST)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, StatusEffectInstance.INFINITE, 1, false, false, false));
        }
        if (jumpFallProtection.contains(uuid)) {
            if (!player.isOnGround() || player.getVelocity().y > 0.0) {
                airborneAfterGoatJump.add(uuid);
            } else if (airborneAfterGoatJump.remove(uuid)) {
                // The damaging landing has already been handled in the entity tick; clear
                // the one-jump protection before any subsequent ordinary fall.
                jumpFallProtection.remove(uuid);
            }
        }
        int ticks = player.isSprinting() ? sprintTicks.getOrDefault(uuid, 0) + 1 : 0;
        sprintTicks.put(uuid, ticks);
        updateSpeed(player, ticks);
    }

    @Override public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) return damageMultiplier;
        if (jumpFallProtection.contains(player.getUuid())) {
            return damageMultiplier * 0.1f; // 90% reduction from a powered jump until the landing is complete.
        }
        return damageMultiplier * 0.4f; // 60% passive reduction for other falls.
    }

    @Override public float modifyOutgoingDamage(ServerPlayerEntity player, net.minecraft.entity.Entity target, DamageSource source, float amount) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) return amount;
        return source.isOf(DamageTypes.PLAYER_ATTACK) ? amount * 0.9f : amount;
    }
}
