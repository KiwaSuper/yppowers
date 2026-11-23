package com.yppowers.power.punch;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.FreezeSystem;
import com.yppowers.system.TempBlockSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Serious Punch: 3-way tunneling shockwave fan (G), HELLDIVE aerial crater slam (H),
 * and capped bare-fist Momentum stacks on consecutive vanilla unarmed melee hits.
 */
public final class SeriousPunchPower extends Power {
    private static final String CD_PUNCH = "serious_punch";
    private static final String CD_HELLDIVE = "serious_punch_helldive";

    private static final class MomentumState {
        UUID targetId;
        int stacks;
        int decayTicks;
    }

    private static final class HelldiveState {
        int ticksAirborne;
        boolean launched;
    }

    private final Map<UUID, MomentumState> momentum = new HashMap<>();
    private final Map<UUID, HelldiveState> helldives = new HashMap<>();

    @Override
    public PowerId id() {
        return PowerId.PUNCH;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Serious Punch", CD_PUNCH),
                AbilitySpec.active(AbilitySlot.SECONDARY, "HELLDIVE", CD_HELLDIVE)
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        MomentumState m = momentum.get(player.getUuid());
        if (m != null && m.stacks > 0) {
            return "Momentum x" + m.stacks;
        }
        return helldives.containsKey(player.getUuid()) ? "HELLDIVE" : "";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.SECONDARY
                ? YPConfig.get().powers.seriousPunch.helldiveEnergyCost
                : super.energyCost(player, slot);
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        if (slot == AbilitySlot.PRIMARY) {
            return cds.checkReady(player, CD_PUNCH, "Serious Punch");
        }
        if (slot == AbilitySlot.SECONDARY) {
            if (helldives.containsKey(player.getUuid())) return false;
            return cds.checkReady(player, CD_HELLDIVE, "HELLDIVE");
        }
        return false;
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.PRIMARY) executeSeriousPunch(player);
        else if (slot == AbilitySlot.SECONDARY) startHelldive(player);
    }

    private void executeSeriousPunch(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.SeriousPunch cfg = YPConfig.get().powers.seriousPunch;

        Vec3d origin = player.getEyePos();
        Vec3d forward = CombatUtil.safeLook(player);
        double[] anglesDeg = {0.0, -30.0, 30.0};
        int range = (int) Math.round(cfg.raycastRange);

        for (double deg : anglesDeg) {
            Vec3d dir = forward.rotateY((float) Math.toRadians(deg)).normalize();
            carveTunnel(world, origin, dir, range);
        }

        Box area = player.getBoundingBox().expand(25.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> CombatUtil.canHarm(player, e))) {
            if (target.getEntityPos().distanceTo(player.getEntityPos()) > 25.0) continue;
            target.damage(world, world.getDamageSources().playerAttack(player), 13.0f);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
            FreezeSystem.freeze(target, FreezeSystem.Source.SERIOUS_PUNCH, 30);
            Vec3d kb = target.getEntityPos().subtract(player.getEntityPos()).normalize().multiply(2.0);
            target.setVelocity(kb.x, 0.7, kb.z);
            ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
        }

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 2));
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_PUNCH, cfg.cooldownTicks);
        VfxSystem.flash(world, origin.add(forward.multiply(2.0)));
        VfxSystem.shockwave(world, ParticleTypes.EXPLOSION, ParticleTypes.CLOUD, player.getEntityPos().add(0, 1.0, 0), 6.0, 30);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0f, 0.6f);
        player.sendMessage(Text.literal("SERIOUS PUNCH!").formatted(Formatting.RED, Formatting.BOLD), true);
    }

    private static void carveTunnel(ServerWorld world, Vec3d origin, Vec3d dir, int range) {
        for (int step = 2; step <= range; step++) {
            Vec3d center = origin.add(dir.multiply(step));
            BlockPos base = BlockPos.ofFloored(center);
            if (step % 4 == 0) {
                VfxSystem.spawn(world, ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.2, 0.2, 0.2, 0.0);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos pos = base.add(dx, dy, dz);
                        var state = world.getBlockState(pos);
                        if (!state.isAir() && !TempBlockSystem.isProtectedBlock(world, pos, state)) {
                            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                        }
                    }
                }
            }
        }
    }

    private void startHelldive(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.SeriousPunch cfg = YPConfig.get().powers.seriousPunch;
        Vec3d look = CombatUtil.safeLook(player);
        double v = cfg.helldiveLaunchVelocity;
        player.setVelocity(-look.x * v * 0.5, v * 1.15, -look.z * v * 0.5);
        ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();

        HelldiveState hs = new HelldiveState();
        hs.launched = true;
        helldives.put(player.getUuid(), hs);
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_HELLDIVE, cfg.helldiveCooldownTicks);
        VfxSystem.burst(world, ParticleTypes.FLAME, player.getEntityPos(), 16, 0.5, 0.1);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.7f);
    }

    private void triggerHelldiveImpact(ServerPlayerEntity player) {
        helldives.remove(player.getUuid());
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        PowerConfigs.SeriousPunch cfg = YPConfig.get().powers.seriousPunch;

        BlockPos center = player.getBlockPos();
        int cr = cfg.helldiveCraterRadius;
        int cd = cfg.helldiveCraterDepth;
        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                if (dx * dx + dz * dz > cr * cr) continue;
                for (int dy = 0; dy >= -cd; dy--) {
                    BlockPos p = center.add(dx, dy, dz);
                    var st = world.getBlockState(p);
                    if (!st.isAir() && !TempBlockSystem.isProtectedBlock(world, p, st)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), 2);
                    }
                }
            }
        }

        Box area = player.getBoundingBox().expand(cfg.helldiveRadius);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> CombatUtil.canHarm(player, e))) {
            if (target.getEntityPos().distanceTo(player.getEntityPos()) > cfg.helldiveRadius) continue;
            target.damage(world, world.getDamageSources().playerAttack(player), (float) cfg.helldiveDamage);
            Vec3d out = target.getEntityPos().subtract(player.getEntityPos()).normalize();
            target.setVelocity(out.x * 1.4, 0.9, out.z * 1.4);
            ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
        }

        VfxSystem.flash(world, player.getEntityPos());
        VfxSystem.shockwave(world, ParticleTypes.FLAME, ParticleTypes.EXPLOSION, player.getEntityPos().add(0, 0.2, 0), cfg.helldiveRadius, 36);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.8f, 0.7f);
    }

    @Override
    public boolean cancelFallDamage(ServerPlayerEntity player, float fallDistance, float damageMultiplier) {
        return helldives.containsKey(player.getUuid());
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity attacker, Entity target, DamageSource source, float amount) {
        if (!CombatUtil.isVanillaMeleeActive(attacker.getUuid())) return amount;
        if (!attacker.getMainHandStack().isEmpty() || !(target instanceof LivingEntity)) return amount;
        MomentumState m = momentum.get(attacker.getUuid());
        if (m == null || !target.getUuid().equals(m.targetId) || m.stacks <= 0) return amount;
        double bonus = 1.0 + m.stacks * YPConfig.get().powers.seriousPunch.momentumDamagePerStack;
        return (float) (amount * bonus);
    }

    @Override
    public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (!attacker.getMainHandStack().isEmpty() || !(target instanceof LivingEntity) || !CombatUtil.canHarm(attacker, target)) {
            return;
        }
        PowerConfigs.SeriousPunch cfg = YPConfig.get().powers.seriousPunch;
        MomentumState m = momentum.computeIfAbsent(attacker.getUuid(), ignored -> new MomentumState());
        if (target.getUuid().equals(m.targetId)) {
            m.stacks = Math.min(cfg.momentumMax, m.stacks + cfg.momentumGainPerHit);
        } else {
            m.targetId = target.getUuid();
            m.stacks = Math.min(cfg.momentumMax, cfg.momentumGainPerHit);
        }
        m.decayTicks = cfg.momentumDecayTicks;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        MomentumState m = momentum.get(uuid);
        if (m != null) {
            m.decayTicks--;
            if (m.decayTicks <= 0) momentum.remove(uuid);
        }

        HelldiveState hs = helldives.get(uuid);
        if (hs != null) {
            hs.ticksAirborne++;
            if (hs.ticksAirborne == 8) {
                Vec3d look = CombatUtil.safeLook(player);
                player.setVelocity(look.x * 1.4, -1.8, look.z * 1.4);
                ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
            }
            if (hs.ticksAirborne > 5 && player.isOnGround()) {
                triggerHelldiveImpact(player);
            } else if (hs.ticksAirborne > 100) {
                helldives.remove(uuid);
            }
        }
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        momentum.remove(player.getUuid());
        helldives.remove(player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        momentum.remove(player.getUuid());
        helldives.remove(player.getUuid());
    }
}
