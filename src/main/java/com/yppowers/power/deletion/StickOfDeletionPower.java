package com.yppowers.power.deletion;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Stick of Deletion: hitting with a stick launches enemies across the map and punishes wall impacts. */
public final class StickOfDeletionPower extends Power {
    private static final String CD_LAUNCH = "deletion_launch";
    private final Map<UUID, Map<UUID, Integer>> launchedTargets = new HashMap<>();

    @Override
    public PowerId id() {
        return PowerId.DELETION;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(AbilitySpec.active(AbilitySlot.PRIMARY, "Stick Launch", CD_LAUNCH));
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        return "Hit with Stick";
    }

    @Override
    public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (!attacker.getMainHandStack().isOf(Items.STICK)) return;
        if (!(target instanceof LivingEntity) || !CombatUtil.canHarm(attacker, target)) return;
        MinecraftServer server = attacker.getEntityWorld().getServer();
        if (server == null) return;

        CooldownSystem cds = CooldownSystem.get(server);
        if (cds.isOnCooldown(attacker.getUuid(), CD_LAUNCH)) return;

        PowerConfigs.StickOfDeletion cfg = YPConfig.get().powers.stickOfDeletion;
        Vec3d dir = target.getEntityPos().subtract(attacker.getEntityPos());
        dir = dir.lengthSquared() < 1.0e-6 ? CombatUtil.safeLook(attacker) : dir.normalize();

        double strength = Math.max(1.0, cfg.launchVelocity * 2.8);
        Vec3d launch = new Vec3d(dir.x * strength, Math.max(1.5, dir.y * strength), dir.z * strength);
        target.setVelocity(launch);
        ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();

        if (target.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.playSound(world, target.getEntityPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0f, 1.0f);
            VfxSystem.burst(world, ParticleTypes.EXPLOSION, target.getEntityPos().add(0, 1.0, 0), 5, 0.5, 0.0);
            VfxSystem.beam(world, ParticleTypes.END_ROD, attacker.getEntityPos().add(0, 1.0, 0), target.getEntityPos().add(0, 1.0, 0), 16);
        }

        launchedTargets.computeIfAbsent(attacker.getUuid(), ignored -> new HashMap<>()).put(target.getUuid(), 0);
        if (cfg.cooldownTicks > 0) {
            cds.set(attacker.getUuid(), CD_LAUNCH, cfg.cooldownTicks);
            attacker.getItemCooldownManager().set(Items.STICK.getDefaultStack(), cfg.cooldownTicks);
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        Map<UUID, Integer> tracked = launchedTargets.get(player.getUuid());
        if (tracked == null || tracked.isEmpty()) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        float impactDmg = (float) YPConfig.get().powers.stickOfDeletion.wallImpactDamage;
        Iterator<Map.Entry<UUID, Integer>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            Entity target = world.getEntity(entry.getKey());
            if (target == null || target.isRemoved() || !target.isAlive()) {
                it.remove();
                continue;
            }
            if (target.horizontalCollision) {
                target.damage(world, world.getDamageSources().magic(), impactDmg);
                VfxSystem.flash(world, target.getEntityPos().add(0, 1.0, 0));
                VfxSystem.playSound(world, target.getEntityPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.0f, 1.0f);
                it.remove();
                continue;
            }
            int next = entry.getValue() + 1;
            if (next > 60) it.remove();
            else entry.setValue(next);
        }
        if (tracked.isEmpty()) launchedTargets.remove(player.getUuid());
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        launchedTargets.remove(player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        launchedTargets.remove(player.getUuid());
    }
}
