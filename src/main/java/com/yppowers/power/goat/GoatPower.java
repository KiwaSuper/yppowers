package com.yppowers.power.goat;

import com.yppowers.YPPowersMod;
import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.GraceSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The GOAT: enhanced jumps with fall protection, ramping sprint speed, bonus health/KB resistance,
 * free persistent R passive toggle, and V Overdrive with post-mitigation deferred damage debt.
 */
public final class GoatPower extends Power {
    private static final String CD_OVERDRIVE = "goat_overdrive";
    private static final String NBT_ENABLED = "PassivesEnabled";
    private static final String NBT_DEBT = "DamageDebt";

    private static final Identifier MOD_HEALTH = Identifier.of(YPPowersMod.MOD_ID, "goat_health");
    private static final Identifier MOD_KB = Identifier.of(YPPowersMod.MOD_ID, "goat_kb");
    private static final Identifier MOD_SPRINT = Identifier.of(YPPowersMod.MOD_ID, "goat_sprint");
    private static final Identifier MOD_OD_SPEED = Identifier.of(YPPowersMod.MOD_ID, "goat_overdrive_speed");
    private static final Identifier MOD_OD_KB = Identifier.of(YPPowersMod.MOD_ID, "goat_overdrive_kb");
    private static final DustParticleEffect YELLOW_WAKE = new DustParticleEffect(0xFFE64D, 1.0f);

    private final Map<UUID, Integer> overdriveTicks = new HashMap<>();
    private final Map<UUID, Integer> sprintTicks = new HashMap<>();
    private final Map<UUID, Integer> sprintResetDelay = new HashMap<>();
    private final Set<UUID> trackedJumpAirborne = new HashSet<>();

    @Override
    public PowerId id() {
        return PowerId.GOAT;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.TERTIARY, "Overdrive", CD_OVERDRIVE),
                AbilitySpec.toggle(AbilitySlot.ALTERNATE, "Passives")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        int od = overdriveTicks.getOrDefault(player.getUuid(), 0);
        if (od > 0) return "OVERDRIVE " + CooldownSystem.formatSeconds(od);
        double debt = getDamageDebt(player);
        if (debt > 0.1) return String.format("Debt %.1f HP", debt);
        return arePassivesEnabled(player) ? "Passives ON" : "Passives OFF";
    }

    @Override
    public boolean isToggleActive(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.ALTERNATE && arePassivesEnabled(player);
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.TERTIARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        return switch (slot) {
            case TERTIARY -> YPConfig.get().powers.goat.overdriveEnergyCost;
            case ALTERNATE -> 0.0;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.ALTERNATE) return true;
        if (slot == AbilitySlot.TERTIARY) {
            if (overdriveTicks.getOrDefault(player.getUuid(), 0) > 0) {
                player.sendMessage(Text.literal("GOAT Overdrive is already active.").formatted(Formatting.YELLOW), true);
                return false;
            }
            MinecraftServer server = player.getEntityWorld().getServer();
            return server != null && CooldownSystem.get(server).checkReady(player, CD_OVERDRIVE, "GOAT Overdrive");
        }
        return false;
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.ALTERNATE) {
            boolean next = !arePassivesEnabled(player);
            setPassivesEnabled(player, next);
            if (next) applyPassiveAttributes(player);
            else clearPassiveAttributes(player);
            player.sendMessage(Text.literal("GOAT Passives: " + (next ? "ON" : "OFF"))
                    .formatted(next ? Formatting.GREEN : Formatting.YELLOW), true);
            return;
        }
        if (slot == AbilitySlot.TERTIARY) {
            PowerConfigs.Goat cfg = YPConfig.get().powers.goat;
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server == null) return;
            overdriveTicks.put(player.getUuid(), cfg.overdriveDurationTicks);
            CooldownSystem.get(server).setPersistent(player.getUuid(), CD_OVERDRIVE, cfg.overdriveCooldownTicks);
            applyOverdriveAttributes(player);
            if (player.getEntityWorld() instanceof ServerWorld world) {
                VfxSystem.shockwave(world, ParticleTypes.FLAME, ParticleTypes.ELECTRIC_SPARK, player.getEntityPos().add(0, 0.2, 0), 3.5, 28);
                VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_GOAT_SCREAMING_AMBIENT, SoundCategory.PLAYERS, 1.2f, 0.9f);
            }
            player.sendMessage(Text.literal("GOAT OVERDRIVE ACTIVATED!").formatted(Formatting.GOLD, Formatting.BOLD), true);
        }
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        setPassivesEnabled(player, true);
        applyPassiveAttributes(player);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        if (arePassivesEnabled(player)) applyPassiveAttributes(player);
        else clearPassiveAttributes(player);
        removeOverdriveAttributes(player);
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        if (arePassivesEnabled(newPlayer)) applyPassiveAttributes(newPlayer);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        overdriveTicks.remove(player.getUuid());
        sprintTicks.remove(player.getUuid());
        sprintResetDelay.remove(player.getUuid());
        trackedJumpAirborne.remove(player.getUuid());
        clearPassiveAttributes(player);
        removeOverdriveAttributes(player);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        endOverdrive(player, false);
        trackedJumpAirborne.remove(player.getUuid());
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        endOverdrive(player, false);
        sprintTicks.remove(player.getUuid());
        sprintResetDelay.remove(player.getUuid());
        trackedJumpAirborne.remove(player.getUuid());
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (overdriveTicks.containsKey(p.getUuid())) endOverdrive(p, false);
        }
    }

    @Override
    public void onJump(ServerPlayerEntity player) {
        boolean od = overdriveTicks.getOrDefault(player.getUuid(), 0) > 0;
        if (!od && !arePassivesEnabled(player)) return;
        PowerConfigs.Goat cfg = YPConfig.get().powers.goat;
        double mult = od ? cfg.overdriveJumpMultiplier : cfg.jumpMultiplier;
        Vec3d vel = player.getVelocity();
        double boostedY = Math.max(vel.y, 0.42) * mult;
        player.setVelocity(vel.x, Math.min(boostedY, 1.8), vel.z);
        ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        trackedJumpAirborne.add(player.getUuid());
        if (player.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.burst(world, ParticleTypes.CLOUD, player.getEntityPos(), 8, 0.3, 0.05);
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.PLAYERS, 0.8f, 1.1f);
        }
    }

    @Override
    public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) {
        PowerConfigs.Goat cfg = YPConfig.get().powers.goat;
        boolean od = overdriveTicks.getOrDefault(player.getUuid(), 0) > 0;
        if (od) return 0.0f;
        if (!arePassivesEnabled(player)) return damageMultiplier;
        if (trackedJumpAirborne.contains(player.getUuid()) && cfg.jumpFallProtectionEnabled) {
            return (float) (damageMultiplier * cfg.jumpFallDamageMultiplier);
        }
        if (cfg.passiveFallProtectionEnabled) {
            return (float) (damageMultiplier * cfg.passiveFallDamageMultiplier);
        }
        return damageMultiplier;
    }

    @Override
    public boolean cancelFallDamage(ServerPlayerEntity player, float fallDistance, float damageMultiplier) {
        PowerConfigs.Goat cfg = YPConfig.get().powers.goat;
        if (overdriveTicks.getOrDefault(player.getUuid(), 0) > 0) return true;
        if (arePassivesEnabled(player) && trackedJumpAirborne.remove(player.getUuid())
                && cfg.jumpFallProtectionEnabled && cfg.jumpFallDamageMultiplier <= 1.0e-6) {
            return true;
        }
        return false;
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity attacker, Entity target, DamageSource source, float amount) {
        if (overdriveTicks.getOrDefault(attacker.getUuid(), 0) > 0) {
            return amount * 1.25f;
        }
        if (arePassivesEnabled(attacker) && CombatUtil.isVanillaMeleeActive(attacker.getUuid())) {
            return amount * 0.90f;
        }
        return amount;
    }

    @Override
    public float modifyPostMitigationDamage(ServerPlayerEntity player, DamageSource source, float mitigatedAmount) {
        if (overdriveTicks.getOrDefault(player.getUuid(), 0) <= 0) return mitigatedAmount;
        if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return mitigatedAmount;
        float absorption = Math.max(0.0f, player.getAbsorptionAmount());
        float absorbed = Math.min(absorption, mitigatedAmount);
        if (absorbed > 0.0f) player.setAbsorptionAmount(absorption - absorbed);
        float debt = mitigatedAmount - absorbed;
        if (debt > 0.0f) addDamageDebt(player, debt);
        return 0.0f;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PowerConfigs.Goat cfg = YPConfig.get().powers.goat;

        if (player.isOnGround() && player.getVelocity().y <= 0.0) {
            trackedJumpAirborne.remove(uuid);
        }

        // Overdrive tick
        int od = overdriveTicks.getOrDefault(uuid, 0);
        if (od > 0) {
            if (GraceSystem.isActive(server)) {
                endOverdrive(player, false);
                player.sendMessage(Text.literal("GOAT Overdrive ended because Grace began.").formatted(Formatting.YELLOW), true);
            } else {
                od--;
                if (od <= 0) {
                    endOverdrive(player, true);
                } else {
                    overdriveTicks.put(uuid, od);
                    if (od % 6 == 0 && player.getEntityWorld() instanceof ServerWorld world) {
                        VfxSystem.burst(world, ParticleTypes.ELECTRIC_SPARK, player.getEntityPos().add(0, 1.0, 0), 4, 0.4, 0.05);
                    }
                }
            }
        } else {
            // Pay back deferred Overdrive damage debt gradually
            double debt = getDamageDebt(player);
            if (debt > 0.0 && player.isAlive()
                    && server.getTicks() % Math.max(1, cfg.overdriveDebtReleaseIntervalTicks) == 0
                    && player.getEntityWorld() instanceof ServerWorld world) {
                double release = Math.min(debt, Math.max(0.1, cfg.overdriveDebtReleasePerHit));
                setDamageDebt(player, Math.max(0.0, debt - release));
                player.damage(world, world.getDamageSources().magic(), (float) release);
            }
        }

        // Passives
        if (arePassivesEnabled(player)) {
            StatusEffectInstance jb = player.getStatusEffect(StatusEffects.JUMP_BOOST);
            if (jb == null || (jb.getAmplifier() == cfg.jumpBoostAmplifier && jb.getDuration() <= 10)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, cfg.jumpBoostAmplifier, false, false));
            }
            tickSprintRamp(player, cfg, od > 0);
        } else {
            removeModifier(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPRINT);
            sprintTicks.remove(uuid);
            sprintResetDelay.remove(uuid);
        }
    }

    private void tickSprintRamp(ServerPlayerEntity player, PowerConfigs.Goat cfg, boolean inOverdrive) {
        UUID uuid = player.getUuid();
        if (!cfg.sprintEnabled || inOverdrive) {
            removeModifier(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPRINT);
            return;
        }
        boolean sprinting = player.isSprinting() || (cfg.preserveSprintWhileJumping && !player.isOnGround() && sprintTicks.getOrDefault(uuid, 0) > 0);
        int ramp = sprintTicks.getOrDefault(uuid, 0);
        if (sprinting) {
            ramp = Math.min(cfg.sprintAccelerationTicks, ramp + 1);
            sprintTicks.put(uuid, ramp);
            sprintResetDelay.put(uuid, cfg.sprintResetDelayTicks);
        } else {
            int delay = sprintResetDelay.getOrDefault(uuid, 0);
            if (delay > 0) {
                sprintResetDelay.put(uuid, delay - 1);
            } else {
                ramp = 0;
                sprintTicks.remove(uuid);
            }
        }

        if (ramp <= 0) {
            removeModifier(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPRINT);
            return;
        }

        double progress = (double) ramp / Math.max(1, cfg.sprintAccelerationTicks);
        double targetMult = cfg.sprintStartMultiplier + (cfg.maxSpeed - cfg.sprintStartMultiplier) * progress;
        double bonus = Math.max(0.0, targetMult - 1.0);
        setModifier(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPRINT, bonus, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);

        if (ramp >= cfg.sprintAccelerationTicks && player.getVelocity().horizontalLengthSquared() > 0.04
                && player.getEntityWorld() instanceof ServerWorld world
                && world.getServer() != null && world.getServer().getTicks() % 3 == 0) {
            Vec3d wakePos = player.getEntityPos().subtract(player.getVelocity().normalize().multiply(0.4)).add(0, 0.2, 0);
            world.spawnParticles(YELLOW_WAKE, wakePos.x, wakePos.y, wakePos.z, 2, 0.15, 0.1, 0.15, 0.0);
        }
    }

    private void endOverdrive(ServerPlayerEntity player, boolean notify) {
        overdriveTicks.remove(player.getUuid());
        removeOverdriveAttributes(player);
        if (notify) {
            player.sendMessage(Text.literal("GOAT Overdrive ended. Deferred damage is catching up.").formatted(Formatting.GOLD), true);
        }
    }

    private void applyPassiveAttributes(ServerPlayerEntity player) {
        setModifier(player, EntityAttributes.MAX_HEALTH, MOD_HEALTH, 10.0, EntityAttributeModifier.Operation.ADD_VALUE);
        setModifier(player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_KB, 0.2, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    private void clearPassiveAttributes(ServerPlayerEntity player) {
        removeModifier(player, EntityAttributes.MAX_HEALTH, MOD_HEALTH);
        removeModifier(player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_KB);
        removeModifier(player, EntityAttributes.MOVEMENT_SPEED, MOD_SPRINT);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private void applyOverdriveAttributes(ServerPlayerEntity player) {
        PowerConfigs.Goat cfg = YPConfig.get().powers.goat;
        setModifier(player, EntityAttributes.MOVEMENT_SPEED, MOD_OD_SPEED,
                Math.max(0.0, cfg.overdriveMovementMultiplier - 1.0), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_OD_KB,
                cfg.overdriveKnockbackResistanceBonus, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    private void removeOverdriveAttributes(ServerPlayerEntity player) {
        removeModifier(player, EntityAttributes.MOVEMENT_SPEED, MOD_OD_SPEED);
        removeModifier(player, EntityAttributes.KNOCKBACK_RESISTANCE, MOD_OD_KB);
    }

    private static void setModifier(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                    Identifier id, double value, EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = player.getAttributeInstance(attr);
        if (inst == null) return;
        if (inst.getModifier(id) != null) inst.removeModifier(id);
        inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
    }

    private static void removeModifier(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, Identifier id) {
        EntityAttributeInstance inst = player.getAttributeInstance(attr);
        if (inst != null && inst.getModifier(id) != null) inst.removeModifier(id);
    }

    private boolean arePassivesEnabled(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return true;
        NbtCompound nbt = PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.GOAT);
        return nbt.getBoolean(NBT_ENABLED, true);
    }

    private void setPassivesEnabled(ServerPlayerEntity player, boolean enabled) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.GOAT).putBoolean(NBT_ENABLED, enabled);
        store.markDirty();
    }

    private double getDamageDebt(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return 0.0;
        return Math.max(0.0, PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.GOAT).getDouble(NBT_DEBT, 0.0));
    }

    private void setDamageDebt(ServerPlayerEntity player, double value) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.GOAT).putDouble(NBT_DEBT, Math.max(0.0, value));
        store.markDirty();
    }

    private void addDamageDebt(ServerPlayerEntity player, double amount) {
        double cap = YPConfig.get().powers.goat.overdriveMaxDamageDebt;
        setDamageDebt(player, Math.min(cap, getDamageDebt(player) + amount));
    }
}
