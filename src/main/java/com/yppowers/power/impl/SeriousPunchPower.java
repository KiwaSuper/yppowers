package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
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
    private final Map<UUID, Integer> momentum = new java.util.HashMap<>();
    private final Map<UUID, Integer> momentumDecay = new java.util.HashMap<>();
    private final Map<UUID, UUID> momentumTarget = new java.util.HashMap<>();
    private final Map<UUID, Integer> hellDiveCooldowns = new java.util.HashMap<>();
    private static final class HellDiveState { int ticks=30; boolean descending; HellDiveState() {} }
    private final Map<UUID, HellDiveState> hellDives = new java.util.HashMap<>();
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

    @Override public void onSecondaryActivate(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int cooldown = hellDiveCooldowns.getOrDefault(id, 0);
        if (cooldown > 0) {
            player.sendMessage(Text.literal("HELLDIVE cooldown: " + ((cooldown + 19) / 20) + "s").formatted(Formatting.RED), true);
            return;
        }
        if (hellDives.containsKey(id)) {
            player.sendMessage(Text.literal("HELLDIVE is already in progress."), true);
            return;
        }
        Vec3d look = player.getRotationVec(1.0F).normalize();
        player.setVelocity(-look.x * 0.45, 1.15, -look.z * 0.45);
        player.velocityDirty = true;
        hellDives.put(id, new HellDiveState());
        hellDiveCooldowns.put(id, Math.max(0, YPConfig.getInstance().serious_punch_helldive_cooldown_ticks));
        player.sendMessage(Text.literal("HELLDIVE — ascent!" ).formatted(Formatting.RED, Formatting.BOLD), true);
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2f, .7f);
    }

    @Override public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
        if (!source.isOf(DamageTypes.PLAYER_ATTACK) || !(target instanceof LivingEntity) || source.getAttacker() != player
                || !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty()) return amount;
        int stacks = Math.max(0, Math.min(Math.max(0, YPConfig.getInstance().serious_punch_momentum_max), momentum.getOrDefault(player.getUuid(), 0)));
        double perStack = Math.max(0, YPConfig.getInstance().serious_punch_momentum_damage_per_stack);
        return (float)(amount * (1.0 + stacks * perStack));
    }

    @Override public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (!(target instanceof LivingEntity) || !attacker.getMainHandStack().isEmpty() || !attacker.getOffHandStack().isEmpty()
                || !PowerCombatUtil.canHarm(attacker, target)) return;
        UUID owner = attacker.getUuid();
        UUID oldTarget = momentumTarget.get(owner);
        int current = momentum.getOrDefault(owner, 0);
        if (oldTarget != null && !oldTarget.equals(target.getUuid())) current = 0;
        int maximum = Math.max(0, YPConfig.getInstance().serious_punch_momentum_max);
        int gain = Math.max(1, YPConfig.getInstance().serious_punch_momentum_gain_per_hit);
        current = Math.min(maximum, current + gain);
        momentum.put(owner, current);
        momentumTarget.put(owner, target.getUuid());
        momentumDecay.put(owner, Math.max(1, YPConfig.getInstance().serious_punch_momentum_decay_ticks));
        attacker.sendMessage(Text.literal("Momentum: " + current + "/" + maximum).formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    private void tickHellDive(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        HellDiveState state = hellDives.get(id);
        if (state == null) return;
        if (--state.ticks <= 0 && !state.descending) {
            state.descending = true;
            state.ticks = 80;
        }
        if (state.descending) {
            Vec3d v = player.getVelocity();
            player.setVelocity(v.x * 0.65, -2.2, v.z * 0.65);
            player.velocityDirty = true;
            if (player.isOnGround() || state.ticks <= 0) {
                performHellDiveImpact(player);
                hellDives.remove(id);
            }
        }
    }

    private void performHellDiveImpact(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || player.getEntityWorld().getServer() == null) return;
        YPConfig cfg = YPConfig.getInstance();
        double radius = Math.max(1.0, cfg.serious_punch_helldive_radius);
        BlockPos center = player.getBlockPos();
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY()+.2, player.getZ(), 1, 0, 0, 0, 0);
        EpicVfx.impact(world, player.getX(), player.getY(), player.getZ(), 2.0);
        world.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.2f, .55f);
        AllianceManager alliances = AllianceManager.get(world.getServer());
        Box area = player.getBoundingBox().expand(radius, 2.0, radius);
        for (Entity entity : world.getOtherEntities(player, area, e -> e.isAlive())) {
            if (entity instanceof ServerPlayerEntity p && alliances.isAlly(player, p)) continue;
            if (entity.squaredDistanceTo(player) > radius * radius) continue;
            entity.damage(world, world.getDamageSources().playerAttack(player), (float)Math.max(0, cfg.serious_punch_helldive_damage));
            Vec3d push = entity.getEntityPos().subtract(player.getEntityPos()).normalize().multiply(1.1).add(0, .65, 0);
            entity.addVelocity(push); entity.velocityDirty = true;
        }
        int craterRadius = Math.max(0, cfg.serious_punch_helldive_crater_radius);
        int depth = Math.max(0, cfg.serious_punch_helldive_crater_depth);
        for (int dx=-craterRadius; dx<=craterRadius; dx++) for (int dz=-craterRadius; dz<=craterRadius; dz++) {
            if (dx*dx+dz*dz > craterRadius*craterRadius) continue;
            for (int dy=0; dy<depth; dy++) {
                BlockPos pos = center.add(dx, -dy, dz);
                BlockState block = world.getBlockState(pos);
                boolean protectedCore = block.isOf(Blocks.HEAVY_CORE) && ThorPower.isProtectedHeavyCore(world, pos);
                if (!block.isAir() && world.getBlockEntity(pos) == null && !block.isOf(Blocks.BEDROCK)
                        && !protectedCore && block.getHardness(world,pos)>=0) world.breakBlock(pos,false);
            }
        }
        world.spawnParticles(ParticleTypes.CRIT, player.getX(), player.getY(), player.getZ(), 80, radius/2, .3, radius/2, .15);
        player.sendMessage(Text.literal("HELLDIVE impact! Momentum: " + momentum.getOrDefault(player.getUuid(), 0)).formatted(Formatting.RED, Formatting.BOLD), true);
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
                    boolean protectedThorCore = state.isOf(Blocks.HEAVY_CORE)
                            && ThorPower.isProtectedHeavyCore(world, blockPos);
                    if (!state.isAir() && !state.isOf(Blocks.BEDROCK) && !protectedThorCore
                            && state.getHardness(world, blockPos) >= 0) {
                        world.breakBlock(blockPos, false);
                    }
                }
            }
        }
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        decrement(cooldowns, uuid); decrement(hellDiveCooldowns, uuid);
        int decay = momentumDecay.getOrDefault(uuid, 0);
        if (decay > 0) momentumDecay.put(uuid, decay - 1);
        else if (momentum.getOrDefault(uuid, 0) > 0) {
            int stacks = momentum.get(uuid) - 1;
            if (stacks <= 0) { momentum.remove(uuid); momentumTarget.remove(uuid); momentumDecay.remove(uuid); }
            else { momentum.put(uuid, stacks); momentumDecay.put(uuid, Math.max(1, YPConfig.getInstance().serious_punch_momentum_decay_ticks)); }
        }
        tickHellDive(player);
    }

    private static void decrement(Map<UUID,Integer> map, UUID id) { int n = map.getOrDefault(id, 0); if (n > 0) map.put(id, n - 1); }

    @Override public void onPlayerDeath(ServerPlayerEntity player) {
        UUID id = player.getUuid(); momentum.remove(id); momentumDecay.remove(id); momentumTarget.remove(id); hellDives.remove(id);
    }
    @Override public void onRemove(ServerPlayerEntity player) {
        UUID id = player.getUuid(); cooldowns.remove(id); hellDiveCooldowns.remove(id); momentum.remove(id); momentumDecay.remove(id); momentumTarget.remove(id); hellDives.remove(id);
    }
}
