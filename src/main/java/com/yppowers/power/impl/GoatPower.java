package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import com.yppowers.power.PowerManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GoatPower extends Power {
    private static final Identifier HEALTH_ID = Identifier.of("yppowers", "goat_health");
    private static final Identifier KB_ID = Identifier.of("yppowers", "goat_knockback_resistance");
    private static final Identifier SPEED_ID = Identifier.of("yppowers", "goat_speed");
    private final Map<UUID, Integer> sprintTicks = new HashMap<>();

    @Override public PowerType getType() { return PowerType.GOAT; }

    @Override public void onAssign(ServerPlayerEntity player) { applyModifiers(player); }
    @Override public void onPlayerJoin(ServerPlayerEntity player) { applyModifiers(player); }
    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) { applyModifiers(player); }
    @Override public void onRemove(ServerPlayerEntity player) {
        removeModifiers(player);
        sprintTicks.remove(player.getUuid());
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

    private void updateSpeed(ServerPlayerEntity player, int ticksSprinting) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(SPEED_ID);
        double maxSpeed = Math.max(1.5, YPConfig.getInstance().goat_max_speed);
        double progress = Math.min(1.0, Math.max(0, ticksSprinting) / 100.0);
        // MULTIPLY_TOTAL yields total speed of 1.5x from rest and ramps to the configured cap.
        double multiplier = 0.5 + (maxSpeed - 1.5) * progress;
        speed.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, multiplier, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    @Override public void onJump(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) return;
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
        int ticks = player.isSprinting() ? sprintTicks.getOrDefault(uuid, 0) + 1 : 0;
        sprintTicks.put(uuid, ticks);
        updateSpeed(player, ticks);
    }

    @Override public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) return damageMultiplier;
        return damageMultiplier * 0.4f; // 60% reduction while keeping vanilla fall-distance calculation.
    }

    @Override public float modifyOutgoingDamage(ServerPlayerEntity player, net.minecraft.entity.Entity target, DamageSource source, float amount) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT)) return amount;
        return source.isOf(DamageTypes.PLAYER_ATTACK) ? amount * 0.9f : amount;
    }
}
