package com.yppowers.power.fallout;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.SummonSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Fallout Warrior: Radioactive squad summon (G: Healer, Defender, Assassin),
 * Toxic Pool zone (H), and free persistent Radioactive Aura toggle (R).
 */
public final class FalloutWarriorPower extends Power {
    private static final String CD_SQUAD = "fallout_squad";
    private static final String CD_POOL = "fallout_pool";
    private static final String CAT_SQUAD = "fallout_squad";
    private static final String NBT_AURA = "AuraEnabled";
    private static final DustParticleEffect RAD_GREEN = new DustParticleEffect(0x66FF33, 1.1f);

    private static final class ToxicPool {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d center;
        final int radius;
        final double damage;
        int ticksRemaining;

        ToxicPool(UUID ownerId, ServerWorld world, Vec3d center, int radius, double damage, int ticksRemaining) {
            this.ownerId = ownerId;
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.damage = damage;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private final List<ToxicPool> pools = new ArrayList<>();

    @Override
    public PowerId id() {
        return PowerId.FALLOUT_WARRIOR;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Rad Squad", CD_SQUAD),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Toxic Pool", CD_POOL),
                AbilitySpec.toggle(AbilitySlot.ALTERNATE, "Rad Aura")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        return isAuraEnabled(player) ? "Aura ON" : "Aura OFF";
    }

    @Override
    public boolean isToggleActive(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.ALTERNATE && isAuraEnabled(player);
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        PowerConfigs.FalloutWarrior cfg = YPConfig.get().powers.falloutWarrior;
        return switch (slot) {
            case PRIMARY -> cfg.minionEnergyCost;
            case SECONDARY -> cfg.poolEnergyCost;
            case ALTERNATE -> 0.0;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        PowerConfigs.FalloutWarrior cfg = YPConfig.get().powers.falloutWarrior;
        return switch (slot) {
            case PRIMARY -> {
                if (!cds.checkReady(player, CD_SQUAD, "Radioactive Squad")) yield false;
                if (SummonSystem.countActive(server, player.getUuid(), CAT_SQUAD) >= cfg.activeMinionCap) {
                    player.sendMessage(Text.literal("Radioactive minion cap reached.").formatted(Formatting.YELLOW), true);
                    yield false;
                }
                yield true;
            }
            case SECONDARY -> cds.checkReady(player, CD_POOL, "Toxic Pool");
            case ALTERNATE -> true;
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> summonSquad(player);
            case SECONDARY -> spawnToxicPool(player);
            case ALTERNATE -> {
                boolean next = !isAuraEnabled(player);
                setAuraEnabled(player, next);
                player.sendMessage(Text.literal("Radioactive Aura: " + (next ? "ON" : "OFF"))
                        .formatted(next ? Formatting.GREEN : Formatting.YELLOW), true);
            }
            default -> {}
        }
    }

    private void summonSquad(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.FalloutWarrior cfg = YPConfig.get().powers.falloutWarrior;
        int current = SummonSystem.countActive(world.getServer(), player.getUuid(), CAT_SQUAD);
        int toSpawn = Math.min(cfg.minionsPerCast, Math.max(0, cfg.activeMinionCap - current));
        if (toSpawn <= 0) return;

        for (int i = 0; i < toSpawn; i++) {
            double angle = (Math.PI * 2.0 * i) / toSpawn;
            double sx = player.getX() + Math.cos(angle) * 2.2;
            double sz = player.getZ() + Math.sin(angle) * 2.2;
            int role = i % 3;
            MobEntity mob;
            if (role == 2) {
                VindicatorEntity v = EntityType.VINDICATOR.create(world, SpawnReason.TRIGGERED);
                if (v == null) continue;
                v.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
                v.setCustomName(Text.literal("Rad Assassin").formatted(Formatting.GREEN));
                setBaseAttr(v, EntityAttributes.MAX_HEALTH, cfg.assassinHealth);
                if (v.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
                    v.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(0.35 * cfg.assassinSpeedMultiplier);
                }
                mob = v;
            } else {
                PillagerEntity p = EntityType.PILLAGER.create(world, SpawnReason.TRIGGERED);
                if (p == null) continue;
                p.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
                boolean healer = role == 0;
                p.setCustomName(Text.literal(healer ? "Rad Healer" : "Rad Defender").formatted(healer ? Formatting.GREEN : Formatting.DARK_GREEN));
                if (healer) p.addCommandTag("yppowers_rad_healer");
                setBaseAttr(p, EntityAttributes.MAX_HEALTH, healer ? cfg.healerHealth : cfg.defenderHealth);
                mob = p;
            }
            mob.refreshPositionAndAngles(sx, player.getY(), sz, player.getYaw(), 0.0f);
            mob.setCustomNameVisible(true);
            mob.setPersistent();
            mob.setHealth(mob.getMaxHealth());
            mob.addVelocity(0.0, 0.35, 0.0);
            world.spawnEntity(mob);
            SummonSystem.register(mob, player.getUuid(), CAT_SQUAD, cfg.minionLifespanTicks, false);
            VfxSystem.risingHelix(world, RAD_GREEN, mob.getEntityPos(), 0.9, 2.0, 14);
        }

        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_SQUAD, cfg.minionSummonCooldownTicks);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.PLAYERS, 0.9f, 1.1f);
        player.sendMessage(Text.literal("Radioactive Squad deployed!").formatted(Formatting.GREEN), true);
    }

    private void spawnToxicPool(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.FalloutWarrior cfg = YPConfig.get().powers.falloutWarrior;
        pools.add(new ToxicPool(player.getUuid(), world, player.getEntityPos(), cfg.poolRadius, cfg.poolDamage, cfg.poolDurationTicks));
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_POOL, cfg.poolCooldownTicks);
        VfxSystem.shockwave(world, RAD_GREEN, ParticleTypes.SNEEZE, player.getEntityPos().add(0, 0.2, 0), cfg.poolRadius, 36);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 1.2f, 0.6f);
        player.sendMessage(Text.literal("Toxic Pool deployed!").formatted(Formatting.GREEN), true);
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        if (pools.isEmpty()) return;
        Iterator<ToxicPool> it = pools.iterator();
        while (it.hasNext()) {
            ToxicPool pool = it.next();
            pool.ticksRemaining--;
            if (pool.ticksRemaining <= 0) {
                it.remove();
                continue;
            }
            if (pool.ticksRemaining % 6 == 0) {
                VfxSystem.ring(pool.world, RAD_GREEN, pool.center.add(0, 0.2, 0), pool.radius, 28, 0.0);
            }
            if (pool.ticksRemaining % 20 == 0) {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pool.ownerId);
                if (owner == null) continue;
                Box box = Box.of(pool.center, pool.radius * 2.0, 5.0, pool.radius * 2.0);
                for (LivingEntity target : pool.world.getEntitiesByClass(LivingEntity.class, box,
                        e -> CombatUtil.canHarm(owner, e))) {
                    if (target.getEntityPos().distanceTo(pool.center) > pool.radius) continue;
                    target.damage(pool.world, pool.world.getDamageSources().magic(), (float) pool.damage);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0));
                }
            }
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || !(player.getEntityWorld() instanceof ServerWorld world)) return;
        PowerConfigs.FalloutWarrior cfg = YPConfig.get().powers.falloutWarrior;

        // Rad Healer periodic owner heal
        if (server.getTicks() % 60 == 0) {
            for (MobEntity mob : SummonSystem.getOwnedMobs(server, player.getUuid(), CAT_SQUAD)) {
                if (mob.getCommandTags().contains("yppowers_rad_healer") && mob.squaredDistanceTo(player) <= 144.0) {
                    player.heal(1.5f);
                    VfxSystem.burst(world, ParticleTypes.HEART, player.getEntityPos().add(0, 1.2, 0), 2, 0.3, 0.02);
                }
            }
        }

        if (!isAuraEnabled(player)) return;
        if (server.getTicks() % 10 == 0) {
            VfxSystem.ring(world, RAD_GREEN, player.getEntityPos().add(0, 0.2, 0), cfg.auraRadius, 22, 0.0);
        }
        if (server.getTicks() % Math.max(1, cfg.auraIntervalTicks) == 0) {
            AllianceSystem alliances = AllianceSystem.get(server);
            Box box = player.getBoundingBox().expand(cfg.auraRadius);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && e.getEntityPos().distanceTo(player.getEntityPos()) <= cfg.auraRadius)) {
                if (target instanceof ServerPlayerEntity sp && alliances.isAlly(player, sp)) {
                    sp.heal((float) cfg.auraHealAmount);
                } else if (CombatUtil.canHarm(player, target)) {
                    target.damage(world, world.getDamageSources().magic(), (float) cfg.auraDamageAmount);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 40, 0));
                }
            }
        }
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) SummonSystem.discardCategory(server, player.getUuid(), CAT_SQUAD);
    }

    private boolean isAuraEnabled(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return true;
        return PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.FALLOUT_WARRIOR)
                .getBoolean(NBT_AURA, YPConfig.get().powers.falloutWarrior.auraEnabledByDefault);
    }

    private void setAuraEnabled(ServerPlayerEntity player, boolean enabled) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.FALLOUT_WARRIOR).putBoolean(NBT_AURA, enabled);
        store.markDirty();
    }

    private static void setBaseAttr(MobEntity mob, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr, double value) {
        var inst = mob.getAttributeInstance(attr);
        if (inst != null) inst.setBaseValue(value);
    }
}
