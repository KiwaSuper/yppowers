package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public final class FalloutWarriorPower extends Power {
   private static final int EMERGE_TICKS = 60;
   private static final double EMERGE_DEPTH = 2.2;
   private final Map<UUID, Integer> summonCooldowns = new HashMap<>();
   private final Map<UUID, Integer> poolCooldowns = new HashMap<>();
   private final Map<UUID, FalloutWarriorPower.Pool> pools = new HashMap<>();
   private final Map<UUID, List<FalloutWarriorPower.Minion>> minions = new HashMap<>();
   private final Map<UUID, Integer> auraClocks = new HashMap<>();
   private final Set<UUID> auraDisabled = new HashSet<>();

   @Override
   public PowerType getType() {
      return PowerType.FALLOUT_WARRIOR;
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      if (!YPConfig.getInstance().fallout_aura_enabled_by_default) {
         this.auraDisabled.add(player.getUuid());
      }
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      this.summonWarriors(player);
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      this.createPool(player);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (!this.auraDisabled.remove(id)) {
         this.auraDisabled.add(id);
         player.sendMessage(Text.literal("Radioactive Aura disabled").formatted(Formatting.YELLOW), true);
         ServerWorld var4 = player.getEntityWorld();
         if (var4 instanceof ServerWorld) {
            EpicVfx.implosion(var4, ParticleTypes.COMPOSTER, player.getX(), player.getBodyY(0.5), player.getZ(), 1.5, 18, 0.12);
         }

         var4 = player.getEntityWorld();
         if (var4 instanceof ServerWorld) {
            EpicVfx.cue(var4, player.getId(), "fallout_warrior", "aura", player.getX(), player.getBodyY(0.5), player.getZ(), Vec3d.ZERO, 0.65F, 20, 0.0F);
         }
      } else {
         player.sendMessage(Text.literal("Radioactive Aura enabled").formatted(Formatting.GREEN), true);
         ServerWorld var6 = player.getEntityWorld();
         if (var6 instanceof ServerWorld) {
            EpicVfx.activation(var6, player.getX(), player.getY(), player.getZ(), 0.8);
         }

         var6 = player.getEntityWorld();
         if (var6 instanceof ServerWorld) {
            EpicVfx.cue(var6, player.getId(), "fallout_warrior", "aura", player.getX(), player.getBodyY(0.5), player.getZ(), Vec3d.ZERO, 0.8F, 24, 0.0F);
         }
      }
   }

   private void summonWarriors(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         ServerWorld world = id;
         UUID var23 = player.getUuid();
         int cooldown = this.summonCooldowns.getOrDefault(var23, 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Radioactive Warriors cooldown: " + (cooldown + 19) / 20 + "s"), true);
         } else {
            List<FalloutWarriorPower.Minion> owned = this.minions.computeIfAbsent(var23, ignored -> new ArrayList<>());
            owned.removeIf(m -> m.entity.isRemoved() || !m.entity.isAlive() || m.ticks <= 0);
            int cap = Math.max(3, YPConfig.getInstance().fallout_active_minion_cap);
            int castSize = Math.max(3, YPConfig.getInstance().fallout_minions_per_cast);
            if (owned.size() + 3 <= cap && castSize == 3) {
               String[] roles = new String[]{"HEALER", "DEFENDER", "ASSASSIN"};
               List<FalloutWarriorPower.Minion> created = new ArrayList<>();

               for (int i = 0; i < 3; i++) {
                  String role = roles[i];
                  MobEntity mob = role.equals("ASSASSIN")
                     ? EntityType.VINDICATOR.create(world, SpawnReason.TRIGGERED)
                     : EntityType.PILLAGER.create(world, SpawnReason.TRIGGERED);
                  if (mob == null) {
                     for (FalloutWarriorPower.Minion partial : created) {
                        partial.entity.discard();
                        owned.remove(partial);
                     }

                     player.sendMessage(Text.literal("The radioactive squad could not fully manifest."), true);
                     return;
                  }

                  double gx = player.getX() + (i - 1) * 1.4;
                  double gz = player.getZ() + 1.2;
                  double gy = groundY(world, gx, player.getY(), gz);
                  mob.refreshPositionAndAngles(gx, gy - 2.2, gz, player.getYaw(), 0.0F);
                  mob.setNoGravity(true);
                  mob.setAiDisabled(true);
                  mob.setInvulnerable(true);
                  mob.setCustomName(
                     Text.literal("☢ " + role + " " + player.getName().getString())
                        .formatted(i == 0 ? Formatting.GREEN : (i == 1 ? Formatting.GOLD : Formatting.DARK_GREEN))
                  );
                  mob.setCustomNameVisible(true);
                  mob.addCommandTag("yppowers_fallout_minion");
                  mob.addCommandTag("yppowers_owner_" + var23);
                  mob.setPersistent();

                  double hp = switch (role) {
                     case "HEALER" -> Math.max(4.0, YPConfig.getInstance().fallout_healer_health);
                     case "DEFENDER" -> Math.max(8.0, YPConfig.getInstance().fallout_defender_health);
                     default -> Math.max(6.0, YPConfig.getInstance().fallout_assassin_health);
                  };
                  mob.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(hp);
                  mob.setHealth((float)hp);
                  if (mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE) != null) {
                     mob.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE)
                        .setBaseValue(role.equals("ASSASSIN") ? 7.0 : (role.equals("DEFENDER") ? 5.0 : 2.0));
                  }

                  mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(role.equals("ASSASSIN") ? Items.IRON_AXE : Items.CROSSBOW));
                  if (role.equals("HEALER")) {
                     mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.GOLDEN_CARROT));
                     mob.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
                  } else if (role.equals("DEFENDER")) {
                     mob.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                     mob.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                  } else if (mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED) != null) {
                     mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED)
                        .setBaseValue(
                           mob.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).getBaseValue()
                              * Math.max(1.0, YPConfig.getInstance().fallout_assassin_speed_multiplier)
                        );
                  }

                  SummonTuning.strengthen(player, mob);
                  AwakeningManager.strengthenFalloutMinion(player, mob);
                  world.spawnEntity(mob);
                  FalloutWarriorPower.Minion minion = new FalloutWarriorPower.Minion(
                     mob, role, Math.max(20, YPConfig.getInstance().fallout_minion_lifespan_ticks)
                  );
                  minion.bx = gx;
                  minion.by = gy;
                  minion.bz = gz;
                  minion.yaw = player.getYaw();
                  owned.add(minion);
                  created.add(minion);
               }

               this.summonCooldowns.put(var23, Math.max(0, YPConfig.getInstance().fallout_minion_summon_cooldown_ticks));
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.PLAYERS, 1.2F, 0.9F);
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PILLAGER_CELEBRATE, SoundCategory.PLAYERS, 1.0F, 0.7F);
               world.spawnParticles(ParticleTypes.COMPOSTER, true, true, player.getX(), player.getBodyY(0.6), player.getZ(), 35, 1.0, 0.8, 1.0, 0.1);
               EpicVfx.activation(world, player.getX(), player.getY(), player.getZ(), 1.0);
               EpicVfx.cue(world, player.getId(), "fallout_warrior", "summon", player.getX(), player.getBodyY(0.5), player.getZ(), Vec3d.ZERO, 1.0F, 30, 0.0F);
            } else {
               player.sendMessage(Text.literal("Radioactive Warriors needs room for a complete three-minion squad."), true);
            }
         }
      }
   }

   private void createPool(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var6 = player.getUuid();
         int cd = this.poolCooldowns.getOrDefault(var6, 0);
         if (cd > 0) {
            player.sendMessage(Text.literal("Toxic Pool cooldown: " + (cd + 19) / 20 + "s"), true);
         } else {
            this.pools.put(var6, new FalloutWarriorPower.Pool(id, player.getEntityPos(), Math.max(1, YPConfig.getInstance().fallout_pool_duration_ticks)));
            this.poolCooldowns.put(var6, Math.max(0, YPConfig.getInstance().fallout_pool_cooldown_ticks));
            Vec3d center = player.getEntityPos();
            EpicVfx.cue(id, player.getId(), "fallout_warrior", "toxic_bomb", center.x, center.y, center.z, Vec3d.ZERO, 0.9F, 24, 0.0F);
            EpicVfx.shockwave(id, center.x, center.y, center.z, Math.min(10.0, Math.max(2.0, (double)YPConfig.getInstance().fallout_pool_radius)));
            EpicVfx.ring(id, ParticleTypes.SPORE_BLOSSOM_AIR, center.x, center.y + 0.15, center.z, 0.35, 0.1, 20, 0.01);
            id.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 1.0F, 0.6F);
            player.sendMessage(Text.literal("Radioactive pool deployed."), true);
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      decrement(this.summonCooldowns, id);
      decrement(this.poolCooldowns, id);
      ServerWorld aura = player.getEntityWorld();
      if (aura instanceof ServerWorld) {
         ServerWorld world = aura;
         if (player.getEntityWorld().getServer() != null) {
            if (GraceManager.isActive(world.getServer())) {
               return;
            }

            if (!this.auraDisabled.contains(id) && player.age % 12 == 0) {
               drawRing(
                  world, player.getEntityPos(), Math.max(0.0, YPConfig.getInstance().fallout_aura_radius), ParticleTypes.HAPPY_VILLAGER, player.getY() + 0.15
               );
            }

            int aurax = this.auraClocks.getOrDefault(id, 0) + 1;
            if (aurax >= Math.max(1, YPConfig.getInstance().fallout_aura_interval_ticks)) {
               aurax = 0;
               if (!this.auraDisabled.contains(id)) {
                  this.tickAura(player, world);
               }
            }

            this.auraClocks.put(id, aurax);
            FalloutWarriorPower.Pool pool = this.pools.get(id);
            if (pool != null) {
               if (pool.world == world && --pool.ticks > 0) {
                  if (pool.ticks % 12 == 0) {
                     drawRing(world, pool.center, Math.max(1, YPConfig.getInstance().fallout_pool_radius), ParticleTypes.COMPOSTER, pool.center.y + 0.15);
                  }

                  if (pool.ticks % 4 == 0) {
                     world.spawnParticles(
                        ParticleTypes.SPORE_BLOSSOM_AIR, true, true,
                        pool.center.x,
                        pool.center.y + 0.1,
                        pool.center.z,
                        4,
                        YPConfig.getInstance().fallout_pool_radius / 2.0,
                        0.3,
                        YPConfig.getInstance().fallout_pool_radius / 2.0,
                        0.02
                     );
                  }

                  if (pool.ticks % 10 == 0) {
                     double r = Math.max(1, YPConfig.getInstance().fallout_pool_radius);
                     Box area = new Box(pool.center.x - r, pool.center.y - 2.0, pool.center.z - r, pool.center.x + r, pool.center.y + 2.0, pool.center.z + r);
                     AllianceManager alliances = AllianceManager.get(world.getServer());

                     for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
                        if (!(target instanceof ServerPlayerEntity p && alliances.isAlly(player, p)) && PowerCombatUtil.canHarm(player, target)) {
                           target.damage(world, world.getDamageSources().magic(), (float)Math.max(0.0, YPConfig.getInstance().fallout_pool_damage));
                           target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0));
                        }
                     }
                  }
               } else {
                  this.pools.remove(id);
               }
            }

            List<FalloutWarriorPower.Minion> owned = this.minions.get(id);
            if (owned != null) {
               owned.removeIf(
                  m -> {
                     if (m.entity.isRemoved() || !m.entity.isAlive()) {
                        m.entity.discard();
                        return true;
                     } else if (m.emerge > 0) {
                        tickEmerge(m, world);
                        return false;
                     } else if (--m.ticks <= 0) {
                        m.entity.discard();
                        return true;
                     } else {
                        if (m.attackClock > 0) {
                           m.attackClock--;
                        }

                        if (m.entity.getEntityWorld() != world || m.entity.squaredDistanceTo(player) > 900.0) {
                           m.entity.teleport(world, player.getX(), player.getY(), player.getZ(), Set.of(), player.getYaw(), 0.0F, false);
                        }

                        boolean manualMelee = m.role.equals("DEFENDER");
                        LivingEntity current = m.entity.getTarget();
                        if (m.ticks % 5 == 0 || current == null || !current.isAlive()) {
                           LivingEntity targetx = PowerCombatUtil.pickDefenseTarget(player, 18.0);
                           if (targetx != null) {
                              m.entity.setTarget(targetx);
                              if (manualMelee && m.entity.squaredDistanceTo(targetx) > 9.0) {
                                 m.entity.getNavigation().startMovingTo(targetx, 1.25);
                              }
                           } else {
                              m.entity.setTarget(null);
                              if (m.entity.squaredDistanceTo(player) > 36.0) {
                                 m.entity.getNavigation().startMovingTo(player, 1.2);
                              }
                           }

                           current = m.entity.getTarget();
                        }

                        if (manualMelee
                           && current != null
                           && current.isAlive()
                           && m.attackClock <= 0
                           && m.entity.squaredDistanceTo(current) <= 7.839999999999999
                           && PowerCombatUtil.canHarm(player, current)) {
                           float damage = (float)m.entity.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);
                           current.damage(world, world.getDamageSources().mobAttack(m.entity), Math.max(1.0F, damage));
                           m.entity.swingHand(Hand.MAIN_HAND);
                           m.attackClock = 20;
                        }

                        if (m.role.equals("HEALER") && m.ticks % 20 == 0) {
                           if (player.squaredDistanceTo(m.entity) <= 576.0 && player.getHealth() < player.getMaxHealth()) {
                              player.heal(1.0F);
                              world.spawnParticles(ParticleTypes.HEART, true, true, player.getX(), player.getBodyY(0.9), player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
                           }

                           for (ServerPlayerEntity ally : world.getPlayers()) {
                              if (ally != player
                                 && this.alliancesAreAllies(player, ally)
                                 && ally.squaredDistanceTo(player) <= 144.0
                                 && ally.getHealth() < ally.getMaxHealth()) {
                                 ally.heal(1.0F);
                              }
                           }
                        }

                        return false;
                     }
                  }
               );
            }

            return;
         }
      }
   }

   private static double groundY(ServerWorld world, double x, double y, double z) {
      BlockPos start = BlockPos.ofFloored(x, y, z);

      for (int i = 0; i <= 8; i++) {
         BlockPos pos = start.down(i);
         if (!world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()) {
            return pos.getY() + 1.0;
         }
      }

      return y;
   }

   private static void tickEmerge(FalloutWarriorPower.Minion m, ServerWorld world) {
      m.emerge--;
      double progress = 1.0 - m.emerge / 60.0;
      m.entity.refreshPositionAndAngles(m.bx, m.by - 2.2 * (1.0 - progress), m.bz, m.yaw, 0.0F);
      m.entity.setVelocity(Vec3d.ZERO);
      BlockState below = world.getBlockState(BlockPos.ofFloored(m.bx, m.by - 0.5, m.bz));
      if (below.isAir()) {
         below = Blocks.DEEPSLATE.getDefaultState();
      }

      world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, below), true, true, m.bx, m.by + 0.1, m.bz, 10, 0.45, 0.1, 0.45, 0.15);
      if (m.emerge % 12 == 0) {
         world.spawnParticles(ParticleTypes.SCULK_SOUL, true, true, m.bx, m.by + 0.3, m.bz, 2, 0.3, 0.2, 0.3, 0.02);
      }

      if (m.emerge <= 0) {
         m.entity.refreshPositionAndAngles(m.bx, m.by, m.bz, m.yaw, 0.0F);
         m.entity.setNoGravity(false);
         m.entity.setAiDisabled(false);
         m.entity.setInvulnerable(false);
      }
   }

   private static void drawRing(ServerWorld world, Vec3d center, double radius, ParticleEffect type, double y) {
      if (!(radius <= 0.0)) {
         int points = (int)Math.max(16L, Math.min(48L, Math.round(radius * 4.0)));

         for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2) * i / points;
            world.spawnParticles(type, true, true, center.x + Math.cos(angle) * radius, y, center.z + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }
   }

   private void tickAura(ServerPlayerEntity player, ServerWorld world) {
      double r = Math.max(0.0, YPConfig.getInstance().fallout_aura_radius);
      Box box = player.getBoundingBox().expand(r);
      AllianceManager alliances = AllianceManager.get(world.getServer());

      for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
         if (!(target.squaredDistanceTo(player) > r * r)) {
            if (target instanceof ServerPlayerEntity other && alliances.isAlly(player, other)) {
               other.heal((float)Math.max(0.0, YPConfig.getInstance().fallout_aura_heal_amount));
            } else if (PowerCombatUtil.canHarm(player, target)) {
               target.damage(world, world.getDamageSources().magic(), (float)Math.max(0.0, YPConfig.getInstance().fallout_aura_damage_amount));
            }
         }
      }

      world.spawnParticles(ParticleTypes.COMPOSTER, true, true, player.getX(), player.getBodyY(0.5), player.getZ(), 4, r / 3.0, 0.5, r / 3.0, 0.02);
   }

   private boolean alliancesAreAllies(ServerPlayerEntity a, ServerPlayerEntity b) {
      return AllianceManager.get(a.getEntityWorld().getServer()).isAlly(a, b);
   }

   private static void decrement(Map<UUID, Integer> map, UUID id) {
      int v = map.getOrDefault(id, 0);
      if (v > 0) {
         map.put(id, v - 1);
      }
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.cleanup(player.getUuid());
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.cleanup(player.getUuid());
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.cleanup(player.getUuid());
      this.auraDisabled.remove(player.getUuid());
   }

   private void cleanup(UUID id) {
      this.pools.remove(id);
      List<FalloutWarriorPower.Minion> owned = this.minions.remove(id);
      if (owned != null) {
         for (FalloutWarriorPower.Minion m : owned) {
            m.entity.discard();
         }
      }

      this.auraClocks.remove(id);
      this.summonCooldowns.remove(id);
      this.poolCooldowns.remove(id);
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.summonCooldowns.remove(playerId);
      this.poolCooldowns.remove(playerId);
   }

   private static final class Minion {
      final MobEntity entity;
      final String role;
      int ticks;
      int attackClock;
      int emerge = 60;
      double bx;
      double by;
      double bz;
      float yaw;

      Minion(MobEntity entity, String role, int ticks) {
         this.entity = entity;
         this.role = role;
         this.ticks = ticks;
      }
   }

   private static final class Pool {
      final ServerWorld world;
      final Vec3d center;
      int ticks;

      Pool(ServerWorld world, Vec3d center, int ticks) {
         this.world = world;
         this.center = center;
         this.ticks = ticks;
      }
   }
}
