package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
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
    private static final int EMERGE_TICKS = 60;
    private static final double EMERGE_DEPTH = 2.2;
    private static final class Minion { final MobEntity entity; final String role; int ticks; int attackClock;
        int emerge = EMERGE_TICKS; double bx, by, bz; float yaw;
        Minion(MobEntity entity, String role, int ticks) { this.entity = entity; this.role = role; this.ticks = ticks; } }
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
            String role = roles[i];
            // The Assassin is a Vindicator (native axe melee AI); Healer and Defender stay pillagers.
            MobEntity mob = role.equals("ASSASSIN")
                    ? EntityType.VINDICATOR.create(world, net.minecraft.entity.SpawnReason.TRIGGERED)
                    : EntityType.PILLAGER.create(world, net.minecraft.entity.SpawnReason.TRIGGERED);
            if (mob == null) {
                for (Minion partial : created) { partial.entity.discard(); owned.remove(partial); }
                player.sendMessage(Text.literal("The radioactive squad could not fully manifest."), true);
                return;
            }
            double gx = player.getX() + (i - 1) * 1.4, gz = player.getZ() + 1.2;
            double gy = groundY(world, gx, player.getY(), gz);
            // Warden-style emergence: start buried, rise out of the ground while the AI is paused.
            mob.refreshPositionAndAngles(gx, gy - EMERGE_DEPTH, gz, player.getYaw(), 0);
            mob.setNoGravity(true); mob.setAiDisabled(true); mob.setInvulnerable(true);
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
            // A pillager only attacks while holding a crossbow (it has no melee goal), so every role carries one.
            mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(role.equals("ASSASSIN") ? Items.IRON_AXE : Items.CROSSBOW));
            if (role.equals("HEALER")) {
                mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLDEN_CARROT));
                mob.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
            } else if (role.equals("DEFENDER")) {
                mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            } else {
                if (mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
                    mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(
                            mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getBaseValue()
                                    * Math.max(1.0, YPConfig.getInstance().fallout_assassin_speed_multiplier));
                }
            }
            world.spawnEntity(mob);
            Minion minion = new Minion(mob, role, Math.max(20, YPConfig.getInstance().fallout_minion_lifespan_ticks));
            minion.bx = gx; minion.by = gy; minion.bz = gz; minion.yaw = player.getYaw();
            owned.add(minion);
            created.add(minion);
        }
        summonCooldowns.put(id, Math.max(0, YPConfig.getInstance().fallout_minion_summon_cooldown_ticks));
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.PLAYERS, 1.2f, 0.9f);
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
        // Visible radius rings: green sparkles on the ground edge of the aura.
        if (!auraDisabled.contains(id) && player.age % 4 == 0)
            drawRing(world, player.getEntityPos(), Math.max(0, YPConfig.getInstance().fallout_aura_radius), ParticleTypes.HAPPY_VILLAGER, player.getY() + 0.15);
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
                if (pool.ticks % 4 == 0)
                    drawRing(world, pool.center, Math.max(1, YPConfig.getInstance().fallout_pool_radius), ParticleTypes.COMPOSTER, pool.center.y + 0.15);
                world.spawnParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pool.center.x, pool.center.y + 0.1, pool.center.z,
                        10, YPConfig.getInstance().fallout_pool_radius / 2.0, 0.3, YPConfig.getInstance().fallout_pool_radius / 2.0, 0.02);
                if (pool.ticks % 10 == 0) {
                    double r = Math.max(1, YPConfig.getInstance().fallout_pool_radius);
                    Box area = new Box(pool.center.x-r, pool.center.y-2, pool.center.z-r, pool.center.x+r, pool.center.y+2, pool.center.z+r);
                    AllianceManager alliances = AllianceManager.get(world.getServer());
                    for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
                        if (target instanceof ServerPlayerEntity p && alliances.isAlly(player, p)) continue;
                        if (!PowerCombatUtil.canHarm(player, target)) continue; // never hurt the owner's own squad
                        target.damage(world, world.getDamageSources().magic(), (float)Math.max(0, YPConfig.getInstance().fallout_pool_damage));
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
                    }
                }
            }
        }
        List<Minion> owned = minions.get(id);
        if (owned != null) {
            owned.removeIf(m -> {
                if (m.entity.isRemoved() || !m.entity.isAlive()) { m.entity.discard(); return true; }
                if (m.emerge > 0) { tickEmerge(m, world); return false; }
                if (--m.ticks <= 0) { m.entity.discard(); return true; }
                if (m.attackClock > 0) m.attackClock--;
                // Keep the squad with its owner, including across long moves and dimension changes.
                if (m.entity.getEntityWorld() != world || m.entity.squaredDistanceTo(player) > 30 * 30) {
                    m.entity.teleport(world, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYaw(), 0, false);
                }
                boolean manualMelee = m.role.equals("DEFENDER"); // pillagers have no melee goal; the Vindicator does
                LivingEntity current = m.entity.getTarget();
                if (m.ticks % 5 == 0 || current == null || !current.isAlive()) {
                    LivingEntity target = PowerCombatUtil.pickDefenseTarget(player, 18.0);
                    if (target != null) {
                        m.entity.setTarget(target);
                        if (manualMelee && m.entity.squaredDistanceTo(target) > 9) m.entity.getNavigation().startMovingTo(target, 1.25);
                    } else {
                        m.entity.setTarget(null);
                        if (m.entity.squaredDistanceTo(player) > 36) m.entity.getNavigation().startMovingTo(player, 1.2);
                    }
                    current = m.entity.getTarget();
                }
                if (manualMelee && current != null && current.isAlive() && m.attackClock <= 0
                        && m.entity.squaredDistanceTo(current) <= 2.8 * 2.8 && PowerCombatUtil.canHarm(player, current)) {
                    float damage = (float) m.entity.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
                    current.damage(world, world.getDamageSources().mobAttack(m.entity), Math.max(1.0f, damage));
                    m.entity.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    m.attackClock = 20;
                }
                if (m.role.equals("HEALER") && m.ticks % 20 == 0) {
                    if (player.squaredDistanceTo(m.entity) <= 24 * 24 && player.getHealth() < player.getMaxHealth()) {
                        player.heal(1.0f);
                        world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getBodyY(0.9), player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                    }
                    for (ServerPlayerEntity ally : world.getPlayers()) if (ally != player && alliancesAreAllies(player, ally)
                            && ally.squaredDistanceTo(player) <= 12*12 && ally.getHealth() < ally.getMaxHealth()) ally.heal(1.0f);
                }
                return false;
            });
        }
    }

    /** Top of the first solid block at or below y (searching up to 8 blocks down), so squads emerge from the ground. */
    private static double groundY(ServerWorld world, double x, double y, double z) {
        BlockPos start = BlockPos.ofFloored(x, y, z);
        for (int i = 0; i <= 8; i++) {
            BlockPos pos = start.down(i);
            if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) return pos.getY() + 1.0;
        }
        return y;
    }

    private static void tickEmerge(Minion m, ServerWorld world) {
        m.emerge--;
        double progress = 1.0 - (double) m.emerge / EMERGE_TICKS;
        m.entity.refreshPositionAndAngles(m.bx, m.by - EMERGE_DEPTH * (1.0 - progress), m.bz, m.yaw, 0);
        m.entity.setVelocity(Vec3d.ZERO);
        BlockState below = world.getBlockState(BlockPos.ofFloored(m.bx, m.by - 0.5, m.bz));
        if (below.isAir()) below = Blocks.DEEPSLATE.getDefaultState();
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, below), m.bx, m.by + 0.1, m.bz, 10, 0.45, 0.1, 0.45, 0.15);
        if (m.emerge % 12 == 0) world.spawnParticles(ParticleTypes.SCULK_SOUL, m.bx, m.by + 0.3, m.bz, 2, 0.3, 0.2, 0.3, 0.02);
        if (m.emerge <= 0) {
            m.entity.refreshPositionAndAngles(m.bx, m.by, m.bz, m.yaw, 0);
            m.entity.setNoGravity(false); m.entity.setAiDisabled(false); m.entity.setInvulnerable(false);
        }
    }

    private static void drawRing(ServerWorld world, Vec3d center, double radius, ParticleEffect type, double y) {
        if (radius <= 0) return;
        int points = (int) Math.max(24, Math.min(72, Math.round(radius * 6)));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            world.spawnParticles(type, center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    private void tickAura(ServerPlayerEntity player, ServerWorld world) {
        double r = Math.max(0, YPConfig.getInstance().fallout_aura_radius);
        Box box = player.getBoundingBox().expand(r);
        AllianceManager alliances = AllianceManager.get(world.getServer());
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (target.squaredDistanceTo(player) > r*r) continue;
            if (target instanceof ServerPlayerEntity other && alliances.isAlly(player, other)) {
                other.heal((float)Math.max(0, YPConfig.getInstance().fallout_aura_heal_amount));
                continue;
            }
            if (!PowerCombatUtil.canHarm(player, target)) continue; // never hurt the owner's own squad
            target.damage(world, world.getDamageSources().magic(), (float)Math.max(0, YPConfig.getInstance().fallout_aura_damage_amount));
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
