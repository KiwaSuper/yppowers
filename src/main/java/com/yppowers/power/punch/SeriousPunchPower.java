package com.yppowers.power.punch;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import java.util.List;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import com.yppowers.power.timewarper.TimeWarperPower;
import com.yppowers.power.iceemperor.IceEmperorPower;
import com.yppowers.system.EpicVfx;
import com.yppowers.power.thor.ThorPower;
import com.yppowers.system.PowerCombatUtil;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.progression.RitualManager;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
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

public class SeriousPunchPower extends Power {
   private final Map<UUID, Integer> cooldowns = new HashMap<>();
   private final Map<UUID, Integer> momentum = new HashMap<>();
   private final Map<UUID, Integer> momentumDecay = new HashMap<>();
   private final Map<UUID, UUID> momentumTarget = new HashMap<>();
   private final Map<UUID, Integer> hellDiveCooldowns = new HashMap<>();
   private final Map<UUID, SeriousPunchPower.HellDiveState> hellDives = new HashMap<>();
   private static final Map<Entity, SeriousPunchPower.FrozenEntity> FROZEN_ENTITIES = new IdentityHashMap<>();
   private static long lastFreezeTick = Long.MIN_VALUE;

   @Override
   public PowerType getType() {
      return PowerType.PUNCH;
   }

   public static boolean isEntityFrozen(Entity entity) {
      return entity != null && FROZEN_ENTITIES.containsKey(entity);
   }

   public static Vec3d getFrozenVelocity(Entity entity) {
      SeriousPunchPower.FrozenEntity frozen = entity == null ? null : FROZEN_ENTITIES.get(entity);
      return frozen == null ? null : frozen.velocity;
   }

   public static void shutdown(MinecraftServer server) {
      for (Entry<Entity, SeriousPunchPower.FrozenEntity> entry : FROZEN_ENTITIES.entrySet()) {
         Entity entity = entry.getKey();
         if (entity != null && entity.isAlive() && !entity.isRemoved()) {
            entity.setVelocity(entry.getValue().velocity);
            ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
         }
      }
      FROZEN_ENTITIES.clear();
      lastFreezeTick = Long.MIN_VALUE;
      if (server != null && PowerManager.get(server).getPowerInstance(PowerType.PUNCH) instanceof SeriousPunchPower punch) {
         punch.hellDives.clear();
      }
   }

   public static void serverTick(MinecraftServer server) {
      if (PowerManager.get(server).getPowerInstance(PowerType.PUNCH) instanceof SeriousPunchPower punch) {
         punch.tickFrozenEntities(server);
      }
   }

   private void tickFrozenEntities(MinecraftServer server) {
      long currentTick = server.getTicks();
      if (currentTick != lastFreezeTick) {
         lastFreezeTick = currentTick;
         Iterator<Entry<Entity, SeriousPunchPower.FrozenEntity>> iterator = FROZEN_ENTITIES.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<Entity, SeriousPunchPower.FrozenEntity> entry = iterator.next();
            Entity entity = entry.getKey();
            SeriousPunchPower.FrozenEntity freeze = entry.getValue();
            if (entity.isRemoved() || !entity.isAlive()) {
               iterator.remove();
            } else if (currentTick >= freeze.expiresAt) {
               iterator.remove();
               if (!TimeWarperPower.isEntityFrozen(entity) && !IceEmperorPower.isEntityFrozen(entity)) {
                  entity.setVelocity(freeze.velocity);
                  ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
               }
            } else {
               entity.setPosition(freeze.position);
               entity.setVelocity(Vec3d.ZERO);
               entity.setYaw(freeze.yaw);
               entity.setPitch(freeze.pitch);
               ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
            }
         }
      }
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      int cooldown = this.cooldowns.getOrDefault(uuid, 0);
      if (cooldown > 0) {
         player.sendMessage(Text.literal("Serious Punch on cooldown! " + (cooldown + 19) / 20 + "s").formatted(Formatting.RED), true);
      } else {
         this.cooldowns.put(uuid, Math.max(0, YPConfig.getInstance().serious_punch_cooldown_ticks));
         ServerWorld world = player.getEntityWorld();
         Vec3d lookDir = player.getRotationVec(1.0F).normalize();
         Vec3d start = player.getEyePos();
         EpicVfx.cue(world, player.getId(), "serious_punch", "beam", start.x, start.y, start.z, lookDir, 8.0F, 10, 0.1F);
         EpicVfx.impact(world, start.x, start.y, start.z, 1.8);
         int range = Math.max(1, (int)Math.round(YPConfig.getInstance().punch_raycast_range));
         List<Vec3d> punchDirections = List.of(rotateAroundY(lookDir, -30.0), lookDir, rotateAroundY(lookDir, 30.0));

         for (Vec3d direction : punchDirections) {
            destroyPunchTunnel(player, world, start, direction, range);
         }

         Box box = player.getBoundingBox().expand(25.0);
         long expiresAt = player.getEntityWorld().getServer().getTicks() + 30L;

         for (Entity entity : world.getOtherEntities(player, box, e -> PowerCombatUtil.canHarm(player, e))) {
            if (!(player.squaredDistanceTo(entity) > 625.0)) {
               Vec3d targetCenter = entity.getBoundingBox().getCenter();
               boolean struckByFan = false;

               for (Vec3d direction : punchDirections) {
                  double along = targetCenter.subtract(start).dotProduct(direction);
                  if (!(along < 0.0) && !(along > 25.0)) {
                     Vec3d closestPoint = start.add(direction.multiply(along));
                     if (closestPoint.squaredDistanceTo(targetCenter) <= 4.0) {
                        struckByFan = true;
                        break;
                     }
                  }
               }

               if (struckByFan) {
                  RitualManager.beginKillCredit(player, PowerType.PUNCH, "serious_punch_kills", entity);
                  boolean damaged;

                  try {
                     damaged = entity.damage(world, world.getDamageSources().playerAttack(player), 13.0F);
                  } finally {
                     RitualManager.endKillCredit(entity);
                  }

                  if (damaged && entity.isAlive() && !entity.isRemoved()) {
                     if (entity instanceof LivingEntity living) {
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 1));
                        living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 127, false, false, false));
                     }

                     FROZEN_ENTITIES.compute(entity, (target, existing) -> {
                        if (existing == null) {
                           return new SeriousPunchPower.FrozenEntity(target, expiresAt);
                        } else {
                           existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
                           return (SeriousPunchPower.FrozenEntity)existing;
                        }
                     });
                  }
               }
            }
         }

         player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 2));
         world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.0F, 1.0F);
      }
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      int cooldown = this.hellDiveCooldowns.getOrDefault(id, 0);
      if (cooldown > 0) {
         player.sendMessage(Text.literal("HELLDIVE cooldown: " + (cooldown + 19) / 20 + "s").formatted(Formatting.RED), true);
      } else if (this.hellDives.containsKey(id)) {
         player.sendMessage(Text.literal("HELLDIVE is already in progress."), true);
      } else {
         Vec3d look = player.getRotationVec(1.0F).normalize();
         player.setVelocity(-look.x * 0.45, 1.15, -look.z * 0.45);
         ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
         this.hellDives.put(id, new SeriousPunchPower.HellDiveState());
         this.hellDiveCooldowns.put(id, Math.max(0, YPConfig.getInstance().serious_punch_helldive_cooldown_ticks));
         ServerWorld var6 = player.getEntityWorld();
         if (var6 instanceof ServerWorld) {
            EpicVfx.cue(var6, player.getId(), "serious_punch", "windup", player.getX(), player.getY(), player.getZ(), look, 1.0F, 12, 0.0F);
            EpicVfx.activation(var6, player.getX(), player.getY(), player.getZ(), 1.0);
         }

         player.sendMessage(Text.literal("HELLDIVE — ascent!").formatted(Formatting.RED, Formatting.BOLD), true);
         player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2F, 0.7F);
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
      if (source.isOf(DamageTypes.PLAYER_ATTACK)
         && target instanceof LivingEntity
         && source.getAttacker() == player
         && player.getMainHandStack().isEmpty()
         && player.getOffHandStack().isEmpty()) {
         int stacks = Math.max(0, Math.min(Math.max(0, YPConfig.getInstance().serious_punch_momentum_max), this.momentum.getOrDefault(player.getUuid(), 0)));
         double perStack = Math.max(0.0, YPConfig.getInstance().serious_punch_momentum_damage_per_stack);
         return (float)(amount * (1.0 + stacks * perStack));
      } else {
         return amount;
      }
   }

   @Override
   public void onAttack(ServerPlayerEntity attacker, Entity target) {
      if (target instanceof LivingEntity
         && attacker.getMainHandStack().isEmpty()
         && attacker.getOffHandStack().isEmpty()
         && PowerCombatUtil.canHarm(attacker, target)) {
         if (target.getEntityWorld() instanceof ServerWorld world) {
            Vec3d hitCenter = target.getBoundingBox().getCenter();
            EpicVfx.impact(world, hitCenter.x, hitCenter.y, hitCenter.z, 0.7);
         }

         UUID owner = attacker.getUuid();
         UUID oldTarget = this.momentumTarget.get(owner);
         int current = this.momentum.getOrDefault(owner, 0);
         if (oldTarget != null && !oldTarget.equals(target.getUuid())) {
            current = 0;
         }

         int maximum = Math.max(0, YPConfig.getInstance().serious_punch_momentum_max);
         int gain = Math.max(1, YPConfig.getInstance().serious_punch_momentum_gain_per_hit);
         current = Math.min(maximum, current + gain);
         this.momentum.put(owner, current);
         this.momentumTarget.put(owner, target.getUuid());
         this.momentumDecay.put(owner, Math.max(1, YPConfig.getInstance().serious_punch_momentum_decay_ticks));
         attacker.sendMessage(Text.literal("Momentum: " + current + "/" + maximum).formatted(Formatting.GOLD, Formatting.BOLD), true);
      }
   }

   private void tickHellDive(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      SeriousPunchPower.HellDiveState state = this.hellDives.get(id);
      if (state != null) {
         if (!player.isAlive() || GraceManager.isActive(player.getEntityWorld().getServer())) {
            this.hellDives.remove(id);
            return;
         }
         if (--state.ticks <= 0 && !state.descending) {
            state.descending = true;
            state.ticks = 80;
         }

         if (state.descending) {
            Vec3d v = player.getVelocity();
            player.setVelocity(v.x * 0.65, -2.2, v.z * 0.65);
            ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
            if (player.isOnGround() || state.ticks <= 0) {
               this.performHellDiveImpact(player);
               this.hellDives.remove(id);
            }
         }
      }
   }

   private void performHellDiveImpact(ServerPlayerEntity player) {
      ServerWorld cfg = player.getEntityWorld();
      if (cfg instanceof ServerWorld) {
         ServerWorld world = cfg;
         if (player.getEntityWorld().getServer() != null) {
            YPConfig cfgx = YPConfig.getInstance();
            double radius = Math.max(1.0, cfgx.serious_punch_helldive_radius);
            BlockPos center = player.getBlockPos();
            cfg.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, player.getX(), player.getY(), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            cfg.spawnParticles(ParticleTypes.SONIC_BOOM, true, true, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            EpicVfx.cue(cfg, player.getId(), "serious_punch", "impact", player.getX(), player.getY(), player.getZ(), Vec3d.ZERO, 2.0F, 16, 0.35F);
            EpicVfx.impact(cfg, player.getX(), player.getY(), player.getZ(), 2.0);
            cfg.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.2F, 0.55F);
            AllianceManager alliances = AllianceManager.get(cfg.getServer());
            Box area = player.getBoundingBox().expand(radius, 2.0, radius);

            for (Entity entity : cfg.getOtherEntities(player, area, e -> PowerCombatUtil.canHarm(player, e))) {
               if (!(entity.squaredDistanceTo(player) > radius * radius)) {
                  entity.damage(world, world.getDamageSources().playerAttack(player), (float)Math.max(0.0, cfgx.serious_punch_helldive_damage));
                  Vec3d push = entity.getEntityPos().subtract(player.getEntityPos()).normalize().multiply(1.1).add(0.0, 0.65, 0.0);
                  entity.addVelocity(push);
                  ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
               }
            }

            int craterRadius = Math.max(0, cfgx.serious_punch_helldive_crater_radius);
            int depth = Math.max(0, cfgx.serious_punch_helldive_crater_depth);

            for (int dx = -craterRadius; dx <= craterRadius; dx++) {
               for (int dz = -craterRadius; dz <= craterRadius; dz++) {
                  if (dx * dx + dz * dz <= craterRadius * craterRadius) {
                     for (int dy = 0; dy < depth; dy++) {
                        BlockPos pos = center.add(dx, -dy, dz);
                        if (!world.isInBuildLimit(pos)
                           || !world.getWorldBorder().contains(pos)
                           || !TimeWarperDomainManager.mayModifyBlock(player, pos)) {
                           continue;
                        }
                        BlockState block = world.getBlockState(pos);
                        if (!block.isAir()
                           && world.getBlockEntity(pos) == null
                           && !block.isOf(Blocks.BEDROCK)
                           && block.getHardness(world, pos) >= 0.0F) {
                           world.breakBlock(pos, false);
                        }
                     }
                  }
               }
            }

            world.spawnParticles(ParticleTypes.CRIT, true, true, player.getX(), player.getY(), player.getZ(), 80, radius / 2.0, 0.3, radius / 2.0, 0.15);
            player.sendMessage(
               Text.literal("HELLDIVE impact! Momentum: " + this.momentum.getOrDefault(player.getUuid(), 0)).formatted(Formatting.RED, Formatting.BOLD), true
            );
            return;
         }
      }
   }

   private static Vec3d rotateAroundY(Vec3d direction, double degrees) {
      double radians = Math.toRadians(degrees);
      double cos = Math.cos(radians);
      double sin = Math.sin(radians);
      return new Vec3d(direction.x * cos - direction.z * sin, direction.y, direction.x * sin + direction.z * cos).normalize();
   }

   private static void destroyPunchTunnel(ServerPlayerEntity player, ServerWorld world, Vec3d start, Vec3d direction, int range) {
      Vec3d up = new Vec3d(0.0, 1.0, 0.0);
      Vec3d right = direction.crossProduct(up).normalize();
      if (right.lengthSquared() < 0.1) {
         up = new Vec3d(1.0, 0.0, 0.0);
         right = direction.crossProduct(up).normalize();
      }

      Vec3d realUp = right.crossProduct(direction).normalize();
      EpicVfx.beam(world, ParticleTypes.END_ROD, start, start.add(direction.multiply(range)), 0.5);

      for (int i = 0; i < range; i++) {
         Vec3d center = start.add(direction.multiply(i));
         if (i % 2 == 0) {
            world.spawnParticles(ParticleTypes.SONIC_BOOM, true, true, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
         }

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               BlockPos blockPos = BlockPos.ofFloored(center.add(right.multiply(dx)).add(realUp.multiply(dy)));
               if (!world.isInBuildLimit(blockPos)
                  || !world.getWorldBorder().contains(blockPos)
                  || !TimeWarperDomainManager.mayModifyBlock(player, blockPos)) {
                  continue;
               }
               BlockState state = world.getBlockState(blockPos);
               if (!state.isAir()
                  && world.getBlockEntity(blockPos) == null
                  && !state.isOf(Blocks.BEDROCK)
                  && state.getHardness(world, blockPos) >= 0.0F) {
                  world.breakBlock(blockPos, false);
               }
            }
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      decrement(this.cooldowns, uuid);
      decrement(this.hellDiveCooldowns, uuid);
      int decay = this.momentumDecay.getOrDefault(uuid, 0);
      if (decay > 0) {
         this.momentumDecay.put(uuid, decay - 1);
      } else if (this.momentum.getOrDefault(uuid, 0) > 0) {
         int stacks = this.momentum.get(uuid) - 1;
         if (stacks <= 0) {
            this.momentum.remove(uuid);
            this.momentumTarget.remove(uuid);
            this.momentumDecay.remove(uuid);
         } else {
            this.momentum.put(uuid, stacks);
            this.momentumDecay.put(uuid, Math.max(1, YPConfig.getInstance().serious_punch_momentum_decay_ticks));
         }
      }

      this.tickHellDive(player);
   }

   private static void decrement(Map<UUID, Integer> map, UUID id) {
      int n = map.getOrDefault(id, 0);
      if (n > 0) {
         map.put(id, n - 1);
      }
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.hellDives.remove(player.getUuid());
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      this.momentum.remove(id);
      this.momentumDecay.remove(id);
      this.momentumTarget.remove(id);
      this.hellDives.remove(id);
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      this.cooldowns.remove(id);
      this.hellDiveCooldowns.remove(id);
      this.momentum.remove(id);
      this.momentumDecay.remove(id);
      this.momentumTarget.remove(id);
      this.hellDives.remove(id);
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.cooldowns.remove(playerId);
      this.hellDiveCooldowns.remove(playerId);
   }

   private static final class FrozenEntity {
      final Vec3d position;
      final Vec3d velocity;
      final float yaw;
      final float pitch;
      long expiresAt;

      FrozenEntity(Entity entity, long expiresAt) {
         this.position = entity.getEntityPos();
         Vec3d savedVelocity = TimeWarperPower.getFrozenVelocity(entity);
         if (savedVelocity == null) {
            savedVelocity = IceEmperorPower.getFrozenVelocity(entity);
         }

         this.velocity = savedVelocity == null ? entity.getVelocity() : savedVelocity;
         this.yaw = entity.getYaw();
         this.pitch = entity.getPitch();
         this.expiresAt = expiresAt;
      }
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      YPConfig cfg = YPConfig.getInstance();
      UUID id = player.getUuid();
      return List.of(
         HudPill.ability(AbilityKey.PRIMARY, "Shockwave", this.cooldowns.getOrDefault(id, 0), cfg.serious_punch_cooldown_ticks, energy >= cfg.energy_cost_primary),
         HudPill.ability(AbilityKey.SECONDARY, "HELLDIVE", this.hellDiveCooldowns.getOrDefault(id, 0), cfg.serious_punch_helldive_cooldown_ticks, energy >= cfg.energy_cost_secondary)
      );
   }

   private static final class HellDiveState {
      int ticks = 30;
      boolean descending;

      HellDiveState() {
      }
   }
}
