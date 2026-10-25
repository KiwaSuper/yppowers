package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Serious Punch implementation. Freeze state is advanced once per server tick. */
public class SeriousPunchPower extends Power {
    private final Map<UUID, Integer> cooldowns = new java.util.HashMap<>();
    private static final Map<Entity, FrozenEntity> FROZEN_ENTITIES = new IdentityHashMap<>();
    private static long lastFreezeTick = Long.MIN_VALUE;

    private static final class FrozenEntity {
        final Vec3d position;
        final Vec3d velocity;
        final float yaw;
        final float pitch;
        long expiresAt;

        FrozenEntity(Entity entity, long expiresAt) {
            this.position = entity.getEntityPos();
            Vec3d savedVelocity = TimeWarperPower.getFrozenVelocity(entity);
            if (savedVelocity == null) savedVelocity = IceEmperorPower.getFrozenVelocity(entity);
            this.velocity = savedVelocity == null ? entity.getVelocity() : savedVelocity;
            this.yaw = entity.getYaw();
            this.pitch = entity.getPitch();
            this.expiresAt = expiresAt;
        }
    }

    @Override public PowerType getType() { return PowerType.PUNCH; }

    public static boolean isEntityFrozen(Entity entity) {
        return entity != null && FROZEN_ENTITIES.containsKey(entity);
    }

    /** Returns the velocity captured before Serious Punch froze this entity, if applicable. */
    public static Vec3d getFrozenVelocity(Entity entity) {
        FrozenEntity frozen = entity == null ? null : FROZEN_ENTITIES.get(entity);
        return frozen == null ? null : frozen.velocity;
    }

    public static void serverTick(MinecraftServer server) {
        Power registered = PowerManager.get(server).getPowerInstance(PowerType.PUNCH);
        if (registered instanceof SeriousPunchPower punch) punch.tickFrozenEntities(server);
    }

    private void tickFrozenEntities(MinecraftServer server) {
        long currentTick = server.getTicks();
        if (currentTick == lastFreezeTick) return;
        lastFreezeTick = currentTick;
        Iterator<Map.Entry<Entity, FrozenEntity>> iterator = FROZEN_ENTITIES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, FrozenEntity> entry = iterator.next();
            Entity entity = entry.getKey();
            FrozenEntity freeze = entry.getValue();
            if (entity.isRemoved() || !entity.isAlive()) {
                iterator.remove();
                continue;
            }
            if (currentTick >= freeze.expiresAt) {
                iterator.remove();
                // Time Stop or Absolute Zero may still own this freeze. Let the final active
                // freeze restore the original velocity instead of briefly unfreezing the entity.
                if (!TimeWarperPower.isEntityFrozen(entity) && !IceEmperorPower.isEntityFrozen(entity)) {
                    entity.setVelocity(freeze.velocity);
                    ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
                }
                continue;
            }
            entity.setPosition(freeze.position);
            entity.setVelocity(Vec3d.ZERO);
            entity.setYaw(freeze.yaw);
            entity.setPitch(freeze.pitch);
            ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
        }
    }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int cooldown = cooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            player.sendMessage(Text.literal("Serious Punch on cooldown! " + ((cooldown + 19) / 20) + "s").formatted(Formatting.RED), true);
            return;
        }

        cooldowns.put(uuid, Math.max(0, YPConfig.getInstance().serious_punch_cooldown_ticks));
        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        Vec3d lookDir = player.getRotationVec(1.0F).normalize();
        Vec3d start = player.getEyePos();
        int range = Math.max(1, (int) Math.round(YPConfig.getInstance().punch_raycast_range));

        // Serious Punch now fires as a three-beam fan: center, 30 degrees left, and
        // 30 degrees right. Each beam keeps the original 3x3 block-clearing tunnel.
        List<Vec3d> punchDirections = List.of(
                rotateAroundY(lookDir, -30.0),
                lookDir,
                rotateAroundY(lookDir, 30.0)
        );
        for (Vec3d direction : punchDirections) {
            destroyPunchTunnel(world, start, direction, range);
        }

        AllianceManager alliances = AllianceManager.get(player.getEntityWorld().getServer());
        Box box = player.getBoundingBox().expand(25.0);
        long expiresAt = player.getEntityWorld().getServer().getTicks() + 30L;
        for (Entity entity : world.getOtherEntities(player, box, e -> true)) {
            if (entity instanceof ServerPlayerEntity targetPlayer && alliances.isAlly(player, targetPlayer)) continue;
            if (player.squaredDistanceTo(entity) > 25.0 * 25.0) continue;
            Vec3d targetCenter = entity.getBoundingBox().getCenter();
            boolean struckByFan = false;
            for (Vec3d direction : punchDirections) {
                double along = targetCenter.subtract(start).dotProduct(direction);
                if (along < 0.0 || along > 25.0) continue;
                Vec3d closestPoint = start.add(direction.multiply(along));
                if (closestPoint.squaredDistanceTo(targetCenter) <= 2.0 * 2.0) {
                    struckByFan = true;
                    break;
                }
            }
            if (!struckByFan) continue;
            entity.damage(world, world.getDamageSources().playerAttack(player), 13.0f);
            if (!entity.isAlive() || entity.isRemoved()) continue;
            if (entity instanceof LivingEntity living) {
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
                living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 127, false, false, false));
            }
            FROZEN_ENTITIES.compute(entity, (target, existing) -> {
                if (existing == null) return new FrozenEntity(target, expiresAt);
                existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
                return existing;
            });
        }

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 2));
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private static Vec3d rotateAroundY(Vec3d direction, double degrees) {
        double radians = Math.toRadians(degrees);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3d(
                direction.x * cos - direction.z * sin,
                direction.y,
                direction.x * sin + direction.z * cos
        ).normalize();
    }

    private static void destroyPunchTunnel(ServerWorld world, Vec3d start, Vec3d direction, int range) {
        Vec3d up = new Vec3d(0, 1, 0);
        Vec3d right = direction.crossProduct(up).normalize();
        if (right.lengthSquared() < 0.1) {
            up = new Vec3d(1, 0, 0);
            right = direction.crossProduct(up).normalize();
        }
        Vec3d realUp = right.crossProduct(direction).normalize();

        // breakBlock(false) deliberately suppresses all block drops, as before.
        for (int i = 0; i < range; i++) {
            Vec3d center = start.add(direction.multiply(i));
            if (i % 2 == 0) {
                world.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos blockPos = BlockPos.ofFloored(center.add(right.multiply(dx)).add(realUp.multiply(dy)));
                    BlockState state = world.getBlockState(blockPos);
                    if (!state.isAir() && !state.isOf(Blocks.BEDROCK) && state.getHardness(world, blockPos) >= 0) {
                        world.breakBlock(blockPos, false);
                    }
                }
            }
        }
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int cooldown = cooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) cooldowns.put(uuid, cooldown - 1);
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        cooldowns.remove(player.getUuid());
    }
}
