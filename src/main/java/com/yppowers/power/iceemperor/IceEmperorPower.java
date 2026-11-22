package com.yppowers.power.iceemperor;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.network.YPNetworking;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.EnergySystem;
import com.yppowers.system.FreezeSystem;
import com.yppowers.system.SummonSystem;
import com.yppowers.system.TempBlockSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Ice Emperor: Glacial Aura & Ice Trail (R toggle), Absolute Zero (G), Ice Meteor (H),
 * Frost Guard Polar Bears with low-Energy rogue fallback (V), Form Switching (K), and Heat Blast (L).
 */
public final class IceEmperorPower extends Power {
    private static final String CD_ABS_ZERO = "ice_absolute_zero";
    private static final String CD_METEOR = "ice_meteor";
    private static final String CD_GUARDS = "ice_guards";
    private static final String CD_HEAT_LOCKOUT = "ice_heat_blast_lockout";
    private static final String CAT_GUARD = "ice_frost_guard";
    private static final String NBT_ENABLED = "KitEnabled";

    private final IceFormHandler formHandler = new IceFormHandler();

    @Override
    public PowerId id() {
        return PowerId.ICE_EMPEROR;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Absolute Zero", CD_ABS_ZERO),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Ice Meteor", CD_METEOR),
                AbilitySpec.active(AbilitySlot.TERTIARY, "Frost Guards", CD_GUARDS),
                AbilitySpec.toggle(AbilitySlot.ALTERNATE, "Glacial Field"),
                AbilitySpec.utility(AbilitySlot.FORM_SWITCH, "Form"),
                AbilitySpec.active(AbilitySlot.HEAT_BLAST, "Heat Blast", CD_HEAT_LOCKOUT)
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        String form = YPConfig.get().powers.iceEmperor.formsEnabled ? formHandler.getForm(player).displayName() : "Standard";
        return form + (isKitEnabled(player) ? "" : " (OFF)");
    }

    @Override
    public boolean isToggleActive(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.ALTERNATE && isKitEnabled(player);
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY || slot == AbilitySlot.TERTIARY
                || slot == AbilitySlot.ALTERNATE || slot == AbilitySlot.FORM_SWITCH || slot == AbilitySlot.HEAT_BLAST;
    }

    @Override
    public boolean handlesOwnEnergy(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.TERTIARY || slot == AbilitySlot.HEAT_BLAST;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        return (slot == AbilitySlot.ALTERNATE || slot == AbilitySlot.FORM_SWITCH) ? 0.0 : super.energyCost(player, slot);
    }

    @Override
    public int abilityLockoutTicks(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.HEAT_BLAST || slot == AbilitySlot.ALTERNATE) return 0;
        MinecraftServer server = player.getEntityWorld().getServer();
        return server == null ? 0 : CooldownSystem.get(server).getRemaining(player.getUuid(), CD_HEAT_LOCKOUT);
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        if (slot == AbilitySlot.ALTERNATE || slot == AbilitySlot.FORM_SWITCH) return true;
        if (!isKitEnabled(player)) {
            player.sendMessage(Text.literal("Ice Emperor is toggled OFF (press R to enable).").formatted(Formatting.YELLOW), true);
            return false;
        }
        return switch (slot) {
            case PRIMARY -> cds.checkReady(player, CD_ABS_ZERO, "Absolute Zero");
            case SECONDARY -> cds.checkReady(player, CD_METEOR, "Ice Meteor");
            case TERTIARY -> {
                if (EnergySystem.get(server).isBleeding(player.getUuid())) {
                    player.sendMessage(Text.literal("You are Bleeding. Abilities are disabled.").formatted(Formatting.RED), true);
                    yield false;
                }
                yield cds.checkReady(player, CD_GUARDS, "Frost Guards");
            }
            case HEAT_BLAST -> {
                if (EnergySystem.get(server).isBleeding(player.getUuid())) {
                    player.sendMessage(Text.literal("You are Bleeding.").formatted(Formatting.RED), true);
                    yield false;
                }
                if (!cds.checkReady(player, CD_HEAT_LOCKOUT, "Heat Blast lockout")) yield false;
                double min = YPConfig.get().powers.iceEmperor.heatBlastMinEnergy;
                if (EnergySystem.get(server).getEnergy(player.getUuid()) + 1.0e-6 < min) {
                    player.sendMessage(Text.literal("Heat Blast requires at least " + (int) Math.ceil(min) + " Energy.").formatted(Formatting.RED), true);
                    yield false;
                }
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> castAbsoluteZero(player);
            case SECONDARY -> castIceMeteor(player);
            case TERTIARY -> summonFrostGuards(player);
            case ALTERNATE -> toggleKit(player);
            case FORM_SWITCH -> {
                if (!YPConfig.get().powers.iceEmperor.formsEnabled) return;
                IceForm next = formHandler.cycleForm(player);
                player.sendMessage(Text.literal("Ice Emperor Form: " + next.displayName()).formatted(Formatting.AQUA, Formatting.BOLD), true);
            }
            case HEAT_BLAST -> castHeatBlast(player);
            default -> {}
        }
    }

    private void toggleKit(ServerPlayerEntity player) {
        boolean next = !isKitEnabled(player);
        setKitEnabled(player, next);
        if (next) formHandler.applyAttributes(player, formHandler.getForm(player));
        else formHandler.clearAttributes(player);
        player.sendMessage(Text.literal("Ice Emperor Glacial Field: " + (next ? "ON" : "OFF"))
                .formatted(next ? Formatting.AQUA : Formatting.YELLOW), true);
    }

    private void castAbsoluteZero(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        Box area = player.getBoundingBox().expand(10.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> CombatUtil.canHarm(player, e))) {
            if (target.getEntityPos().distanceTo(player.getEntityPos()) > 10.0) continue;
            target.damage(world, world.getDamageSources().freeze(), 4.0f);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, cfg.freezeDurationTicks + 40, 3));
            FreezeSystem.freeze(target, FreezeSystem.Source.ICE_EMPEROR, cfg.freezeDurationTicks);
            // Temporary packed-ice prison around target feet/torso
            BlockPos bp = target.getBlockPos();
            for (int dy = 0; dy <= 1; dy++) {
                TempBlockSystem.setTemporary(world, bp.add(1, dy, 0), Blocks.PACKED_ICE.getDefaultState(), cfg.freezeDurationTicks, "ice_prison", false, null);
                TempBlockSystem.setTemporary(world, bp.add(-1, dy, 0), Blocks.PACKED_ICE.getDefaultState(), cfg.freezeDurationTicks, "ice_prison", false, null);
                TempBlockSystem.setTemporary(world, bp.add(0, dy, 1), Blocks.PACKED_ICE.getDefaultState(), cfg.freezeDurationTicks, "ice_prison", false, null);
                TempBlockSystem.setTemporary(world, bp.add(0, dy, -1), Blocks.PACKED_ICE.getDefaultState(), cfg.freezeDurationTicks, "ice_prison", false, null);
            }
        }
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_ABS_ZERO, cfg.absoluteZeroCooldownTicks);
        VfxSystem.shockwave(world, ParticleTypes.SNOWFLAKE, ParticleTypes.CLOUD, player.getEntityPos().add(0, 0.3, 0), 10.0, 40);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.3f, 0.6f);
        player.sendMessage(Text.literal("ABSOLUTE ZERO!").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    private void castIceMeteor(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        HitResult hit = player.raycast(48.0, 0.0f, false);
        Vec3d impact = hit.getPos();

        double r = Math.min(cfg.meteorRadius, 32.0);
        Box box = Box.of(impact, r * 2.0, r * 2.0, r * 2.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> CombatUtil.canHarm(player, e))) {
            if (target.getEntityPos().distanceTo(impact) > r) continue;
            target.damage(world, world.getDamageSources().freeze(), (float) cfg.meteorDamage);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, cfg.meteorEffectDurationTicks, 3));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, Math.min(80, cfg.meteorEffectDurationTicks), 0));
            FreezeSystem.freeze(target, FreezeSystem.Source.ICE_EMPEROR, Math.min(60, cfg.meteorEffectDurationTicks));
        }

        // Temporary snow/ice patch around impact center
        BlockPos center = BlockPos.ofFloored(impact);
        int iceR = 6;
        for (int dx = -iceR; dx <= iceR; dx++) {
            for (int dz = -iceR; dz <= iceR; dz++) {
                if (dx * dx + dz * dz > iceR * iceR) continue;
                BlockPos ground = center.add(dx, -1, dz);
                if (!world.getBlockState(ground).isAir()) {
                    TempBlockSystem.setTemporary(world, ground, Blocks.PACKED_ICE.getDefaultState(),
                            cfg.meteorIceDurationTicks, "ice_meteor", false, null);
                }
            }
        }

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_METEOR, cfg.meteorCooldownTicks);
        VfxSystem.flash(world, impact.add(0, 1.0, 0));
        VfxSystem.lightPillar(world, ParticleTypes.SNOWFLAKE, impact, 24.0, 36);
        VfxSystem.shockwave(world, ParticleTypes.SNOWFLAKE, ParticleTypes.EXPLOSION, impact.add(0, 0.4, 0), 12.0, 40);
        VfxSystem.playSound(world, impact, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.8f, 0.7f);
        player.sendMessage(Text.literal("ICE METEOR IMPACT!").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    private void summonFrostGuards(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        MinecraftServer server = world.getServer();
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        EnergySystem energy = EnergySystem.get(server);
        double cost = YPConfig.get().energy.defaultTertiaryCost;

        boolean lowEnergy = energy.getEnergy(player.getUuid()) + 1.0e-6 < cost;
        if (lowEnergy) {
            energy.drainAll(player);
            player.sendMessage(Text.literal("Insufficient Energy! Weakened Frost Guards answer — they may turn rogue!").formatted(Formatting.YELLOW), true);
        } else {
            energy.trySpend(player, cost, "Frost Guards");
        }

        for (int i = 0; i < cfg.guardCount; i++) {
            PolarBearEntity bear = EntityType.POLAR_BEAR.create(world, SpawnReason.TRIGGERED);
            if (bear == null) continue;
            boolean rogue = lowEnergy && (world.random.nextDouble() * 100.0 < cfg.guardHostileChancePercent);
            bear.refreshPositionAndAngles(player.getX() + (i == 0 ? 1.5 : -1.5), player.getY(), player.getZ() + 1.5, player.getYaw(), 0.0f);
            bear.setCustomName(Text.literal((rogue ? "Rogue " : "") + "Frost Guard · " + player.getName().getString())
                    .formatted(rogue ? Formatting.RED : Formatting.AQUA));
            bear.setCustomNameVisible(true);
            bear.setPersistent();
            if (lowEnergy) {
                var spd = bear.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
                if (spd != null) spd.setBaseValue(spd.getBaseValue() * cfg.guardLowEnergySpeedMultiplier);
                var atk = bear.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
                if (atk != null) atk.setBaseValue(atk.getBaseValue() * cfg.guardLowEnergyDamageMultiplier);
            }
            world.spawnEntity(bear);
            SummonSystem.register(bear, player.getUuid(), CAT_GUARD, 2400, rogue);
            VfxSystem.risingHelix(world, ParticleTypes.SNOWFLAKE, bear.getEntityPos(), 1.2, 2.2, 16);
        }
        CooldownSystem.get(server).set(player.getUuid(), CD_GUARDS, cfg.guardCooldownTicks);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.PLAYERS, 1.1f, 0.9f);
    }

    private void castHeatBlast(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        MinecraftServer server = world.getServer();
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        EnergySystem.get(server).drainAll(player);
        CooldownSystem.get(server).setPersistent(player.getUuid(), CD_HEAT_LOCKOUT, cfg.heatBlastLockoutTicks);

        Box area = player.getBoundingBox().expand(cfg.heatBlastRadius);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> CombatUtil.canHarm(player, e))) {
            if (target.getEntityPos().distanceTo(player.getEntityPos()) > cfg.heatBlastRadius) continue;
            target.setOnFireFor(cfg.heatBlastFireSeconds);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, cfg.heatBlastSlownessTicks, cfg.heatBlastSlownessAmplifier));
            target.damage(world, world.getDamageSources().onFire(), 6.0f);
        }
        VfxSystem.shockwave(world, ParticleTypes.FLAME, ParticleTypes.LAVA, player.getEntityPos().add(0, 0.5, 0), cfg.heatBlastRadius, 40);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.6f, 0.6f);
        player.sendMessage(Text.literal("HEAT BLAST! All Energy spent — Ice Emperor locked for 60s.").formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    @Override
    public void onAnyMobKilled(ServerWorld world, MobEntity victim, DamageSource source) {
        if (!SummonSystem.hasCategory(victim, CAT_GUARD) || SummonSystem.isRogue(victim)) return;
        UUID ownerId = SummonSystem.getOwnerUuid(victim);
        if (ownerId == null || world.getServer() == null) return;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        double backlash = YPConfig.get().powers.iceEmperor.guardDeathDamage;
        if (owner != null && owner.isAlive() && backlash > 0.0 && owner.getEntityWorld() instanceof ServerWorld ownerWorld) {
            owner.damage(ownerWorld, ownerWorld.getDamageSources().freeze(), (float) backlash);
            owner.sendMessage(Text.literal("A Frost Guard fell — you feel the chilling backlash!").formatted(Formatting.RED), true);
        }
    }

    @Override
    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.IS_FREEZING)) return 0.0f;
        if (source.isIn(DamageTypeTags.IS_FIRE)) return amount * 1.30f;
        return amount;
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity attacker, Entity target, DamageSource source, float amount) {
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        float result = amount;
        if (isKitEnabled(attacker) && attacker.getEntityWorld() instanceof ServerWorld world) {
            var below = world.getBlockState(attacker.getBlockPos().down());
            if (below.isOf(Blocks.ICE) || below.isOf(Blocks.PACKED_ICE) || below.isOf(Blocks.BLUE_ICE) || below.isOf(Blocks.FROSTED_ICE)) {
                result *= 1.20f;
            }
        }
        if (cfg.formsEnabled && isKitEnabled(attacker)) {
            IceForm form = formHandler.getForm(attacker);
            if (form == IceForm.ARMOURED) result *= (float) cfg.armouredOutgoingDamageMultiplier;
            else if (form == IceForm.ASSASSIN && CombatUtil.isVanillaMeleeActive(attacker.getUuid())) {
                result *= (float) cfg.assassinMeleeDamageMultiplier;
            }
        }
        return result;
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        player.setFrozenTicks(0);
        boolean enabled = isKitEnabled(player);
        formHandler.tickForm(player, enabled);
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;

        // Nether Weakness
        if (world.getRegistryKey() == World.NETHER) {
            StatusEffectInstance weak = player.getStatusEffect(StatusEffects.WEAKNESS);
            if (weak == null || weak.getDuration() <= 10) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false));
            }
        }

        if (!enabled) return;
        PowerConfigs.IceEmperor cfg = YPConfig.get().powers.iceEmperor;
        int tick = world.getServer().getTicks();

        // Sprint packed-ice trail
        if (player.isSprinting() && player.isOnGround() && (formHandler.getForm(player) != IceForm.ASSASSIN || cfg.assassinIceTrailEnabled)) {
            BlockPos under = player.getBlockPos().down();
            if (!world.getBlockState(under).isAir()) {
                TempBlockSystem.setTemporary(world, under, Blocks.PACKED_ICE.getDefaultState(), 80, "ice_trail", false, null);
            }
        }

        // Glacial Aura: freeze nearby water & extinguish fire every 5 ticks
        if (tick % 5 == 0) {
            int r = (int) Math.ceil(cfg.auraRadius);
            BlockPos center = player.getBlockPos();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    BlockPos p = center.add(dx, -1, dz);
                    var st = world.getBlockState(p);
                    if (st.isOf(Blocks.WATER)) {
                        TempBlockSystem.setTemporary(world, p, Blocks.FROSTED_ICE.getDefaultState(), 100, "ice_aura_water", false, null);
                    }
                    BlockPos above = center.add(dx, 0, dz);
                    if (world.getBlockState(above).isOf(Blocks.FIRE)) {
                        world.setBlockState(above, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }

        // Glacial Aura frost damage to nearby hostile mobs
        if (tick % Math.max(5, cfg.auraFreezeDamageIntervalTicks) == 0) {
            Box area = player.getBoundingBox().expand(cfg.auraRadius);
            for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, area,
                    e -> e instanceof Monster && CombatUtil.canHarm(player, e))) {
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1));
                mob.damage(world, world.getDamageSources().freeze(), (float) cfg.auraFreezeDamage);
            }
        }
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        setKitEnabled(player, true);
        formHandler.applyAttributes(player, formHandler.getForm(player));
        if (player.getEntityWorld().getServer() != null) {
            YPNetworking.broadcastIceEmperorForm(player.getEntityWorld().getServer(), player, formHandler.getForm(player).name(), true);
        }
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        if (isKitEnabled(player)) formHandler.applyAttributes(player, formHandler.getForm(player));
        if (player.getEntityWorld().getServer() != null) {
            YPNetworking.broadcastIceEmperorForm(player.getEntityWorld().getServer(), player, formHandler.getForm(player).name(), true);
        }
    }

    @Override
    public void onAnyPlayerJoin(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        for (ServerPlayerEntity other : server.getPlayerManager().getPlayerList()) {
            if (store.hasPower(other, PowerId.ICE_EMPEROR)) {
                YPNetworking.sendIceEmperorForm(player, other.getUuid().toString(), formHandler.getForm(other).name(), true);
            }
        }
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        if (isKitEnabled(newPlayer)) formHandler.applyAttributes(newPlayer, formHandler.getForm(newPlayer));
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        formHandler.clearAttributes(player);
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) {
            SummonSystem.discardCategory(server, player.getUuid(), CAT_GUARD);
            YPNetworking.broadcastIceEmperorForm(server, player, "", false);
        }
    }

    private boolean isKitEnabled(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return true;
        return PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.ICE_EMPEROR).getBoolean(NBT_ENABLED, true);
    }

    private void setKitEnabled(ServerPlayerEntity player, boolean enabled) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.ICE_EMPEROR).putBoolean(NBT_ENABLED, enabled);
        store.markDirty();
    }
}
