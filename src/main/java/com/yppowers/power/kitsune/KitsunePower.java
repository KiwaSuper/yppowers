package com.yppowers.power.kitsune;

import com.yppowers.YPPowersMod;
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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kitsune: Giant Kitsune transformation (R) with long-range block placement,
 * Claw Swipe (G), Foxfire Burst (H), Spirit Pounce (V), Foxfire Detonation (B), and blue soul-fire DoT.
 */
public final class KitsunePower extends Power {
    private static final String CD_CLAW = "kitsune_claw";
    private static final String CD_BURST = "kitsune_burst";
    private static final String CD_POUNCE = "kitsune_pounce";
    private static final String CD_DETONATE = "kitsune_detonate";
    private static final String CD_TRANSFORM = "kitsune_transform";
    private static final Identifier MOD_FOX_SPEED = Identifier.of(YPPowersMod.MOD_ID, "kitsune_fox_speed");

    private static final class FoxfireMark {
        final ServerWorld world;
        int ticksRemaining;
        int clock;

        FoxfireMark(ServerWorld world, int ticksRemaining) {
            this.world = world;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static final class FoxForm {
        final FoxEntity avatar;
        int ticksRemaining;

        FoxForm(FoxEntity avatar, int ticksRemaining) {
            this.avatar = avatar;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private final Map<UUID, FoxForm> transformed = new HashMap<>();
    private final Map<UUID, Map<UUID, FoxfireMark>> foxfire = new HashMap<>();

    @Override
    public PowerId id() {
        return PowerId.KITSUNE;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Claw Swipe", CD_CLAW),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Foxfire Burst", CD_BURST),
                AbilitySpec.active(AbilitySlot.TERTIARY, "Spirit Pounce", CD_POUNCE),
                AbilitySpec.active(AbilitySlot.QUATERNARY, "Detonate", CD_DETONATE),
                AbilitySpec.active(AbilitySlot.ALTERNATE, "Giant Fox", CD_TRANSFORM)
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        FoxForm form = transformed.get(player.getUuid());
        return form != null ? "GIANT FOX " + CooldownSystem.formatSeconds(form.ticksRemaining) : "Human Form";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY
                || slot == AbilitySlot.TERTIARY || slot == AbilitySlot.QUATERNARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        PowerConfigs.Kitsune cfg = YPConfig.get().powers.kitsune;
        return switch (slot) {
            case PRIMARY -> cfg.clawEnergyCost;
            case SECONDARY -> cfg.burstEnergyCost;
            case TERTIARY -> cfg.pounceEnergyCost;
            case QUATERNARY -> cfg.detonationEnergyCost;
            case ALTERNATE -> 0.0;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        if (slot == AbilitySlot.ALTERNATE) {
            return transformed.containsKey(player.getUuid()) || cds.checkReady(player, CD_TRANSFORM, "Giant Kitsune");
        }
        if ((slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY || slot == AbilitySlot.TERTIARY)
                && !transformed.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("Transform into Giant Kitsune (R) first!").formatted(Formatting.AQUA), true);
            return false;
        }
        return switch (slot) {
            case PRIMARY -> cds.checkReady(player, CD_CLAW, "Claw Swipe");
            case SECONDARY -> cds.checkReady(player, CD_BURST, "Foxfire Burst");
            case TERTIARY -> cds.checkReady(player, CD_POUNCE, "Spirit Pounce");
            case QUATERNARY -> cds.checkReady(player, CD_DETONATE, "Foxfire Detonation");
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> clawSwipe(player);
            case SECONDARY -> foxfireBurst(player);
            case TERTIARY -> spiritPounce(player);
            case QUATERNARY -> foxfireDetonation(player);
            case ALTERNATE -> toggleTransform(player);
            default -> {}
        }
    }

    private void toggleTransform(ServerPlayerEntity player) {
        if (transformed.containsKey(player.getUuid())) {
            endTransform(player, true);
            return;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.Kitsune cfg = YPConfig.get().powers.kitsune;

        FoxEntity fox = EntityType.FOX.create(world, SpawnReason.TRIGGERED);
        if (fox != null) {
            fox.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
            fox.setAiDisabled(true);
            fox.setInvulnerable(true);
            fox.setSilent(true);
            fox.setNoGravity(true);
            var scale = fox.getAttributeInstance(EntityAttributes.SCALE);
            if (scale != null) scale.setBaseValue(2.6);
            world.spawnEntity(fox);
        }

        transformed.put(player.getUuid(), new FoxForm(fox, cfg.transformationDurationTicks));
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_TRANSFORM, cfg.transformationCooldownTicks);

        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (speed.getModifier(MOD_FOX_SPEED) != null) speed.removeModifier(MOD_FOX_SPEED);
            speed.addTemporaryModifier(new EntityAttributeModifier(
                    MOD_FOX_SPEED, Math.max(0.0, cfg.transformationSpeedMultiplier - 1.0), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, cfg.transformationDurationTicks, 0, false, false));
        VfxSystem.risingHelix(world, ParticleTypes.SOUL_FIRE_FLAME, player.getEntityPos(), 1.4, 2.8, 22);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_FOX_AMBIENT, SoundCategory.PLAYERS, 1.2f, 0.7f);
        player.sendMessage(Text.literal("GIANT KITSUNE TRANSFORMATION!").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    private void endTransform(ServerPlayerEntity player, boolean notify) {
        FoxForm form = transformed.remove(player.getUuid());
        if (form != null && form.avatar != null && !form.avatar.isRemoved()) {
            form.avatar.discard();
        }
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(MOD_FOX_SPEED) != null) speed.removeModifier(MOD_FOX_SPEED);
        player.removeStatusEffect(StatusEffects.INVISIBILITY);
        if (notify) player.sendMessage(Text.literal("Returned to human form.").formatted(Formatting.GRAY), true);
    }

    private void clawSwipe(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.Kitsune cfg = YPConfig.get().powers.kitsune;
        Vec3d look = CombatUtil.safeLook(player);
        Vec3d center = player.getEyePos().add(look.multiply(cfg.clawRange * 0.55));
        Box area = Box.of(center, cfg.clawRange * 2.0, cfg.clawRange * 2.0, cfg.clawRange * 2.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> CombatUtil.canHarm(player, e))) {
            Vec3d delta = target.getBoundingBox().getCenter().subtract(player.getEyePos());
            double dist = delta.length();
            if (dist > cfg.clawRange || dist < 0.1 || look.dotProduct(delta.normalize()) < 0.35) continue;
            target.damage(world, world.getDamageSources().playerAttack(player), (float) cfg.clawDamage);
            applyFoxfire(player, target);
            Vec3d push = delta.normalize().multiply(0.9).add(0, 0.28, 0);
            target.addVelocity(push);
            ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
        }
        VfxSystem.burst(world, ParticleTypes.SWEEP_ATTACK, center, 6, 0.7, 0.0);
        VfxSystem.burst(world, ParticleTypes.SOUL_FIRE_FLAME, center, 18, 0.7, 0.04);
        VfxSystem.playSound(world, center, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 0.75f);
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_CLAW, cfg.clawCooldownTicks);
    }

    private void foxfireBurst(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.Kitsune cfg = YPConfig.get().powers.kitsune;
        Vec3d origin = player.getEyePos();
        Vec3d dir = CombatUtil.safeLook(player);
        double maxRange = 24.0;

        VfxSystem.beam(world, ParticleTypes.SOUL_FIRE_FLAME, origin.add(dir), origin.add(dir.multiply(maxRange)), 22);
        Box area = player.getBoundingBox().stretch(dir.multiply(maxRange)).expand(1.4);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> CombatUtil.canHarm(player, e))) {
            Vec3d delta = target.getBoundingBox().getCenter().subtract(origin);
            double dist = delta.length();
            if (dist > maxRange || dist < 0.1 || dir.dotProduct(delta.normalize()) < 0.95) continue;
            target.damage(world, world.getDamageSources().magic(), (float) cfg.burstDamage);
            applyFoxfire(player, target);
        }
        VfxSystem.playSound(world, origin, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.4f);
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_BURST, cfg.burstCooldownTicks);
    }

    private void spiritPounce(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.Kitsune cfg = YPConfig.get().powers.kitsune;
        Vec3d origin = player.getEyePos();
        Vec3d dir = CombatUtil.safeLook(player);
        Box area = player.getBoundingBox().stretch(dir.multiply(cfg.pounceRange)).expand(1.6);

        LivingEntity best = null;
        double bestDistSq = cfg.pounceRange * cfg.pounceRange;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area, x -> CombatUtil.canHarm(player, x))) {
            Vec3d delta = e.getBoundingBox().getCenter().subtract(origin);
            double dist = delta.length();
            if (dist > cfg.pounceRange || dist < 0.1 || dir.dotProduct(delta.normalize()) < 0.90) continue;
            if (dist * dist < bestDistSq) {
                bestDistSq = dist * dist;
                best = e;
            }
        }
        if (best != null) {
            Vec3d delta = best.getEntityPos().subtract(player.getEntityPos()).normalize();
            player.setVelocity(delta.multiply(1.5).add(0, 0.35, 0));
            ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
            best.damage(world, world.getDamageSources().playerAttack(player), (float) cfg.pounceDamage);
            applyFoxfire(player, best);
            best.addVelocity(delta.multiply(cfg.pounceKnockback).add(0, 0.4, 0));
            ((EntityVelocityAccessor) best).invokeYppowersScheduleVelocityUpdate();
        } else {
            player.setVelocity(dir.x * 1.1, 0.45, dir.z * 1.1);
            ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        }
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_POUNCE, cfg.pounceCooldownTicks);
    }

    private void foxfireDetonation(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        Map<UUID, FoxfireMark> marks = foxfire.remove(player.getUuid());
        if (marks == null || marks.isEmpty()) {
            player.sendMessage(Text.literal("No targets afflicted with Foxfire!").formatted(Formatting.YELLOW), true);
            return;
        }
        PowerConfigs.Kitsune cfg = YPConfig.get().powers.kitsune;
        int detonated = 0;
        for (Map.Entry<UUID, FoxfireMark> entry : marks.entrySet()) {
            Entity e = entry.getValue().world.getEntity(entry.getKey());
            if (!(e instanceof LivingEntity living) || !living.isAlive()) continue;
            if (!CombatUtil.canHarm(player, living)) continue;
            VfxSystem.shockwave(entry.getValue().world, ParticleTypes.SOUL_FIRE_FLAME, ParticleTypes.EXPLOSION, living.getEntityPos().add(0, 0.5, 0), cfg.detonationRadius, 24);
            VfxSystem.playSound(entry.getValue().world, living.getEntityPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.3f);
            Box splash = living.getBoundingBox().expand(cfg.detonationRadius);
            for (LivingEntity splashTarget : entry.getValue().world.getEntitiesByClass(LivingEntity.class, splash,
                    x -> CombatUtil.canHarm(player, x))) {
                splashTarget.damage(entry.getValue().world, entry.getValue().world.getDamageSources().magic(), (float) cfg.detonationDamage);
            }
            detonated++;
        }
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_DETONATE, cfg.detonationCooldownTicks);
        player.sendMessage(Text.literal("Detonated Foxfire on " + detonated + " target(s)!").formatted(Formatting.AQUA), true);
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || !transformed.containsKey(player.getUuid())) return ActionResult.PASS;
        ItemStack stack = player.getMainHandStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return ActionResult.PASS;
        HitResult hit = player.raycast(YPConfig.get().powers.kitsune.foxPlaceRange, 0.0f, false);
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            ActionResult res = blockItem.place(new ItemPlacementContext(player, hand, stack, bhr));
            if (res.isAccepted()) return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private void applyFoxfire(ServerPlayerEntity owner, LivingEntity target) {
        if (!(target.getEntityWorld() instanceof ServerWorld world)) return;
        int dur = YPConfig.get().powers.kitsune.foxfireDurationTicks;
        foxfire.computeIfAbsent(owner.getUuid(), ignored -> new HashMap<>())
                .put(target.getUuid(), new FoxfireMark(world, dur));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, dur, 0, false, false));
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        FoxForm form = transformed.get(uuid);
        if (form != null) {
            form.ticksRemaining--;
            if (form.ticksRemaining <= 0) {
                endTransform(player, true);
            } else if (form.avatar != null && !form.avatar.isRemoved()) {
                form.avatar.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                form.avatar.setHeadYaw(player.getHeadYaw());
                if (form.ticksRemaining % 4 == 0 && player.getEntityWorld() instanceof ServerWorld world) {
                    VfxSystem.burst(world, ParticleTypes.SOUL_FIRE_FLAME, player.getEntityPos().add(0, 0.3, 0), 3, 0.4, 0.02);
                }
            }
        }

        Map<UUID, FoxfireMark> marks = foxfire.get(uuid);
        if (marks != null && !marks.isEmpty()) {
            float dps = (float) YPConfig.get().powers.kitsune.foxfireDamagePerSecond;
            Iterator<Map.Entry<UUID, FoxfireMark>> it = marks.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, FoxfireMark> entry = it.next();
                FoxfireMark m = entry.getValue();
                Entity e = m.world.getEntity(entry.getKey());
                if (!(e instanceof LivingEntity living) || !living.isAlive() || --m.ticksRemaining <= 0) {
                    it.remove();
                    continue;
                }
                m.clock++;
                if (m.clock % 5 == 0) {
                    VfxSystem.burst(m.world, ParticleTypes.SOUL_FIRE_FLAME, living.getEntityPos().add(0, 1.0, 0), 3, 0.3, 0.02);
                }
                if (m.clock >= 20) {
                    m.clock = 0;
                    if (CombatUtil.canHarm(player, living)) {
                        living.damage(m.world, m.world.getDamageSources().magic(), dps);
                    }
                }
            }
            if (marks.isEmpty()) foxfire.remove(uuid);
        }
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        endTransform(player, false);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        endTransform(player, false);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        endTransform(player, false);
        foxfire.remove(player.getUuid());
    }
}
