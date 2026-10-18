package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class StickOfDeletionPower extends Power {
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    private final Map<UUID, Map<UUID, Integer>> knockedEntities = new HashMap<>();

    @Override
    public PowerType getType() {
        return PowerType.DELETION;
    }

    @Override
    public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (attacker.getMainHandStack().isOf(Items.STICK)) {
            if (target instanceof ServerPlayerEntity targetPlayer && AllianceManager.get(attacker.getEntityWorld().getServer()).isAlly(attacker, targetPlayer)) return;
            if (!(target instanceof LivingEntity) || attacker.getEntityWorld().getServer() == null) return;
            UUID aId = attacker.getUuid();
            if (cooldowns.getOrDefault(aId, 0) <= 0) {
                Vec3d dir = target.getEntityPos().subtract(attacker.getEntityPos()).normalize();
                if (dir.lengthSquared() < 1.0E-6) dir = attacker.getRotationVec(1.0F).normalize();
                Vec3d launch = new Vec3d(dir.x * 12.0, Math.max(1.5, dir.y * 12.0), dir.z * 12.0);
                target.setVelocity(launch);
                ((com.yppowers.mixin.EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
                
                target.getEntityWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                ((ServerWorld) target.getEntityWorld()).spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 1, target.getZ(), 5, 0.5, 0.5, 0.5, 0);

                knockedEntities.computeIfAbsent(aId, k -> new HashMap<>()).put(target.getUuid(), 0);
                int cooldownTicks = Math.max(0, YPConfig.getInstance().stick_of_deletion_cooldown_ticks);
                if (cooldownTicks > 0) {
                    cooldowns.put(aId, cooldownTicks);
                    attacker.getItemCooldownManager().set(Items.STICK.getDefaultStack(), cooldownTicks);
                } else {
                    cooldowns.remove(aId);
                }
            }
        }
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        UUID pId = player.getUuid();
        if (cooldowns.getOrDefault(pId, 0) > 0) {
            cooldowns.put(pId, cooldowns.get(pId) - 1);
        }

        Map<UUID, Integer> knocked = knockedEntities.get(pId);
        if (knocked != null) {
            ServerWorld world = (ServerWorld) player.getEntityWorld();
            Iterator<Map.Entry<UUID, Integer>> it = knocked.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Integer> entry = it.next();
                Entity target = world.getEntity(entry.getKey());
                if (target == null || target.isRemoved()) {
                    it.remove();
                    continue;
                }

                if (target.horizontalCollision) {
                    target.damage(world, world.getDamageSources().magic(), 8.0f);
                    world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, target.getX(), target.getY() + 1, target.getZ(), 1, 0, 0, 0, 0);
                    world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.0f, 1.0f);
                    it.remove();
                    continue;
                }

                int ticks = entry.getValue() + 1;
                if (ticks > 60) {
                    it.remove();
                } else {
                    entry.setValue(ticks);
                }
            }
        }
    }
}
