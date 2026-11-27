package com.yppowers.power.impl;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;

public class GoatPower extends Power {
   private static final Identifier HEALTH_ID = Identifier.of("yppowers", "goat_health");
   private static final Identifier KB_ID = Identifier.of("yppowers", "goat_knockback_resistance");
   private static final Identifier SPEED_ID = Identifier.of("yppowers", "goat_speed");
   private static final Identifier STEP_HEIGHT_ID = Identifier.of("yppowers", "goat_step_height");
   private static final Identifier OVERDRIVE_SPEED_ID = Identifier.of("yppowers", "goat_overdrive_speed");
   private static final Identifier OVERDRIVE_JUMP_ID = Identifier.of("yppowers", "goat_overdrive_jump");
   private static final Identifier OVERDRIVE_KB_ID = Identifier.of("yppowers", "goat_overdrive_knockback");
   private static final double MAX_SPEED_MOVEMENT_FRACTION = 0.5;
   private static final DustParticleEffect MAX_SPEED_DUST = new DustParticleEffect(15844367, 1.0F);
   private final Map<UUID, Integer> sprintTicks = new HashMap<>();
   private final Map<UUID, Integer> sprintStopTicks = new HashMap<>();
   private final Map<UUID, Integer> gapJumpCooldown = new HashMap<>();
   private final Map<UUID, Identifier> lastDimensions = new HashMap<>();
   private final Map<UUID, GoatPower.JumpBoostLease> jumpBoostLeases = new HashMap<>();
   private final Map<UUID, GoatPower.SavedOverdriveEffects> preOverdriveEffects = new HashMap<>();
   private final Map<UUID, Integer> overdriveElapsedTicks = new HashMap<>();
   private final Map<UUID, Integer> awakeningMaxSpeedTicks = new HashMap<>();
   private final Map<UUID, Integer> awakeningImpactCooldowns = new HashMap<>();
   private final Map<UUID, Vec3d> awakeningLastMaxSpeedDirections = new HashMap<>();
   private final Set<UUID> jumpFallProtection = new HashSet<>();
   private final Set<UUID> airborneAfterGoatJump = new HashSet<>();

   @Override
   public PowerType getType() {
      return PowerType.GOAT;
   }

   public static boolean isSpear(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         Identifier id = Registries.ITEM.getId(stack.getItem());
         return id != null && id.getPath().endsWith("_spear");
      } else {
         return false;
      }
   }

   public static boolean hasReachedMaxSpeed(ServerPlayerEntity player) {
      return player != null && player.getEntityWorld().getServer() != null
         ? PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GOAT) instanceof GoatPower goat && goat.atMaxSpeed(player)
         : false;
   }

   private boolean atMaxSpeed(ServerPlayerEntity player) {
      if (this.arePassivesEnabled(player) && player.isSprinting()) {
         YPConfig config = YPConfig.getInstance();
         if (!config.goat_sprint_enabled) {
            return false;
         } else {
            double start = Math.min(config.goat_sprint_start_multiplier, config.goat_max_speed);
            double max = Math.max(start, config.goat_max_speed);
            int ramp = Math.max(1, config.goat_sprint_acceleration_ticks_to_max);
            int ticks = this.sprintTicks.getOrDefault(player.getUuid(), 0);
            if (!(max <= start + 1.0E-6) && ticks < ramp) {
               return false;
            } else {
               Vec3d velocity = player.getVelocity();
               double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
               return horizontalSpeed >= Math.max(0.08, player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 0.5);
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.resetMovementState(player.getUuid());
      this.applyModifiers(player);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.resetMovementState(player.getUuid());
      this.applyModifiers(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      if (player.getEntityWorld().getServer() != null) {
         EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(oldPlayer);
      }

      this.clearFallTracking(player.getUuid());
      this.resetMovementState(player.getUuid());
      this.releaseJumpBoost(player);
      this.removeSprintModifier(player);
      this.applyModifiers(player);
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(player);
      }

      this.clearFallTracking(player.getUuid());
      this.resetMovementState(player.getUuid());
      this.releaseJumpBoost(player);
      this.removeSprintModifier(player);
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.clearFallTracking(player.getUuid());
      this.resetMovementState(player.getUuid());
      this.releaseJumpBoost(player);
      this.removeSprintModifier(player);
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         EnergyManager.get(player.getEntityWorld().getServer()).onPlayerDeath(player);
      }

      this.removeOverdrive(player);
      this.removeModifiers(player);
      this.resetMovementState(player.getUuid());
      this.clearFallTracking(player.getUuid());
   }

   private void resetMovementState(UUID id) {
      this.sprintTicks.remove(id);
      this.sprintStopTicks.remove(id);
      this.gapJumpCooldown.remove(id);
      this.lastDimensions.remove(id);
      this.awakeningMaxSpeedTicks.remove(id);
      this.awakeningImpactCooldowns.remove(id);
      this.awakeningLastMaxSpeedDirections.remove(id);
   }

   private void clearFallTracking(UUID id) {
      this.jumpFallProtection.remove(id);
      this.airborneAfterGoatJump.remove(id);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         boolean enabled = manager.togglePowerEnabled(player, PowerType.GOAT);
         this.clearFallTracking(player.getUuid());
         this.resetMovementState(player.getUuid());
         if (enabled) {
            this.applyModifiers(player);
            player.sendMessage(Text.literal("GOAT passives enabled.").formatted(Formatting.GREEN), true);
         } else {
            this.removePassiveModifiers(player);
            player.sendMessage(Text.literal("GOAT passives disabled. Press R to re-enable them.").formatted(Formatting.YELLOW), true);
         }
      }
   }

   private boolean arePassivesEnabled(ServerPlayerEntity player) {
      return player.getEntityWorld().getServer() == null ? false : PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.GOAT);
   }

   private void applyModifiers(ServerPlayerEntity player) {
      if (!this.arePassivesEnabled(player)) {
         this.removePassiveModifiers(player);
      } else {
         EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
         if (health != null && health.getModifier(HEALTH_ID) == null) {
            health.addPersistentModifier(new EntityAttributeModifier(HEALTH_ID, 10.0, Operation.ADD_VALUE));
         }

         EntityAttributeInstance kb = player.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
         if (kb != null && kb.getModifier(KB_ID) == null) {
            kb.addPersistentModifier(new EntityAttributeModifier(KB_ID, 0.2, Operation.ADD_VALUE));
         }

         EntityAttributeInstance stepHeight = player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
         if (stepHeight != null) {
            this.ensureAdd(player, EntityAttributes.STEP_HEIGHT, STEP_HEIGHT_ID, Math.max(0.0, 3.0 - stepHeight.getBaseValue()));
         }

         this.ensureJumpBoost(player);
         this.updateSpeed(player, player.isSprinting() ? this.sprintTicks.getOrDefault(player.getUuid(), 1) : 0);
         player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
      }
   }

   private void removeModifiers(ServerPlayerEntity player) {
      this.removePassiveModifiers(player);
   }

   private void removePassiveModifiers(ServerPlayerEntity player) {
      EntityAttributeInstance health = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
      if (health != null) {
         health.removeModifier(HEALTH_ID);
      }

      EntityAttributeInstance kb = player.getAttributeInstance(EntityAttributes.KNOCKBACK_RESISTANCE);
      if (kb != null) {
         kb.removeModifier(KB_ID);
      }

      EntityAttributeInstance stepHeight = player.getAttributeInstance(EntityAttributes.STEP_HEIGHT);
      if (stepHeight != null) {
         stepHeight.removeModifier(STEP_HEIGHT_ID);
      }

      this.updateSpeed(player, 0);
      this.releaseJumpBoost(player);
      player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         EnergyManager energy = EnergyManager.get(player.getEntityWorld().getServer());
         if (energy.getOverdriveTicks(player.getUuid()) > 0) {
            player.sendMessage(Text.literal("GOAT Overdrive is already active.").formatted(Formatting.YELLOW), true);
         } else if (energy.getGoatCooldownTicks(player.getUuid()) > 0) {
            player.sendMessage(
               Text.literal("GOAT Overdrive cooldown: " + (energy.getGoatCooldownTicks(player.getUuid()) + 19) / 20 + "s.").formatted(Formatting.YELLOW), true
            );
         } else {
            energy.startGoatOverdrive(player);
            player.sendMessage(Text.literal("GOAT OVERDRIVE! Move fast; deferred damage will return afterward.").formatted(Formatting.GOLD), true);
            ServerWorld var4 = player.getEntityWorld();
            if (var4 instanceof ServerWorld) {
               EpicVfx.activation(var4, player.getX(), player.getY(), player.getZ(), 1.25);
               EpicVfx.cue(var4, player.getId(), "goat", "overdrive", player.getX(), player.getY(), player.getZ(), player.getVelocity(), 1.2F, 24, 0.2F);
               var4.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_HORN_BREAK, SoundCategory.PLAYERS, 1.2F, 0.75F);
            }
         }
      }
   }

   public void applyOverdrive(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      this.preOverdriveEffects
         .computeIfAbsent(
            id,
            ignored -> new GoatPower.SavedOverdriveEffects(
               copyEffect(player.getStatusEffect(StatusEffects.STRENGTH)),
               copyEffect(player.getStatusEffect(StatusEffects.RESISTANCE)),
               copyEffect(player.getStatusEffect(StatusEffects.REGENERATION))
            )
         );
      this.overdriveElapsedTicks.putIfAbsent(id, 0);
      YPConfig config = YPConfig.getInstance();
      EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      EntityAttributeModifier goatSpeed = speed == null ? null : speed.getModifier(SPEED_ID);
      double goatSpeedAdd = goatSpeed == null ? 0.0 : goatSpeed.value();
      this.ensureMultiplier(player, EntityAttributes.MOVEMENT_SPEED, OVERDRIVE_SPEED_ID, config.goat_overdrive_movement_multiplier - 1.0 - goatSpeedAdd);
      this.ensureMultiplier(player, EntityAttributes.JUMP_STRENGTH, OVERDRIVE_JUMP_ID, config.goat_overdrive_jump_multiplier - 1.0);
      this.ensureAdd(player, EntityAttributes.KNOCKBACK_RESISTANCE, OVERDRIVE_KB_ID, config.goat_overdrive_knockback_resistance_bonus);
      this.refreshOverdriveEffects(player);
   }

   private static StatusEffectInstance copyEffect(StatusEffectInstance effect) {
      return effect == null ? null : new StatusEffectInstance(effect);
   }

   public void tickOverdrive(ServerPlayerEntity player) {
      this.applyOverdrive(player);
      this.overdriveElapsedTicks.merge(player.getUuid(), 1, Integer::sum);
      ServerWorld var3 = player.getEntityWorld();
      if (var3 instanceof ServerWorld && var3.getTime() % 4L == 0L) {
         var3.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, player.getX(), player.getY() + 0.8, player.getZ(), 3, 0.35, 0.5, 0.35, 0.04);
         var3.spawnParticles(ParticleTypes.POOF, true, true, player.getX(), player.getY(), player.getZ(), 1, 0.25, 0.05, 0.25, 0.01);
      }
   }

   private void ensureMultiplier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double amount) {
      EntityAttributeInstance instance = player.getAttributeInstance(attribute);
      if (instance != null) {
         EntityAttributeModifier modifier = instance.getModifier(id);
         if (modifier == null || Math.abs(modifier.value() - amount) > 1.0E-6) {
            if (modifier != null) {
               instance.removeModifier(id);
            }

            instance.addTemporaryModifier(new EntityAttributeModifier(id, amount, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }
   }

   private void ensureAdd(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double amount) {
      EntityAttributeInstance instance = player.getAttributeInstance(attribute);
      if (instance != null) {
         EntityAttributeModifier modifier = instance.getModifier(id);
         if (modifier == null || Math.abs(modifier.value() - amount) > 1.0E-6) {
            if (modifier != null) {
               instance.removeModifier(id);
            }

            instance.addTemporaryModifier(new EntityAttributeModifier(id, amount, Operation.ADD_VALUE));
         }
      }
   }

   private void refreshOverdriveEffects(ServerPlayerEntity player) {
      player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 5, 2, false, true, true));
      player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 5, 1, false, true, true));
      player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 5, 1, false, true, true));
   }

   public void removeOverdrive(ServerPlayerEntity player) {
      for (Object[] entry : new Object[][]{
         {EntityAttributes.MOVEMENT_SPEED, OVERDRIVE_SPEED_ID},
         {EntityAttributes.JUMP_STRENGTH, OVERDRIVE_JUMP_ID},
         {EntityAttributes.KNOCKBACK_RESISTANCE, OVERDRIVE_KB_ID}
      }) {
         RegistryEntry<EntityAttribute> attribute = (RegistryEntry<EntityAttribute>)entry[0];
         Identifier id = (Identifier)entry[1];
         EntityAttributeInstance instance = player.getAttributeInstance(attribute);
         if (instance != null) {
            instance.removeModifier(id);
         }
      }

      GoatPower.SavedOverdriveEffects saved = this.preOverdriveEffects.remove(player.getUuid());
      int elapsed = this.overdriveElapsedTicks.getOrDefault(player.getUuid(), 0);
      this.overdriveElapsedTicks.remove(player.getUuid());
      if (saved != null) {
         restoreEffect(player, StatusEffects.STRENGTH, saved.strength(), elapsed);
         restoreEffect(player, StatusEffects.RESISTANCE, saved.resistance(), elapsed);
         restoreEffect(player, StatusEffects.REGENERATION, saved.regeneration(), elapsed);
      }
   }

   private static void restoreEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effectType, StatusEffectInstance saved, int elapsedTicks) {
      if (saved != null) {
         player.removeStatusEffect(effectType);
         int originalDuration = saved.getDuration();
         int remaining = originalDuration < 0 ? originalDuration : originalDuration - Math.max(0, elapsedTicks);
         if (remaining != 0 && remaining >= -1) {
            player.addStatusEffect(
               new StatusEffectInstance(
                  saved.getEffectType(), remaining, saved.getAmplifier(), saved.isAmbient(), saved.shouldShowParticles(), saved.shouldShowIcon()
               )
            );
         }
      }
   }

   @Override
   public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
      return amount;
   }

   private void updateSpeed(ServerPlayerEntity player, int ticksSprinting) {
      EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.removeModifier(SPEED_ID);
         YPConfig config = YPConfig.getInstance();
         double goatSpeedModifier = 0.0;
         if (config.goat_sprint_enabled && player.isSprinting() && ticksSprinting > 0) {
            double start = Math.min(config.goat_sprint_start_multiplier, config.goat_max_speed);
            double max = Math.max(start, config.goat_max_speed);
            int accelerationTicks = Math.max(1, config.goat_sprint_acceleration_ticks_to_max);
            double progress = accelerationTicks <= 1 ? 1.0 : Math.min(1.0, (double)Math.max(0, ticksSprinting - 1) / (accelerationTicks - 1));
            double effectiveMultiplier = start + (max - start) * progress;
            goatSpeedModifier = effectiveMultiplier - 1.0;
            speed.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, goatSpeedModifier, Operation.ADD_MULTIPLIED_TOTAL));
         }

         boolean overdrive = player.getEntityWorld().getServer() != null
            && EnergyManager.get(player.getEntityWorld().getServer()).isOverdriveActive(player.getUuid());
         if (overdrive) {
            this.ensureMultiplier(
               player, EntityAttributes.MOVEMENT_SPEED, OVERDRIVE_SPEED_ID, config.goat_overdrive_movement_multiplier - 1.0 - goatSpeedModifier
            );
         } else {
            EntityAttributeInstance movement = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            if (movement != null) {
               movement.removeModifier(OVERDRIVE_SPEED_ID);
            }
         }
      }
   }

   private void ensureJumpBoost(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      int amplifier = YPConfig.getInstance().goat_jump_boost_amplifier;
      StatusEffectInstance current = player.getStatusEffect(StatusEffects.JUMP_BOOST);
      if (this.jumpBoostLeases.get(id) == null && current != null && current.getAmplifier() == 1 && current.getDuration() == -1 && amplifier < 1) {
         player.removeStatusEffect(StatusEffects.JUMP_BOOST);
         current = null;
      }

      GoatPower.JumpBoostLease lease = this.jumpBoostLeases.get(id);
      if (lease == null) {
         if (current != null && current.getAmplifier() >= amplifier) {
            return;
         }

         lease = new GoatPower.JumpBoostLease(current, amplifier);
         this.jumpBoostLeases.put(id, lease);
      }

      StatusEffectInstance latest = player.getStatusEffect(StatusEffects.JUMP_BOOST);
      if (latest == null || latest.getAmplifier() < lease.amplifier) {
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, -1, lease.amplifier, false, false, false));
      }

      lease.elapsedTicks++;
   }

   private void releaseJumpBoost(ServerPlayerEntity player) {
      GoatPower.JumpBoostLease lease = this.jumpBoostLeases.remove(player.getUuid());
      if (lease != null) {
         StatusEffectInstance current = player.getStatusEffect(StatusEffects.JUMP_BOOST);
         if (current == null || current.getAmplifier() == lease.amplifier && current.getDuration() == -1) {
            if (current != null) {
               player.removeStatusEffect(StatusEffects.JUMP_BOOST);
            }

            if (lease.previous != null) {
               int duration = lease.previous.getDuration();
               if (duration >= 0) {
                  duration -= lease.elapsedTicks;
               }

               if (duration > 0 || duration == -1) {
                  player.addStatusEffect(
                     new StatusEffectInstance(
                        lease.previous.getEffectType(),
                        duration,
                        lease.previous.getAmplifier(),
                        lease.previous.isAmbient(),
                        lease.previous.shouldShowParticles(),
                        lease.previous.shouldShowIcon()
                     )
                  );
               }
            }
         }
      }
   }

   private void removeSprintModifier(ServerPlayerEntity player) {
      EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.removeModifier(SPEED_ID);
      }
   }

   private static boolean hasGroundSupport(ServerPlayerEntity player, double x, double z, double feetY) {
      World world = player.getEntityWorld();
      int baseY = MathHelper.floor(feetY);

      for (int y = baseY - 1; y <= baseY; y++) {
         BlockPos pos = new BlockPos(MathHelper.floor(x), y, MathHelper.floor(z));
         VoxelShape shape = world.getBlockState(pos).getCollisionShape(world, pos);

         for (Box box : shape.getBoundingBoxes()) {
            double top = y + box.maxY;
            if (top >= feetY - 0.28
               && top <= feetY + 0.18
               && x >= pos.getX() + box.minX
               && x <= pos.getX() + box.maxX
               && z >= pos.getZ() + box.minZ
               && z <= pos.getZ() + box.maxZ) {
               return true;
            }
         }
      }

      return false;
   }

   private boolean trySprintGapJump(ServerPlayerEntity player, UUID id) {
      if (player.isSprinting() && player.isOnGround() && this.gapJumpCooldown.getOrDefault(id, 0) <= 0) {
         Vec3d velocity = player.getVelocity();
         double horizontalSpeed = Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
         if (horizontalSpeed < 0.12) {
            return false;
         } else {
            double dirX = velocity.x / horizontalSpeed;
            double dirZ = velocity.z / horizontalSpeed;
            double feetY = player.getY();
            double landingDistance = -1.0;

            for (double distance = 1.5; distance <= 3.0; distance += 0.25) {
               if (hasGroundSupport(player, player.getX() + dirX * distance, player.getZ() + dirZ * distance, feetY)) {
                  landingDistance = distance;
                  break;
               }
            }

            if (landingDistance < 0.0) {
               return false;
            } else {
               boolean unsupportedGap = false;

               for (double distancex = 0.5; distancex < landingDistance - 0.35; distancex += 0.25) {
                  if (!hasGroundSupport(player, player.getX() + dirX * distancex, player.getZ() + dirZ * distancex, feetY)) {
                     unsupportedGap = true;
                     break;
                  }
               }

               if (!unsupportedGap) {
                  return false;
               } else {
                  World world = player.getEntityWorld();
                  Box body = player.getBoundingBox();

                  for (double distancexx = 0.0; distancexx <= landingDistance; distancexx += 0.25) {
                     for (double height = 0.0; height <= 1.25; height += 0.25) {
                        Box swept = body.offset(dirX * distancexx, height, dirZ * distancexx);
                        if (!world.isSpaceEmpty(player, swept)) {
                           return false;
                        }
                     }
                  }

                  double leapSpeed = Math.max(horizontalSpeed, landingDistance / 6.0);
                  player.setVelocity(dirX * leapSpeed, Math.max(velocity.y, 0.42), dirZ * leapSpeed);
                  ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
                  this.gapJumpCooldown.put(id, 10);
                  ServerWorld var20 = player.getEntityWorld();
                  if (var20 instanceof ServerWorld) {
                     EpicVfx.impact(var20, player.getX(), player.getY(), player.getZ(), 0.55);
                     EpicVfx.cue(
                        var20, player.getId(), "goat", "trail", player.getX(), player.getY(), player.getZ(), new Vec3d(dirX, 0.0, dirZ), 0.8F, 14, 0.0F
                     );
                  }

                  return true;
               }
            }
         }
      } else {
         return false;
      }
   }

   private int tryAwakeningBlockImpact(ServerPlayerEntity player, UUID id, Vec3d direction) {
      ServerWorld config = player.getEntityWorld();
      if (config instanceof ServerWorld) {
         ServerWorld world = config;
         if (direction != null && this.arePassivesEnabled(player)) {
            YPConfig configx = YPConfig.getInstance();
            EnergyManager energy = EnergyManager.get(config.getServer());
            double cost = Math.max(0.0, configx.goat_awakening_energy_per_block);
            double maxHardness = Math.max(0.0, configx.goat_awakening_max_block_hardness);
            Vec3d forward = new Vec3d(direction.x, 0.0, direction.z).normalize();
            if (forward.lengthSquared() < 0.5) {
               return 0;
            }

            Vec3d side = new Vec3d(-forward.z, 0.0, forward.x);
            double halfWidth = Math.max(0.25, player.getWidth() * 0.5);
            Set<BlockPos> candidates = new LinkedHashSet<>();

            for (double distance : new double[]{0.12, 0.5, 0.95}) {
               for (double lateral : new double[]{-halfWidth * 0.65, 0.0, halfWidth * 0.65}) {
                  double x = player.getX() + forward.x * (halfWidth + distance) + side.x * lateral;
                  double z = player.getZ() + forward.z * (halfWidth + distance) + side.z * lateral;

                  for (double yOffset = 0.15; yOffset < player.getHeight() - 0.1; yOffset += 0.45) {
                     candidates.add(BlockPos.ofFloored(x, player.getY() + yOffset, z));
                  }
               }
            }

            int broken = 0;

            for (BlockPos pos : candidates) {
               if (broken >= 2) {
                  break;
               }

               BlockState state = world.getBlockState(pos);
               if (!state.isAir() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && !state.getCollisionShape(world, pos).isEmpty()) {
                  float hardness = state.getHardness(world, pos);
                  if (Float.isFinite(hardness)
                     && !(hardness < 0.0F)
                     && !(hardness > maxHardness)
                     && !state.isOf(Blocks.BEDROCK)
                     && !state.isOf(Blocks.BARRIER)
                     && !state.isOf(Blocks.STRUCTURE_VOID)
                     && !state.isOf(Blocks.END_PORTAL)
                     && !state.isOf(Blocks.END_GATEWAY)
                     && !state.isOf(Blocks.END_PORTAL_FRAME)) {
                     if (energy.getEnergy(id) + 1.0E-6 < cost) {
                        player.sendMessage(Text.literal("GOAT Awakening impact stopped: not enough Energy.").formatted(Formatting.YELLOW), true);
                        break;
                     }

                     double savedEnergy = energy.getEnergy(id);
                     if (!energy.trySpendAwakening(player, cost)) {
                        break;
                     }

                     boolean reportedBreak = player.interactionManager.tryBreakBlock(pos);
                     boolean actuallyBroke = reportedBreak && world.getBlockState(pos).isAir();
                     if (!actuallyBroke) {
                        energy.setEnergy(id, savedEnergy);
                     } else {
                        broken++;
                        world.spawnParticles(
                           new BlockStateParticleEffect(ParticleTypes.BLOCK, state), true, true,
                           pos.getX() + 0.5,
                           pos.getY() + 0.5,
                           pos.getZ() + 0.5,
                           8,
                           0.25,
                           0.25,
                           0.25,
                           0.12
                        );
                        world.playSound(null, pos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 0.8F, 0.8F);
                     }
                  }
               }
            }

            if (broken > 0) {
               EpicVfx.impact(
                  world,
                  player.getX() + forward.x * 0.7,
                  player.getY() + player.getHeight() * 0.5,
                  player.getZ() + forward.z * 0.7,
                  Math.min(1.0, 0.55 + 0.15 * broken)
               );
               EpicVfx.cue(
                  world,
                  player.getId(),
                  "goat",
                  "impact",
                  player.getX() + forward.x * 0.7,
                  player.getY() + player.getHeight() * 0.5,
                  player.getZ() + forward.z * 0.7,
                  forward,
                  Math.min(1.0F, 0.55F + 0.15F * broken),
                  12,
                  0.15F
               );
               player.sendMessage(Text.literal("GOAT IMPACT — " + broken + " block(s) shattered.").formatted(Formatting.GOLD, Formatting.BOLD), true);
            }

            return broken;
         }
      }

      return 0;
   }

   @Override
   public void onJump(ServerPlayerEntity player) {
      if (this.arePassivesEnabled(player)) {
         YPConfig config = YPConfig.getInstance();
         UUID id = player.getUuid();
         if (config.goat_jump_fall_protection_enabled) {
            this.jumpFallProtection.add(id);
            this.airborneAfterGoatJump.remove(id);
         }

         Vec3d velocity = player.getVelocity();
         double multiplier = Math.max(1.0, config.goat_jump_multiplier);
         player.setVelocity(velocity.x, Math.max(0.0, velocity.y) * multiplier, velocity.z);
         ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
         player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_LONG_JUMP, SoundCategory.PLAYERS, 1.0F, 1.0F);
         ServerWorld var8 = player.getEntityWorld();
         if (var8 instanceof ServerWorld) {
            EpicVfx.impact(var8, player.getX(), player.getY(), player.getZ(), 0.65);
            EpicVfx.cue(var8, player.getId(), "goat", "charge", player.getX(), player.getY(), player.getZ(), player.getVelocity(), 0.75F, 12, 0.0F);
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (player.getEntityWorld().getServer() != null) {
         if (!player.isAlive()) {
            this.clearFallTracking(player.getUuid());
            this.resetMovementState(player.getUuid());
            this.releaseJumpBoost(player);
            this.removeSprintModifier(player);
         } else if (!this.arePassivesEnabled(player)) {
            this.resetMovementState(uuid);
            this.removeSprintModifier(player);
            this.releaseJumpBoost(player);
         } else {
            Identifier dimension = player.getEntityWorld().getRegistryKey().getValue();
            Identifier previousDimension = this.lastDimensions.put(uuid, dimension);
            if (previousDimension != null && !previousDimension.equals(dimension)) {
               this.clearFallTracking(uuid);
               this.sprintTicks.remove(uuid);
               this.sprintStopTicks.remove(uuid);
               this.gapJumpCooldown.remove(uuid);
               this.awakeningMaxSpeedTicks.remove(uuid);
               this.awakeningImpactCooldowns.remove(uuid);
               this.awakeningLastMaxSpeedDirections.remove(uuid);
               this.removeSprintModifier(player);
            }

            this.ensureJumpBoost(player);
            int gapCooldown = this.gapJumpCooldown.getOrDefault(uuid, 0);
            if (gapCooldown > 1) {
               this.gapJumpCooldown.put(uuid, gapCooldown - 1);
            } else if (gapCooldown == 1) {
               this.gapJumpCooldown.remove(uuid);
            }

            if (this.jumpFallProtection.contains(uuid)) {
               if (!player.isOnGround() || player.getVelocity().y > 0.0) {
                  this.airborneAfterGoatJump.add(uuid);
               } else if (this.airborneAfterGoatJump.remove(uuid)) {
                  this.jumpFallProtection.remove(uuid);
               }
            }

            YPConfig config = YPConfig.getInstance();
            int ticks = this.sprintTicks.getOrDefault(uuid, 0);
            if (player.isSprinting() && config.goat_sprint_enabled) {
               this.sprintStopTicks.put(uuid, 0);
               if (player.isOnGround() || config.goat_sprint_preserve_acceleration_while_jumping) {
                  ticks++;
               }
            } else {
               int stopped = this.sprintStopTicks.getOrDefault(uuid, 0) + 1;
               this.sprintStopTicks.put(uuid, stopped);
               if (stopped >= Math.max(1, config.goat_sprint_reset_delay_ticks)) {
                  ticks = 0;
               }
            }

            this.sprintTicks.put(uuid, ticks);
            this.updateSpeed(player, ticks);
            if (config.goat_sprint_enabled) {
               this.trySprintGapJump(player, uuid);
            }

            if (player.isSprinting() && config.goat_sprint_enabled && ticks == 1 && config.goat_sprint_sounds_enabled) {
               ServerWorld var9 = player.getEntityWorld();
               if (var9 instanceof ServerWorld) {
                  var9.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GOAT_STEP, SoundCategory.PLAYERS, 0.45F, 1.1F);
               }
            }

            if (player.isSprinting() && config.goat_sprint_enabled && config.goat_sprint_particles_enabled) {
               ServerWorld var32 = player.getEntityWorld();
               if (var32 instanceof ServerWorld && var32.getTime() % 5L == 0L) {
                  var32.spawnParticles(ParticleTypes.POOF, true, true, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
               }
            }

            double startMultiplier = Math.min(config.goat_sprint_start_multiplier, config.goat_max_speed);
            double maxMultiplier = Math.max(startMultiplier, config.goat_max_speed);
            boolean capReached = maxMultiplier <= startMultiplier + 1.0E-6 || ticks >= Math.max(1, config.goat_sprint_acceleration_ticks_to_max);
            Vec3d velocity = player.getVelocity();
            double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
            double horizontalSpeed = Math.sqrt(horizontalSpeedSquared);
            double maxSpeedThreshold = Math.max(0.08, player.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) * 0.5);
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            boolean awakeningEnabled = config.goat_sprint_enabled
               && manager.isPowerEnabled(player, PowerType.GOAT)
               && AwakeningManager.isAwakened(manager, uuid, PowerType.GOAT)
               && !GraceManager.isActive(player.getEntityWorld().getServer());
            if (!awakeningEnabled) {
               this.awakeningMaxSpeedTicks.remove(uuid);
               this.awakeningImpactCooldowns.remove(uuid);
               this.awakeningLastMaxSpeedDirections.remove(uuid);
            } else {
               int impactCooldown = this.awakeningImpactCooldowns.getOrDefault(uuid, 0);
               if (impactCooldown > 1) {
                  this.awakeningImpactCooldowns.put(uuid, impactCooldown - 1);
               } else if (impactCooldown == 1) {
                  this.awakeningImpactCooldowns.remove(uuid);
               }

               boolean actuallyAtMaxSpeed = player.isSprinting() && capReached && horizontalSpeed >= maxSpeedThreshold;
               if (actuallyAtMaxSpeed) {
                  this.awakeningMaxSpeedTicks.merge(uuid, 1, (oldValue, one) -> Math.min(12000, oldValue + one));
                  this.awakeningLastMaxSpeedDirections.put(uuid, new Vec3d(velocity.x, 0.0, velocity.z).normalize());
               } else if (!player.horizontalCollision) {
                  this.awakeningMaxSpeedTicks.remove(uuid);
                  this.awakeningLastMaxSpeedDirections.remove(uuid);
               }

               int required = Math.max(1, config.goat_awakening_max_speed_ticks);
               if (player.horizontalCollision
                  && this.awakeningMaxSpeedTicks.getOrDefault(uuid, 0) >= required
                  && this.awakeningImpactCooldowns.getOrDefault(uuid, 0) <= 0) {
                  if (!AwakeningManager.isGoatRamConfirmed(player)) {
                     AwakeningManager.requestGoatRamConfirmation(player);
                  } else {
                     Vec3d runDirection = this.awakeningLastMaxSpeedDirections.get(uuid);
                     this.tryAwakeningBlockImpact(player, uuid, runDirection);
                  }

                  this.awakeningImpactCooldowns.put(uuid, Math.max(1, config.goat_awakening_break_interval_ticks));
               }
            }

            if (player.isSprinting() && config.goat_sprint_enabled && capReached && horizontalSpeed >= maxSpeedThreshold && player.age % 2 == 0) {
               ServerWorld var34 = player.getEntityWorld();
               if (var34 instanceof ServerWorld) {
                  double backX = -velocity.x / horizontalSpeed;
                  double backZ = -velocity.z / horizontalSpeed;
                  double trailX = player.getX() + backX * 0.85;
                  double trailZ = player.getZ() + backZ * 0.85;
                  var34.spawnParticles(MAX_SPEED_DUST, true, true, trailX, player.getY() + 0.18, trailZ, 3, 0.07, 0.05, 0.07, 0.005);
                  var34.spawnParticles(ParticleTypes.CLOUD, true, true, trailX, player.getY() + 0.12, trailZ, 1, 0.04, 0.025, 0.04, 0.005);
               }
            }
         }
      }
   }

   @Override
   public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) {
      if (!this.arePassivesEnabled(player)) {
         return damageMultiplier;
      } else {
         YPConfig config = YPConfig.getInstance();
         if (config.goat_jump_fall_protection_enabled && this.jumpFallProtection.contains(player.getUuid())) {
            return damageMultiplier * (float)config.goat_jump_fall_damage_multiplier;
         } else {
            return !config.goat_passive_fall_protection_enabled ? damageMultiplier : damageMultiplier * (float)config.goat_passive_fall_damage_multiplier;
         }
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
      if (!this.arePassivesEnabled(player)) {
         return amount;
      } else {
         return source.isOf(DamageTypes.PLAYER_ATTACK) ? amount * 0.9F : amount;
      }
   }

   private static final class JumpBoostLease {
      final StatusEffectInstance previous;
      final int amplifier;
      int elapsedTicks;

      JumpBoostLease(StatusEffectInstance previous, int amplifier) {
         this.previous = previous == null ? null : new StatusEffectInstance(previous);
         this.amplifier = amplifier;
      }
   }

   private record SavedOverdriveEffects(StatusEffectInstance strength, StatusEffectInstance resistance, StatusEffectInstance regeneration) {
   }
}
