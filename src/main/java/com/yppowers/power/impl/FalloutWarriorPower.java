package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/** Radiation aura, poison ground pool, and an explicitly capped three-role pillager squad. */
public final class FalloutWarriorPower extends Power {
    private static final class Pool { final ServerWorld world; final Vec3d center; int ticks;
        Pool(ServerWorld world, Vec3d center, int ticks) { this.world = world; this.center = center; this.ticks = ticks; } }
    private static final class Minion { final PillagerEntity entity; final String role; int ticks;
        Minion(PillagerEntity entity, String role, int ticks) { this.entity = entity; this.role = role; this.ticks = ticks; } }
    private final Map<UUID, Integer> summonCooldowns = new HashMap<>();
    private final Map<UUID, Integer> poolCooldowns = new HashMap<>();
    private final Map<UUID, Pool> pools = new HashMap<>();
    private final Map<UUID, List<Minion>> minions = new HashMap<>();
    private final Map<UUID, Integer> auraClocks = new HashMap<>();
    private final Set<UUID> auraDisabled = new HashSet<>();

    @Override public PowerType getType() { return PowerType.FALLOUT_WARRIOR; }

    @Override public void onAssign(ServerPlayerEntity player) {
        if (!YPConfig.getInstance().fallout_aura_enabled_by_default) auraDisabled.add(player.getUuid());
    }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) { summonWarriors(player); }

    @Override public void onSecondaryActivate(ServerPlayerEntity player) { createPool(player); }

    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (!auraDisabled.remove(id)) {
            auraDisabled.add(id);
            player.sendMessage(Text.literal("Radioactive Aura disabled").formatted(Formatting.YELLOW), true);
        } else player.sendMessage(Text.literal("Radioactive Aura enabled").formatted(Formatting.GREEN), true);
    }

    private void summonWarriors(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id = player.getUuid();
        int cooldown = summonCooldowns.getOrDefault(id, 0);
        if (cooldown > 0) { player.sendMessage(Text.literal("Radioactive Warriors cooldown: " + ((cooldown + 19) / 20) + "s"), true); return; }
        List<Minion> owned = minions.computeIfAbsent(id, ignored -> new ArrayList<>());
        owned.removeIf(m -> m.entity.isRemoved() || !m.entity.isAlive() || m.ticks <= 0);
        int cap = Math.max(3, YPConfig.getInstance().fallout_active_minion_cap);
        int castSize = Math.max(3, YPConfig.getInstance().fallout_minions_per_cast);
        // One activation always creates the requested trio or nothing; never leave a partial squad.
        if (owned.size() + 3 > cap || castSize != 3) {
            player.sendMessage(Text.literal("Radioactive Warriors needs room for a complete three-minion squad."), true); return;
        }
        String[] roles = {"HEALER", "DEFENDER", "ASSASSIN"};
        List<Minion> created = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            PillagerEntity mob = EntityType.PILLAGER.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
            if (mob == null) {
                for (Minion partial : created) { partial.entity.discard(); owned.remove(partial); }
                player.sendMessage(Text.literal("The radioactive squad could not fully manifest."), true);
                return;
            }
            mob.refreshPositionAndAngles(player.getX() + (i - 1) * 1.4, player.getY(), player.getZ() + 1.2, player.getYaw(), 0);
            String role = roles[i];
            mob.setCustomName(Text.literal("☢ " + role + " " + player.getName().getString()).formatted(
                    i == 0 ? Formatting.GREEN : i == 1 ? Formatting.GOLD : Formatting.DARK_GREEN));
            mob.setCustomNameVisible(true);
            mob.addCommandTag("yppowers_fallout_minion");
            mob.addCommandTag("yppowers_owner_" + id);
            mob.setPersistent();
            double hp = switch (role) {
                case "HEALER" -> Math.max(4.0, YPConfig.getInstance().fallout_healer_health);
                case "DEFENDER" -> Math.max(8.0, YPConfig.getInstance().fallout_defender_health);
                default -> Math.max(6.0, YPConfig.getInstance().fallout_assassin_health);
            };
            mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
            mob.setHealth((float) hp);
            if (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
                mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE).setBaseValue(role.equals("ASSASSIN") ? 7.0 : role.equals("DEFENDER") ? 5.0 : 2.0);
            }
            if (role.equals("HEALER")) {
                mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLDEN_CARROT));
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
            } else if (role.equals("DEFENDER")) {
                mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.SHIELD));
                mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            } else {
                mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                if (mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
                    mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(
                            mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getBaseValue()
                                    * Math.max(1.0, YPConfig.getInstance().fallout_assassin_speed_multiplier));
                }
            }
            world.spawnEntity(mob);
            Minion minion = new Minion(mob, role, Math.max(20, YPConfig.getInstance().fallout_minion_lifespan_ticks));
            owned.add(minion);
            created.add(minion);
        }
        summonCooldowns.put(id, Math.max(0, YPConfig.getInstance().fallout_minion_summon_cooldown_ticks));
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PILLAGER_CELEBRATE, SoundCategory.PLAYERS, 1.0f, 0.7f);
        world.spawnParticles(ParticleTypes.COMPOSTER, player.getX(), player.getBodyY(0.6), player.getZ(), 35, 1, 0.8, 1, 0.1);
        EpicVfx.activation(world, player.getX(), player.getY(), player.getZ(), 1.0);
    }

    private void createPool(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        UUID id = player.getUuid();
        int cd = poolCooldowns.getOrDefault(id, 0);
        if (cd > 0) { player.sendMessage(Text.literal("Toxic Pool cooldown: " + ((cd + 19) / 20) + "s"), true); return; }
        pools.put(id, new Pool(world, player.getEntityPos(), Math.max(1, YPConfig.getInstance().fallout_pool_duration_ticks)));
        poolCooldowns.put(id, Math.max(0, YPConfig.getInstance().fallout_pool_cooldown_ticks));
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 1, 0.6f);
        player.sendMessage(Text.literal("Radioactive pool deployed."), true);
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        decrement(summonCooldowns, id); decrement(poolCooldowns, id);
        if (!(player.getEntityWorld() instanceof ServerWorld world) || player.getEntityWorld().getServer() == null) return;
        if (GraceManager.isActive(world.getServer())) return;
        int aura = auraClocks.getOrDefault(id, 0) + 1;
        if (aura >= Math.max(1, YPConfig.getInstance().fallout_aura_interval_ticks)) {
            aura = 0;
            if (!auraDisabled.contains(id)) tickAura(player, world);
        }
        auraClocks.put(id, aura);
        Pool pool = pools.get(id);
        if (pool != null) {
            if (pool.world != world || --pool.ticks <= 0) pools.remove(id);
            else {
                world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pool.center.x, pool.center.y + 0.1, pool.center.z,
                        10, YPConfig.getInstance().fallout_pool_radius / 2.0, 0.3, YPConfig.getInstance().fallout_pool_radius / 2.0, 0.02);
                if (pool.ticks % 10 == 0) {
                    double r = Math.max(1, YPConfig.getInstance().fallout_pool_radius);
                    Box area = new Box(pool.center.x-r, pool.center.y-2, pool.center.z-r, pool.center.x+r, pool.center.y+2, pool.center.z+r);
                    AllianceManager alliances = AllianceManager.get(world.getServer());
                    for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
                        if (target instanceof ServerPlayerEntity p && alliances.isAlly(player, p)) continue;
                        target.damage(world, world.getDamageSources().magic(), (float)Math.max(0, YPConfig.getInstance().fallout_pool_damage));
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
                    }
                }
            }
        }
        List<Minion> owned = minions.get(id);
        if (owned != null) {
            owned.removeIf(m -> {
                if (m.entity.isRemoved() || !m.entity.isAlive() || --m.ticks <= 0) { m.entity.discard(); return true; }
                if (m.ticks % 10 == 0) {
                    LivingEntity target = PowerCombatUtil.nearestTarget(player, 18.0);
                    if (target != null) m.entity.setTarget(target);
                    else {
                        m.entity.setTarget(null);
                        if (m.entity.squaredDistanceTo(player) > 64) m.entity.getNavigation().startMovingTo(player, 1.1);
                    }
                    if (m.role.equals("HEALER") && m.ticks % 40 == 0) {
                        player.heal(1.0f);
                        for (ServerPlayerEntity ally : world.getPlayers()) if (ally != player && alliancesAreAllies(player, ally)
                                && ally.squaredDistanceTo(player) <= 12*12) ally.heal(1.0f);
                    }
                }
                return false;
            });
        }
    }

    private void tickAura(ServerPlayerEntity player, ServerWorld world) {
        double r = Math.max(0, YPConfig.getInstance().fallout_aura_radius);
        Box box = player.getBoundingBox().expand(r);
        AllianceManager alliances = AllianceManager.get(world.getServer());
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (target.squaredDistanceTo(player) > r*r) continue;
            if (target instanceof ServerPlayerEntity other) {
                if (alliances.isAlly(player, other)) other.heal((float)Math.max(0, YPConfig.getInstance().fallout_aura_heal_amount));
                else target.damage(world, world.getDamageSources().magic(), (float)Math.max(0, YPConfig.getInstance().fallout_aura_damage_amount));
            } else target.damage(world, world.getDamageSources().magic(), (float)Math.max(0, YPConfig.getInstance().fallout_aura_damage_amount));
        }
        world.spawnParticles(ParticleTypes.COMPOSTER, player.getX(), player.getBodyY(0.5), player.getZ(), 4, r/3, 0.5, r/3, 0.02);
    }

    private boolean alliancesAreAllies(ServerPlayerEntity a, ServerPlayerEntity b) {
        return AllianceManager.get(a.getEntityWorld().getServer()).isAlly(a, b);
    }
    private static void decrement(Map<UUID, Integer> map, UUID id) {
        int v = map.getOrDefault(id, 0); if (v > 0) map.put(id, v-1);
    }

    @Override public void onPlayerDeath(ServerPlayerEntity player) { cleanup(player.getUuid()); }
    @Override public void onPlayerDisconnect(ServerPlayerEntity player) { cleanup(player.getUuid()); }
    @Override public void onRemove(ServerPlayerEntity player) { cleanup(player.getUuid()); auraDisabled.remove(player.getUuid()); }
    private void cleanup(UUID id) {
        pools.remove(id);
        List<Minion> owned = minions.remove(id);
        if (owned != null) for (Minion m : owned) m.entity.discard();
        auraClocks.remove(id); summonCooldowns.remove(id); poolCooldowns.remove(id);
    }
}
