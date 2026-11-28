package com.yppowers.power.kitsune;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import java.util.List;
import com.yppowers.system.EpicVfx;
import com.yppowers.system.PowerCombatUtil;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import com.yppowers.progression.RitualManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DragonBreathParticleEffect;
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
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class KitsunePower extends Power {
   private final Map<UUID, Map<UUID, KitsunePower.Foxfire>> foxfireByOwner = new HashMap<>();
   private final Map<UUID, KitsunePower.Transformation> transformations = new HashMap<>();
   private final Map<UUID, Integer> transformationCooldowns = new HashMap<>();
   private final Map<UUID, Integer> clawCooldowns = new HashMap<>();
   private final Map<UUID, Integer> burstCooldowns = new HashMap<>();
   private final Map<UUID, Integer> pounceCooldowns = new HashMap<>();
   private final Map<UUID, Integer> detonationCooldowns = new HashMap<>();
   private final Map<UUID, KitsunePower.CarriedPassenger> carriedPassengers = new HashMap<>();

   @Override
   public PowerType getType() {
      return PowerType.KITSUNE;
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      this.clawSwipe(player);
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      this.foxfireBurst(player);
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      this.spiritPounce(player);
   }

   @Override
   public void onQuaternaryActivate(ServerPlayerEntity player) {
      this.foxfireDetonation(player);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      this.toggleTransformation(player);
   }

   private void clawSwipe(ServerPlayerEntity player) {
      if (!this.transformations.containsKey(player.getUuid())) {
         player.sendMessage(Text.literal("Claw Swipe is only available during Giant Kitsune Transformation."), true);
      } else {
         ServerWorld id = player.getEntityWorld();
         if (id instanceof ServerWorld) {
            ServerWorld world = id;
            UUID var18 = player.getUuid();
            int cd = this.clawCooldowns.getOrDefault(var18, 0);
            if (cd > 0) {
               this.notice(player, "Claw Swipe", cd);
            } else {
               double range = Math.max(1.0, YPConfig.getInstance().kitsune_claw_range);
               Vec3d look = PowerCombatUtil.safeLook(player);
               Vec3d center = player.getEyePos().add(look.multiply(range * 0.55));
               Box area = new Box(center.x - range, center.y - range, center.z - range, center.x + range, center.y + range, center.z + range);
               AllianceManager allies = AllianceManager.get(id.getServer());
               int hits = 0;

               for (LivingEntity target : id.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
                  Vec3d delta = target.getBoundingBox().getCenter().subtract(player.getEyePos());
                  double distance = delta.length();
                  if (!(distance > range)
                     && !(distance < 0.1)
                     && !(look.dotProduct(delta.normalize()) < 0.35)
                     && PowerCombatUtil.canHarm(player, target)) {
                     target.damage(world, world.getDamageSources().playerAttack(player), (float)Math.max(0.0, YPConfig.getInstance().kitsune_claw_damage));
                     this.applyFoxfire(player, target);
                     Vec3d push = delta.normalize().multiply(0.8).add(0.0, 0.25, 0.0);
                     target.addVelocity(push);
                     ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
                     hits++;
                  }
               }

               world.spawnParticles(ParticleTypes.SWEEP_ATTACK, true, true, center.x, center.y, center.z, 8, 0.8, 0.6, 0.8, 0.0);
               world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, center.x, center.y, center.z, 20, 0.8, 0.6, 0.8, 0.04);
               EpicVfx.impact(world, center.x, center.y, center.z, 1.0);
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.75F);
               this.clawCooldowns.put(var18, Math.max(0, YPConfig.getInstance().kitsune_claw_cooldown_ticks));
               if (hits == 0) {
                  player.sendMessage(Text.literal("Claw Swipe missed."), true);
               }
            }
         }
      }
   }

   private void foxfireBurst(ServerPlayerEntity player) {
      if (!this.transformations.containsKey(player.getUuid())) {
         player.sendMessage(Text.literal("Foxfire Burst is only available during Giant Kitsune Transformation."), true);
      } else {
         ServerWorld id = player.getEntityWorld();
         if (id instanceof ServerWorld) {
            ServerWorld world = id;
            UUID var17 = player.getUuid();
            int cd = this.burstCooldowns.getOrDefault(var17, 0);
            if (cd > 0) {
               this.notice(player, "Foxfire Burst", cd);
            } else {
               Vec3d origin = player.getEyePos();
               Vec3d dir = PowerCombatUtil.safeLook(player);
               double maxRange = 24.0;
               AllianceManager allies = AllianceManager.get(id.getServer());
               EpicVfx.cue(id, player.getId(), "kitsune", "foxfire", origin.x, origin.y, origin.z, dir, 1.0F, 16, 0.0F);
               id.spawnParticles(DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), true, true, origin.x, origin.y, origin.z, 24, 0.15, 0.15, 0.15, 0.15);
               EpicVfx.beam(id, ParticleTypes.END_ROD, origin.add(dir), origin.add(dir.multiply(maxRange)), 0.6);
               EpicVfx.ring(id, ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z, 0.3, 0.2, 16, 0.0);

               for (int step = 1; step <= 24; step++) {
                  Vec3d pos = origin.add(dir.multiply(step));
                  if (step % 2 == 0) {
                     world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, pos.x, pos.y, pos.z, 3, 0.12, 0.12, 0.12, 0.02);
                     world.spawnParticles(ParticleTypes.WITCH, true, true, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.01);
                  }
               }

               Box area = player.getBoundingBox().stretch(dir.multiply(maxRange)).expand(1.2);

               for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
                  Vec3d delta = target.getBoundingBox().getCenter().subtract(origin);
                  double distance = delta.length();
                  if (!(distance > maxRange)
                     && !(distance < 0.1)
                     && !(dir.dotProduct(delta.normalize()) < 0.965)
                     && PowerCombatUtil.canHarm(player, target)) {
                     target.damage(world, world.getDamageSources().indirectMagic(player, player), (float)Math.max(0.0, YPConfig.getInstance().kitsune_burst_damage));
                     this.applyFoxfire(player, target);
                  }
               }

               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.4F);
               this.burstCooldowns.put(var17, Math.max(0, YPConfig.getInstance().kitsune_burst_cooldown_ticks));
            }
         }
      }
   }

   private void spiritPounce(ServerPlayerEntity player) {
      if (!this.transformations.containsKey(player.getUuid())) {
         player.sendMessage(Text.literal("Spirit Pounce is only available during Giant Kitsune Transformation."), true);
      } else {
         ServerWorld id = player.getEntityWorld();
         if (id instanceof ServerWorld) {
            UUID var19 = player.getUuid();
            int cd = this.pounceCooldowns.getOrDefault(var19, 0);
            if (cd > 0) {
               this.notice(player, "Spirit Pounce", cd);
            } else {
               double range = Math.max(1.0, YPConfig.getInstance().kitsune_pounce_range);
               Vec3d origin = player.getEyePos();
               Vec3d dir = PowerCombatUtil.safeLook(player);
               Box area = player.getBoundingBox().stretch(dir.multiply(range)).expand(1.4);
               AllianceManager allies = AllianceManager.get(id.getServer());
               LivingEntity best = null;
               double bestD = range * range;

               for (LivingEntity e : id.getEntitiesByClass(LivingEntity.class, area, x -> PowerCombatUtil.canHarm(player, x))) {
                  Vec3d d = e.getBoundingBox().getCenter().subtract(origin);
                  double dist = d.length();
                  if (!(dist > range) && !(dist < 0.1) && !(dir.dotProduct(d.normalize()) < 0.92) && dist * dist < bestD) {
                     best = e;
                     bestD = dist * dist;
                  }
               }

               if (best == null) {
                  player.setVelocity(dir.x * 0.9, 0.45, dir.z * 0.9);
                  ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
                  player.sendMessage(Text.literal("Pounce launched, but no valid target was in reach."), true);
               } else {
                  Vec3d delta = best.getEntityPos().subtract(player.getEntityPos());
                  player.setVelocity(delta.normalize().multiply(1.5).add(0.0, 0.35, 0.0));
                  ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
                  best.damage(id, id.getDamageSources().playerAttack(player), (float)Math.max(0.0, YPConfig.getInstance().kitsune_pounce_damage));
                  this.applyFoxfire(player, best);
                  Vec3d push = delta.normalize().multiply(Math.max(0.0, YPConfig.getInstance().kitsune_pounce_knockback)).add(0.0, 0.4, 0.0);
                  best.addVelocity(push);
                  ((EntityVelocityAccessor)best).invokeYppowersScheduleVelocityUpdate();
               }

               id.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, player.getX(), player.getY(), player.getZ(), 30, 0.5, 0.5, 0.5, 0.08);
               EpicVfx.cue(id, player.getId(), "kitsune", "soul_step", player.getX(), player.getY(), player.getZ(), dir, 1.0F, 14, 0.0F);
               EpicVfx.activation(id, player.getX(), player.getY(), player.getZ(), 1.0);
               this.pounceCooldowns.put(var19, Math.max(0, YPConfig.getInstance().kitsune_pounce_cooldown_ticks));
            }
         }
      }
   }

   public void useCarryAbility(ServerPlayerEntity player) {
      UUID ownerId = player.getUuid();
      if (this.transformations.containsKey(ownerId)) {
         ServerWorld transformation = player.getEntityWorld();
         if (transformation instanceof ServerWorld && transformation.getServer() != null) {
            if (GraceManager.blockAbility(player)) {
               return;
            }
            if (EnergyManager.get(transformation.getServer()).getBleedingTicks(ownerId) > 0) {
               player.sendMessage(Text.literal("You are Bleeding. Abilities are disabled until the effect ends.").formatted(Formatting.RED), true);
               return;
            }
            KitsunePower.Transformation transformationx = this.transformations.get(ownerId);
            if (transformationx != null && !transformationx.fox.isRemoved()) {
               KitsunePower.CarriedPassenger current = this.carriedPassengers.get(ownerId);
               if (current != null && this.isPassengerStillCarried(current, transformationx)) {
                  this.kickOffPassenger(player, current, true);
                  return;
               }

               if (current != null && current.scruffCarry && current.entity != null && !current.entity.isRemoved()) {
                  current.entity.setNoGravity(current.previousNoGravity);
               }

               this.carriedPassengers.remove(ownerId);
               LivingEntity target = this.findCarryTarget(player, transformation);
               if (target == null) {
                  player.sendMessage(
                     Text.literal("Aim at an ally or enemy within " + (int)YPConfig.getInstance().kitsune_carry_range + " blocks to pick them up."), true
                  );
                  return;
               }

               boolean ally = this.isFriendlyCarryTarget(player, target);
               boolean previousNoGravity = target.hasNoGravity();
               if (ally) {
                  if (!target.startRiding(transformationx.fox, true, true)) {
                     player.sendMessage(Text.literal("That ally cannot ride on the Kitsune."), true);
                     return;
                  }
               } else {
                  target.stopRiding();
                  target.setNoGravity(true);
                  this.positionEnemyAtScruff(transformationx.fox, target);
               }

               this.carriedPassengers.put(ownerId, new KitsunePower.CarriedPassenger(target, !ally, previousNoGravity));
               transformation.spawnParticles(
                  ParticleTypes.SOUL_FIRE_FLAME, true, true, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(), 22, 0.35, 0.45, 0.35, 0.035
               );
               transformation.spawnParticles(ParticleTypes.END_ROD, true, true, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.35, 0.4, 0.025);
               transformation.playSound(
                  null,
                  player.getBlockPos(),
                  ally ? SoundEvents.ENTITY_FOX_AMBIENT : SoundEvents.ENTITY_FOX_AGGRO,
                  SoundCategory.PLAYERS,
                  1.0F,
                  ally ? 1.25F : 0.8F
               );
               player.sendMessage(
                  Text.literal(
                        ally
                           ? target.getName().getString() + " is riding on your Kitsune form. Press C again to kick them off."
                           : "You grabbed " + target.getName().getString() + " by the scruff! They will take damage while carried. Press C to throw them off."
                     )
                     .formatted(Formatting.AQUA),
                  true
               );
               return;
            }

            return;
         }
      }

      player.sendMessage(Text.literal("Kitsune Carry is only available during Giant Kitsune Transformation."), true);
   }

   private boolean isPassengerStillCarried(KitsunePower.CarriedPassenger carried, KitsunePower.Transformation transformation) {
      if (carried.entity != null && !carried.entity.isRemoved() && carried.entity.isAlive()) {
         return carried.scruffCarry ? carried.entity.getVehicle() == null : carried.entity.getVehicle() == transformation.fox;
      } else {
         return false;
      }
   }

   private void positionEnemyAtScruff(FoxEntity fox, Entity enemy) {
      double yaw = Math.toRadians(fox.getYaw());
      Vec3d forward = new Vec3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
      double reach = fox.getWidth() * 0.34 + enemy.getWidth() * 0.2;
      Vec3d grip = fox.getEntityPos().add(forward.multiply(reach)).add(0.0, fox.getHeight() * 0.66 - enemy.getHeight() * 0.32, 0.0);
      enemy.refreshPositionAndAngles(grip.x, grip.y, grip.z, fox.getYaw(), 0.0F);
      enemy.setVelocity(Vec3d.ZERO);
      if (enemy instanceof LivingEntity living) {
         living.fallDistance = 0.0;
      }
   }

   private LivingEntity findCarryTarget(ServerPlayerEntity player, ServerWorld world) {
      double range = Math.max(3.0, YPConfig.getInstance().kitsune_carry_range);
      Vec3d origin = player.getEyePos();
      Vec3d look = PowerCombatUtil.safeLook(player);
      Box search = player.getBoundingBox().expand(range);
      LivingEntity best = null;
      double bestDistance = range * range;

      for (LivingEntity candidate : world.getEntitiesByClass(
         LivingEntity.class,
         search,
         entity -> entity != player
            && entity.isAlive()
            && !(entity instanceof ArmorStandEntity)
            && !(entity instanceof WitherEntity)
            && !(entity instanceof EnderDragonEntity)
            && !(entity instanceof WardenEntity)
            && !(entity instanceof ElderGuardianEntity)
            && !entity.getCommandTags().contains("yppowers_morph_disguise")
            && !(entity instanceof FoxEntity fox && fox.getCommandTags().contains("yppowers_kitsune_avatar"))
      )) {
         if ((candidate.getVehicle() == null || candidate.getVehicle() == player)
            && !(candidate instanceof ServerPlayerEntity other && other.isSpectator())
            && (this.isFriendlyCarryTarget(player, candidate) || PowerCombatUtil.canHarm(player, candidate))) {
            Vec3d delta = candidate.getBoundingBox().getCenter().subtract(origin);
            double distanceSquared = delta.lengthSquared();
            if (!(distanceSquared < 0.25) && !(distanceSquared > bestDistance)) {
               double distance = Math.sqrt(distanceSquared);
               if (!(look.dotProduct(delta.multiply(1.0 / distance)) < 0.94)) {
                  BlockHitResult obstruction = world.raycast(
                     new RaycastContext(origin, candidate.getBoundingBox().getCenter(), ShapeType.COLLIDER, FluidHandling.NONE, player)
                  );
                  if (obstruction.getType() != Type.BLOCK || !(obstruction.getPos().squaredDistanceTo(origin) + 0.15 < distanceSquared)) {
                     best = candidate;
                     bestDistance = distanceSquared;
                  }
               }
            }
         }
      }

      return best;
   }

   private boolean isFriendlyCarryTarget(ServerPlayerEntity owner, Entity target) {
      return target instanceof ServerPlayerEntity other && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other)
         ? true
         : PowerCombatUtil.isFriendlyMinion(owner, target);
   }

   private void kickOffPassenger(ServerPlayerEntity player, KitsunePower.CarriedPassenger carried, boolean announce) {
      Entity passenger = carried.entity;
      this.carriedPassengers.remove(player.getUuid());
      KitsunePower.Transformation transformation = this.transformations.get(player.getUuid());
      Entity mount = transformation == null ? null : transformation.fox;
      if (passenger != null && !passenger.isRemoved()) {
         if (carried.scruffCarry || mount != null && passenger.getVehicle() == mount) {
            if (!carried.scruffCarry) {
               passenger.stopRiding();
            } else {
               passenger.setNoGravity(carried.previousNoGravity);
            }

            Vec3d direction = PowerCombatUtil.safeLook(player);
            Vec3d launch = new Vec3d(direction.x, Math.max(0.15, direction.y), direction.z).normalize().multiply(1.35).add(0.0, 0.45, 0.0);
            passenger.setVelocity(launch);
            ((EntityVelocityAccessor)passenger).invokeYppowersScheduleVelocityUpdate();
            if (passenger.getEntityWorld() instanceof ServerWorld world) {
               world.spawnParticles(
                  ParticleTypes.CLOUD, true, true, passenger.getX(), passenger.getY() + passenger.getHeight() * 0.5, passenger.getZ(), 16, 0.28, 0.35, 0.28, 0.04
               );
               world.spawnParticles(
                  ParticleTypes.SOUL_FIRE_FLAME, true, true,
                  passenger.getX(),
                  passenger.getY() + passenger.getHeight() * 0.5,
                  passenger.getZ(),
                  12,
                  0.25,
                  0.35,
                  0.25,
                  0.025
               );
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FOX_SCREECH, SoundCategory.PLAYERS, 0.8F, 1.4F);
            }

            if (announce) {
               player.sendMessage(Text.literal("Kitsune passenger kicked off."), true);
            }
         }
      }
   }

   private void tickCarryAndTunnel(ServerPlayerEntity player, ServerWorld world) {
      UUID ownerId = player.getUuid();
      KitsunePower.CarriedPassenger carried = this.carriedPassengers.get(ownerId);
      if (carried != null) {
         Entity passenger = carried.entity;
         KitsunePower.Transformation transformation = this.transformations.get(ownerId);
         if (passenger == null || passenger.isRemoved()) {
            this.carriedPassengers.remove(ownerId);
         } else if (!passenger.isAlive()) {
            if (carried.scruffCarry) {
               passenger.setNoGravity(carried.previousNoGravity);
            } else {
               passenger.stopRiding();
            }

            this.carriedPassengers.remove(ownerId);
         } else if (transformation != null && !transformation.fox.isRemoved()) {
            if (carried.scruffCarry) {
               if (passenger.getVehicle() != null) {
                  passenger.setNoGravity(carried.previousNoGravity);
                  this.carriedPassengers.remove(ownerId);
                  return;
               }

               this.positionEnemyAtScruff(transformation.fox, passenger);
            } else if (passenger.getVehicle() != transformation.fox) {
               this.carriedPassengers.remove(ownerId);
               return;
            }

            if (!(passenger instanceof LivingEntity living && carried.scruffCarry)) {
               carried.damageClock = 20;
            } else if (--carried.damageClock <= 0) {
               carried.damageClock = 20;
               if (PowerCombatUtil.canHarm(player, living)) {
                  living.damage(world, world.getDamageSources().indirectMagic(player, player), (float)Math.max(0.0, YPConfig.getInstance().kitsune_carry_enemy_damage_per_second));
                  world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, living.getX(), living.getBodyY(0.5), living.getZ(), 8, 0.28, 0.36, 0.28, 0.025);
               }
            }

            Vec3d velocity = player.getVelocity();
            if (velocity.y < -0.05) {
               player.setVelocity(velocity.x, -0.05, velocity.z);
               ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
               velocity = player.getVelocity();
            }

            Vec3d forward = new Vec3d(PowerCombatUtil.safeLook(player).x, 0.0, PowerCombatUtil.safeLook(player).z);
            if (forward.lengthSquared() < 0.12) {
               forward = new Vec3d(velocity.x, 0.0, velocity.z);
            }

            if (player.isSprinting() || !(velocity.horizontalLengthSquared() < 0.025)) {
               if (!(forward.lengthSquared() < 0.01)) {
                  forward = forward.normalize();
                  Vec3d right = new Vec3d(-forward.z, 0.0, forward.x);
                  Vec3d front = player.getEntityPos().add(forward.multiply(player.getWidth() * 0.5 + 0.62));
                  int floorY = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ()).getY();
                  int destroyed = 0;
                  int limit = Math.max(1, YPConfig.getInstance().kitsune_tunnel_blocks_per_tick);

                  for (int dy = 0; dy <= 1 && destroyed < limit; dy++) {
                     for (int lateral = -1; lateral <= 1 && destroyed < limit; lateral++) {
                        BlockPos pos = BlockPos.ofFloored(front.add(right.multiply(lateral * 0.48)).add(0.0, dy + 0.08, 0.0));
                        if (world.isInBuildLimit(pos)
                           && world.getWorldBorder().contains(pos)
                           && TimeWarperDomainManager.mayModifyBlock(player, pos)) {
                           BlockState state = world.getBlockState(pos);
                           if (!state.isAir()
                              && !state.hasBlockEntity()
                              && world.getFluidState(pos).isEmpty()
                              && !state.isOf(Blocks.BEDROCK)
                              && !state.isOf(Blocks.BARRIER)
                              && !state.isOf(Blocks.HEAVY_CORE)
                              && !(state.getHardness(world, pos) < 0.0F)
                              && world.breakBlock(pos, false, player)) {
                              destroyed++;
                              if (player.age % 3 == 0) {
                                 world.spawnParticles(ParticleTypes.POOF, true, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.18, 0.18, 0.18, 0.025);
                              }
                           }
                        }
                     }
                  }

                  if (destroyed > 0 && player.age % 5 == 0) {
                     world.playSound(
                        null, player.getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 0.65F, 0.75F + player.getRandom().nextFloat() * 0.35F
                     );
                  }
               }
            }
         } else {
            if (carried.scruffCarry) {
               passenger.setNoGravity(carried.previousNoGravity);
            }

            this.carriedPassengers.remove(ownerId);
         }
      }
   }

   private void toggleTransformation(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      KitsunePower.Transformation current = this.transformations.get(id);
      if (current != null) {
         this.endTransformation(player, "You returned to normal form.");
      } else {
         int cd = this.transformationCooldowns.getOrDefault(id, 0);
         if (cd > 0) {
            this.notice(player, "Giant Kitsune Transformation", cd);
         } else {
            ServerWorld fox = player.getEntityWorld();
            if (fox instanceof ServerWorld) {
               FoxEntity foxx = EntityType.FOX.create(fox, SpawnReason.TRIGGERED);
               if (foxx == null) {
                  player.sendMessage(Text.literal("The fox avatar could not manifest."), true);
               } else {
                  foxx.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
                  foxx.setCustomName(Text.literal("Kitsune · " + player.getName().getString()).formatted(Formatting.AQUA, Formatting.BOLD));
                  foxx.setCustomNameVisible(false);
                  foxx.setAiDisabled(true);
                  foxx.addCommandTag("yppowers_kitsune_avatar");
                  foxx.addCommandTag("yppowers_owner_" + id);
                  foxx.setInvulnerable(true);
                  foxx.setNoGravity(true);

                  try {
                     EntityAttributeInstance scale = foxx.getAttributeInstance(EntityAttributes.SCALE);
                     if (scale != null) {
                        scale.setBaseValue(Math.max(1.0, YPConfig.getInstance().kitsune_transformation_speed_multiplier));
                     }
                  } catch (Throwable var11) {
                  }

                  StatusEffectInstance old = player.getStatusEffect(StatusEffects.INVISIBILITY);
                  StatusEffectInstance oldSpeed = player.getStatusEffect(StatusEffects.SPEED);
                  this.transformations
                     .put(
                        id,
                        new KitsunePower.Transformation(
                           fox,
                           foxx,
                           Math.max(1, YPConfig.getInstance().kitsune_transformation_duration_ticks),
                           old == null ? null : new StatusEffectInstance(old),
                           oldSpeed == null ? null : new StatusEffectInstance(oldSpeed)
                        )
                     );
                  player.addStatusEffect(
                     new StatusEffectInstance(
                        StatusEffects.INVISIBILITY, Math.max(1, YPConfig.getInstance().kitsune_transformation_duration_ticks), 0, false, false, false
                     )
                  );
                  fox.spawnEntity(foxx);
                  EpicVfx.cue(fox, player.getId(), "kitsune", "summon", foxx.getX(), foxx.getY(), foxx.getZ(), Vec3d.ZERO, 1.35F, 24, 0.0F);
                  EpicVfx.activation(fox, foxx.getX(), foxx.getY(), foxx.getZ(), 1.35);
                  EpicVfx.helix(fox, ParticleTypes.SOUL_FIRE_FLAME, foxx.getX(), foxx.getY(), foxx.getZ(), 2.2, 0.75, 2.0, 20, 0.08);
                  YPNetworking.syncShadowArcherMorphView(player, true, foxx.getId(), Math.max(1.62F, foxx.getEyeHeight(foxx.getPose())), foxx.getHeight());
                  double speed = Math.max(1.0, YPConfig.getInstance().kitsune_transformation_speed_multiplier);
                  player.addStatusEffect(
                     new StatusEffectInstance(
                        StatusEffects.SPEED,
                        Math.max(1, YPConfig.getInstance().kitsune_transformation_duration_ticks),
                        Math.max(0, (int)Math.ceil(speed) - 1),
                        false,
                        false
                     )
                  );
                  fox.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FOX_SCREECH, SoundCategory.PLAYERS, 1.4F, 0.7F);
                  player.sendMessage(Text.literal("GIANT KITSUNE — Foxfire transformation active.").formatted(Formatting.AQUA, Formatting.BOLD), true);
               }
            }
         }
      }
   }

   private static void resetView(ServerPlayerEntity player) {
      try {
         YPNetworking.syncShadowArcherMorphView(player, false, -1, 1.62F, 1.8F);
      } catch (RuntimeException var2) {
      }
   }

   private void endTransformation(ServerPlayerEntity player, String message) {
      KitsunePower.CarriedPassenger carried = this.carriedPassengers.get(player.getUuid());
      if (carried != null) {
         this.kickOffPassenger(player, carried, false);
      }

      resetView(player);
      KitsunePower.Transformation trans = this.transformations.remove(player.getUuid());
      if (trans != null && !trans.fox.isRemoved()) {
         if (player.isAlive() && player.getEntityWorld() == trans.world) {
            EpicVfx.implosion(
               trans.world, ParticleTypes.SOUL_FIRE_FLAME, trans.fox.getX(), trans.fox.getY() + trans.fox.getHeight() * 0.5, trans.fox.getZ(), 1.6, 18, 0.12
            );
         }

         trans.fox.discard();
      }

      player.removeStatusEffect(StatusEffects.INVISIBILITY);
      if (trans != null && trans.oldInvisibility != null) {
         player.addStatusEffect(trans.oldInvisibility);
      }

      player.removeStatusEffect(StatusEffects.SPEED);
      if (trans != null && trans.oldSpeed != null) {
         player.addStatusEffect(trans.oldSpeed);
      }

      this.transformationCooldowns.put(player.getUuid(), Math.max(0, YPConfig.getInstance().kitsune_transformation_cooldown_ticks));
      player.sendMessage(Text.literal(message), true);
   }

   private void applyFoxfire(ServerPlayerEntity owner, LivingEntity target) {
      ServerWorld active = owner.getEntityWorld();
      if (active instanceof ServerWorld && PowerCombatUtil.canHarm(owner, target)) {
         Map<UUID, KitsunePower.Foxfire> activex = this.foxfireByOwner.computeIfAbsent(owner.getUuid(), ignored -> new HashMap<>());
         int duration = Math.max(1, YPConfig.getInstance().kitsune_foxfire_duration_ticks);
         activex.put(target.getUuid(), new KitsunePower.Foxfire(owner.getUuid(), active, duration));
         target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, duration, 0, false, false));
         active.spawnParticles(
            ParticleTypes.SOUL_FIRE_FLAME, true, true,
            target.getX(),
            target.getBodyY(0.5),
            target.getZ(),
            30,
            target.getWidth() * 0.6,
            target.getHeight() * 0.5,
            target.getWidth() * 0.6,
            0.04
         );
      }
   }

   private void foxfireDetonation(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         ServerWorld world = id;
         UUID var23 = player.getUuid();
         int cd = this.detonationCooldowns.getOrDefault(var23, 0);
         if (cd > 0) {
            this.notice(player, "Foxfire Detonation", cd);
         } else {
            Map<UUID, KitsunePower.Foxfire> active = this.foxfireByOwner.get(var23);
            if (active != null && !active.isEmpty()) {
               AllianceManager allies = AllianceManager.get(id.getServer());
               Set<UUID> processed = new HashSet<>();
               Set<UUID> damagedVictims = new HashSet<>();
               double radius = Math.max(0.0, YPConfig.getInstance().kitsune_detonation_radius);
               int visualImpacts = 0;

               for (UUID targetId : new ArrayList<>(active.keySet())) {
                  if (processed.add(targetId) && world.getEntity(targetId) instanceof LivingEntity target && target.isAlive()) {
                     if (target instanceof ServerPlayerEntity p && allies.isAlly(player, p)) {
                        active.remove(targetId);
                     } else {
                        Box area = target.getBoundingBox().expand(radius);

                        for (LivingEntity near : world.getEntitiesByClass(LivingEntity.class, area, e -> PowerCombatUtil.canHarm(player, e))) {
                           if (!(near.getEntityPos().squaredDistanceTo(target.getEntityPos()) > radius * radius)
                              && damagedVictims.add(near.getUuid())) {
                              RitualManager.beginKillCredit(player, PowerType.KITSUNE, "kitsune_detonation_kills", near);

                              try {
                                 near.damage(world, world.getDamageSources().indirectMagic(player, player), (float)Math.max(0.0, YPConfig.getInstance().kitsune_detonation_damage));
                              } finally {
                                 RitualManager.endKillCredit(near);
                              }
                           }
                        }

                        if (visualImpacts < 8) {
                           world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                           EpicVfx.impact(world, target.getX(), target.getBodyY(0.5), target.getZ(), 1.1);
                           EpicVfx.ring(world, ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getBodyY(0.5), target.getZ(), 0.25, 0.08, 16, 0.03);
                           visualImpacts++;
                        }

                        target.removeStatusEffect(StatusEffects.GLOWING);
                        active.remove(targetId);
                     }
                  }
               }

               if (active.isEmpty()) {
                  this.foxfireByOwner.remove(var23);
               }

               EpicVfx.cue(world, player.getId(), "kitsune", "detonation", player.getX(), player.getBodyY(0.5), player.getZ(), Vec3d.ZERO, 2.0F, 20, 0.2F);
               this.detonationCooldowns.put(var23, Math.max(0, YPConfig.getInstance().kitsune_detonation_cooldown_ticks));
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.4F, 0.8F);
            } else {
               player.sendMessage(Text.literal("No targets are afflicted with your Foxfire."), true);
            }
         }
      }
   }

   @Override
   public void onAttack(ServerPlayerEntity attacker, Entity target) {
      if (target instanceof LivingEntity living && PowerCombatUtil.canHarm(attacker, target)) {
         this.applyFoxfire(attacker, living);
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      decrement(this.transformationCooldowns, id);
      decrement(this.clawCooldowns, id);
      decrement(this.burstCooldowns, id);
      decrement(this.pounceCooldowns, id);
      decrement(this.detonationCooldowns, id);
      boolean grace = player.getEntityWorld().getServer() != null && GraceManager.isActive(player.getEntityWorld().getServer());
      KitsunePower.Transformation trans = this.transformations.get(id);
      if (trans != null) {
         if (!grace && player.isAlive() && player.getEntityWorld() == trans.world && !trans.fox.isRemoved() && trans.ticks-- > 0) {
            Vec3d now = player.getEntityPos();
            Vec3d lead = trans.last == null ? Vec3d.ZERO : now.subtract(trans.last).multiply(2.5);
            if (lead.lengthSquared() > 9.0) {
               lead = Vec3d.ZERO;
            }

            trans.last = now;
            trans.fox.teleport(trans.world, now.x + lead.x, now.y + lead.y, now.z + lead.z, Set.of(), player.getYaw(), player.getPitch(), false);
            trans.fox.setHeadYaw(player.getHeadYaw());
            trans.fox.setBodyYaw(player.getBodyYaw());
            trans.fox.setVelocity(lead.multiply(0.4));
            if (player.age % 4 == 0) {
               trans.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, player.getX(), player.getY() + 0.2, player.getZ(), 3, 0.4, 0.1, 0.4, 0.02);
               trans.world
                  .spawnParticles(
                     DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), true, true,
                     player.getX(),
                     player.getY() + 0.3,
                     player.getZ(),
                     1,
                     0.3,
                     0.1,
                     0.3,
                     0.01
                  );
            }

            if (player.getVelocity().horizontalLengthSquared() > 0.04) {
               trans.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, player.getX(), player.getY() + 0.05, player.getZ(), 4, 0.35, 0.05, 0.35, 0.015);
            }

            this.tickCarryAndTunnel(player, trans.world);
         } else {
            this.endTransformation(player, "Kitsune transformation ended.");
         }
      }

      Map<UUID, KitsunePower.Foxfire> active = this.foxfireByOwner.get(id);
      if (active != null) {
         ServerWorld it = player.getEntityWorld();
         if (it instanceof ServerWorld) {
            ServerWorld world = it;
            Iterator<Entry<UUID, KitsunePower.Foxfire>> itx = active.entrySet().iterator();

            while (itx.hasNext()) {
               Entry<UUID, KitsunePower.Foxfire> entry = itx.next();
               KitsunePower.Foxfire f = entry.getValue();
               Entity target = world.getEntity(entry.getKey());
               if (target != null && target.isAlive() && f.world == world && --f.ticks > 0) {
                  if (target instanceof LivingEntity living) {
                     if (!grace && --f.damageClock <= 0) {
                        f.damageClock = 20;
                        if (PowerCombatUtil.canHarm(player, living)) {
                           living.damage(world, world.getDamageSources().indirectMagic(player, player), (float)Math.max(0.0, YPConfig.getInstance().kitsune_foxfire_damage_per_second));
                        }
                     }

                     if (player.age % 4 == 0) {
                        world.spawnParticles(
                           ParticleTypes.SOUL_FIRE_FLAME, true, true,
                           target.getX(),
                           target.getBodyY(0.5),
                           target.getZ(),
                           3,
                           target.getWidth() * 0.55,
                           target.getHeight() * 0.5,
                           target.getWidth() * 0.55,
                           0.03
                        );
                     }

                     if (player.age % 12 == 0) {
                        world.spawnParticles(
                           ParticleTypes.SOUL, true, true,
                           target.getX(),
                           target.getBodyY(0.7),
                           target.getZ(),
                           1,
                           target.getWidth() * 0.4,
                           target.getHeight() * 0.4,
                           target.getWidth() * 0.4,
                           0.02
                        );
                     }
                  }
               } else {
                  if (target instanceof LivingEntity ended) {
                     ended.removeStatusEffect(StatusEffects.GLOWING);
                  }

                  itx.remove();
               }
            }

            if (active.isEmpty()) {
               this.foxfireByOwner.remove(id);
            }
         }
      }
   }

   public boolean tryFoxPlaceBlock(ServerPlayerEntity player) {
      if (this.transformations.containsKey(player.getUuid())) {
         ServerWorld stack = player.getEntityWorld();
         if (stack instanceof ServerWorld) {
            ItemStack stackx = player.getMainHandStack();
            if (!(stackx.getItem() instanceof BlockItem)) {
               return false;
            }

            double range = Math.max(5.0, YPConfig.getInstance().kitsune_fox_place_range);
            Vec3d start = player.getEyePos();
            Vec3d end = start.add(PowerCombatUtil.safeLook(player).multiply(range));
            BlockHitResult hit = stack.raycast(new RaycastContext(start, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
            if (hit.getType() != Type.BLOCK) {
               return false;
            }

            ActionResult result = player.interactionManager.interactBlock(player, stack, stackx, Hand.MAIN_HAND, hit);
            if (!result.isAccepted()) {
               return false;
            }

            player.swingHand(Hand.MAIN_HAND, true);
            EpicVfx.beam(stack, ParticleTypes.SOUL_FIRE_FLAME, start.add(0.0, -0.3, 0.0), hit.getPos(), 0.6);
            stack.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, hit.getPos().x, hit.getPos().y, hit.getPos().z, 8, 0.2, 0.2, 0.2, 0.02);
            return true;
         }
      }

      return false;
   }

   private void notice(ServerPlayerEntity player, String name, int ticks) {
      player.sendMessage(Text.literal(name + " cooldown: " + (ticks + 19) / 20 + "s").formatted(Formatting.YELLOW), true);
   }

   private static void decrement(Map<UUID, Integer> map, UUID id) {
      int n = map.getOrDefault(id, 0);
      if (n > 0) {
         map.put(id, n - 1);
      }
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.cleanup(player, false);
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.cleanup(player, true);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.cleanup(player, false);
   }

   public static void onAnyPlayerDisconnect(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null
         && PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.KITSUNE) instanceof KitsunePower kitsune) {
         Iterator<Entry<UUID, KitsunePower.CarriedPassenger>> it = kitsune.carriedPassengers.entrySet().iterator();
         while (it.hasNext()) {
            Entry<UUID, KitsunePower.CarriedPassenger> entry = it.next();
            KitsunePower.CarriedPassenger carried = entry.getValue();
            if (carried.entity == player) {
               if (carried.scruffCarry) {
                  player.setNoGravity(carried.previousNoGravity);
               } else {
                  player.stopRiding();
               }
               it.remove();
            }
         }
      }
   }

   public static void shutdown(MinecraftServer server) {
      if (server != null && PowerManager.get(server).getPowerInstance(PowerType.KITSUNE) instanceof KitsunePower kitsune) {
         for (KitsunePower.CarriedPassenger carried : kitsune.carriedPassengers.values()) {
            if (carried.entity != null && !carried.entity.isRemoved()) {
               if (carried.scruffCarry) {
                  carried.entity.setNoGravity(carried.previousNoGravity);
               } else {
                  carried.entity.stopRiding();
               }
            }
         }
         kitsune.carriedPassengers.clear();

         for (Entry<UUID, KitsunePower.Transformation> entry : new ArrayList<>(kitsune.transformations.entrySet())) {
            KitsunePower.Transformation trans = entry.getValue();
            if (trans.fox != null && !trans.fox.isRemoved()) {
               trans.fox.discard();
            }
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
            if (owner != null) {
               resetView(owner);
               owner.removeStatusEffect(StatusEffects.INVISIBILITY);
               if (trans.oldInvisibility != null && owner.isAlive()) {
                  owner.addStatusEffect(trans.oldInvisibility);
               }
               owner.removeStatusEffect(StatusEffects.SPEED);
               if (trans.oldSpeed != null && owner.isAlive()) {
                  owner.addStatusEffect(trans.oldSpeed);
               }
            }
         }
         kitsune.transformations.clear();
         kitsune.foxfireByOwner.clear();
      }
   }

   private void cleanup(ServerPlayerEntity player, boolean clearCooldowns) {
      UUID id = player.getUuid();
      KitsunePower.CarriedPassenger carried = this.carriedPassengers.get(id);
      if (carried != null) {
         this.kickOffPassenger(player, carried, false);
      }

      KitsunePower.Transformation trans = this.transformations.remove(id);
      if (trans != null) {
         trans.fox.discard();
         resetView(player);
         player.removeStatusEffect(StatusEffects.INVISIBILITY);
         if (trans.oldInvisibility != null && player.isAlive()) {
            player.addStatusEffect(trans.oldInvisibility);
         }
         player.removeStatusEffect(StatusEffects.SPEED);
         if (trans.oldSpeed != null && player.isAlive()) {
            player.addStatusEffect(trans.oldSpeed);
         }
      }

      Map<UUID, KitsunePower.Foxfire> effects = this.foxfireByOwner.remove(id);
      if (effects != null) {
         effects.clear();
      }

      this.carriedPassengers.remove(id);
      if (clearCooldowns) {
         this.clearCooldowns(id);
      }
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.transformationCooldowns.remove(playerId);
      this.clawCooldowns.remove(playerId);
      this.burstCooldowns.remove(playerId);
      this.pounceCooldowns.remove(playerId);
      this.detonationCooldowns.remove(playerId);
   }

   private static final class CarriedPassenger {
      final Entity entity;
      final boolean scruffCarry;
      final boolean previousNoGravity;
      int damageClock = 20;

      CarriedPassenger(Entity entity, boolean scruffCarry, boolean previousNoGravity) {
         this.entity = entity;
         this.scruffCarry = scruffCarry;
         this.previousNoGravity = previousNoGravity;
      }
   }

   private static final class Foxfire {
      final UUID owner;
      final ServerWorld world;
      int ticks;
      int damageClock;

      Foxfire(UUID owner, ServerWorld world, int ticks) {
         this.owner = owner;
         this.world = world;
         this.ticks = ticks;
      }
   }

   @Override
   public String hudStatusChip(ServerPlayerEntity player) {
      return this.transformations.containsKey(player.getUuid()) ? "GIANT FOX" : "";
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      YPConfig cfg = YPConfig.getInstance();
      UUID id = player.getUuid();
      boolean fox = this.transformations.containsKey(id);
      if (fox) {
         return List.of(
            HudPill.ability(AbilityKey.PRIMARY, "Claw", this.clawCooldowns.getOrDefault(id, 0), cfg.kitsune_claw_cooldown_ticks, energy >= cfg.kitsune_claw_energy_cost),
            HudPill.ability(AbilityKey.SECONDARY, "Foxfire", this.burstCooldowns.getOrDefault(id, 0), cfg.kitsune_burst_cooldown_ticks, energy >= cfg.kitsune_burst_energy_cost),
            HudPill.ability(AbilityKey.TERTIARY, "Pounce", this.pounceCooldowns.getOrDefault(id, 0), cfg.kitsune_pounce_cooldown_ticks, energy >= cfg.kitsune_pounce_energy_cost),
            HudPill.ability(AbilityKey.QUATERNARY, "Detonate", this.detonationCooldowns.getOrDefault(id, 0), cfg.kitsune_detonation_cooldown_ticks, energy >= cfg.kitsune_detonation_energy_cost),
            HudPill.toggle(AbilityKey.ALTERNATE, "Fox", true)
         );
      }
      return List.of(
         HudPill.ability(AbilityKey.QUATERNARY, "Detonate", this.detonationCooldowns.getOrDefault(id, 0), cfg.kitsune_detonation_cooldown_ticks, energy >= cfg.kitsune_detonation_energy_cost),
         HudPill.ability(AbilityKey.ALTERNATE, "Giant Fox", this.transformationCooldowns.getOrDefault(id, 0), cfg.kitsune_transformation_cooldown_ticks, true)
      );
   }

   private static final class Transformation {
      final ServerWorld world;
      final FoxEntity fox;
      int ticks;
      Vec3d last;
      final StatusEffectInstance oldInvisibility;
      final StatusEffectInstance oldSpeed;

      Transformation(ServerWorld world, FoxEntity fox, int ticks, StatusEffectInstance oldInvisibility, StatusEffectInstance oldSpeed) {
         this.world = world;
         this.fox = fox;
         this.ticks = ticks;
         this.oldInvisibility = oldInvisibility;
         this.oldSpeed = oldSpeed;
      }
   }
}
