package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;

public class PoseidonPower extends Power {
   private final Map<UUID, Integer> swapCooldowns = new HashMap<>();
   private final Map<UUID, Integer> tsunamiCooldowns = new HashMap<>();
   private final Map<UUID, PoseidonPower.TsunamiWaveState> activeTsunamiWaves = new HashMap<>();
   private final Set<UUID> highSpeedModeEnabled = new HashSet<>();
   private final Set<UUID> highSpeedModifierApplied = new HashSet<>();
   private static final int EFFECT_REFRESH_TICKS = 40;
   private final Map<UUID, Map<PoseidonPower.WaterEffect, PoseidonPower.EffectLease>> waterEffectLeases = new HashMap<>();
   private final Map<UUID, Map<PoseidonPower.LandEffect, PoseidonPower.EffectLease>> landEffectLeases = new HashMap<>();
   private static final Identifier LAND_SPEED_ID = Identifier.of("yppowers", "poseidon_land_weakness");
   private static final Identifier HIGH_SPEED_SWIM_ID = Identifier.of("yppowers", "poseidon_high_speed_swim");

   @Override
   public PowerType getType() {
      return PowerType.POSEIDON;
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      this.tickCooldown(this.swapCooldowns, uuid);
      this.tickCooldown(this.tsunamiCooldowns, uuid);
      this.tickTsunamiWave(player);
      this.advanceEffectLeases(uuid);
      if (!player.isAlive()) {
         this.cleanupAllManagedEffects(player);
         this.removeLandPenalty(player);
         this.cleanupHighSpeedMode(player);
      } else {
         this.tickHighSpeedMode(player);
         YPConfig config = YPConfig.getInstance();
         if (isWaterConditionMet(player, config)) {
            this.removeLandPenalty(player);
            this.cleanupLandWeakness(player);
            this.applyWaterEffect(player, PoseidonPower.WaterEffect.WATER_BREATHING, StatusEffects.WATER_BREATHING, 0);
            this.applyWaterEffect(player, PoseidonPower.WaterEffect.DOLPHINS_GRACE, StatusEffects.DOLPHINS_GRACE, 0);
            this.applyWaterEffect(
               player, PoseidonPower.WaterEffect.REGENERATION, StatusEffects.REGENERATION, Math.max(0, config.poseidon_water_regeneration_amplifier)
            );
            if (config.poseidon_water_buffs_enabled) {
               this.applyWaterEffect(player, PoseidonPower.WaterEffect.HASTE, StatusEffects.HASTE, config.poseidon_water_haste_amplifier);
               this.applyWaterEffect(player, PoseidonPower.WaterEffect.RESISTANCE, StatusEffects.RESISTANCE, config.poseidon_water_resistance_amplifier);
               this.applyWaterEffect(player, PoseidonPower.WaterEffect.STRENGTH, StatusEffects.STRENGTH, config.poseidon_water_strength_amplifier);
            } else {
               this.cleanupWaterEffects(
                  player, EnumSet.of(PoseidonPower.WaterEffect.HASTE, PoseidonPower.WaterEffect.RESISTANCE, PoseidonPower.WaterEffect.STRENGTH)
               );
            }
         } else {
            this.cleanupWaterEffects(player, EnumSet.allOf(PoseidonPower.WaterEffect.class));
            this.applyLandPenalty(player);
            this.applyLeasedEffect(player, this.landEffectLeases, PoseidonPower.LandEffect.WEAKNESS, StatusEffects.WEAKNESS, 0, 40);
         }
      }
   }

   public static boolean isWaterConditionMet(ServerPlayerEntity player) {
      return isWaterConditionMet(player, YPConfig.getInstance());
   }

   private static boolean isWaterConditionMet(ServerPlayerEntity player, YPConfig config) {
      String condition = config.poseidon_water_condition == null ? "TOUCHING" : config.poseidon_water_condition;

      return switch (condition) {
         case "SUBMERGED" -> player.isSubmergedInWater();
         case "SWIMMING_OR_SUBMERGED" -> player.isSwimming() || player.isSubmergedInWater();
         default -> player.isTouchingWater() || player.isSubmergedInWater();
      };
   }

   public static boolean shouldPreventHungerDepletion(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         YPConfig config = YPConfig.getInstance();
         if (config.poseidon_water_hunger_protection_enabled && isWaterConditionMet(player, config)) {
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            return manager.getPowerType(player.getUuid()) == PowerType.POSEIDON && manager.isPowerEnabled(player, PowerType.POSEIDON);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private static RegistryEntry<StatusEffect> waterEffectType(PoseidonPower.WaterEffect effect) {
      return switch (effect) {
         case WATER_BREATHING -> StatusEffects.WATER_BREATHING;
         case DOLPHINS_GRACE -> StatusEffects.DOLPHINS_GRACE;
         case REGENERATION -> StatusEffects.REGENERATION;
         case HASTE -> StatusEffects.HASTE;
         case RESISTANCE -> StatusEffects.RESISTANCE;
         case STRENGTH -> StatusEffects.STRENGTH;
      };
   }

   private static RegistryEntry<StatusEffect> landEffectType(PoseidonPower.LandEffect effect) {
      return StatusEffects.WEAKNESS;
   }

   private void applyWaterEffect(ServerPlayerEntity player, PoseidonPower.WaterEffect key, RegistryEntry<StatusEffect> type, int amplifier) {
      this.applyLeasedEffect(player, this.waterEffectLeases, key, type, amplifier, 40);
   }

   private <K> void applyLeasedEffect(
      ServerPlayerEntity player, Map<UUID, Map<K, PoseidonPower.EffectLease>> store, K key, RegistryEntry<StatusEffect> type, int amplifier, int duration
   ) {
      UUID id = player.getUuid();
      Map<K, PoseidonPower.EffectLease> leases = store.computeIfAbsent(id, ignored -> new HashMap<>());
      StatusEffectInstance current = player.getStatusEffect(type);
      PoseidonPower.EffectLease lease = leases.get(key);
      if (lease == null) {
         if (current != null && (current.getAmplifier() > amplifier || current.getAmplifier() == amplifier && current.getDuration() > duration + 2)) {
            return;
         }

         lease = new PoseidonPower.EffectLease(current, amplifier);
         leases.put(key, lease);
      }

      if (current == null || current.getAmplifier() <= amplifier && (current.getAmplifier() != amplifier || current.getDuration() <= duration + 2)) {
         if (current == null || current.getAmplifier() != amplifier || current.getDuration() <= duration / 2) {
            player.addStatusEffect(new StatusEffectInstance(type, duration, amplifier, false, false, true));
         }
      }
   }

   private void advanceEffectLeases(UUID id) {
      Map<PoseidonPower.WaterEffect, PoseidonPower.EffectLease> water = this.waterEffectLeases.get(id);
      if (water != null) {
         water.values().forEach(lease -> lease.elapsedTicks++);
      }

      Map<PoseidonPower.LandEffect, PoseidonPower.EffectLease> land = this.landEffectLeases.get(id);
      if (land != null) {
         land.values().forEach(lease -> lease.elapsedTicks++);
      }
   }

   private void cleanupWaterEffects(ServerPlayerEntity player, Set<PoseidonPower.WaterEffect> effects) {
      Map<PoseidonPower.WaterEffect, PoseidonPower.EffectLease> leases = this.waterEffectLeases.get(player.getUuid());
      if (leases != null) {
         for (PoseidonPower.WaterEffect effect : effects) {
            PoseidonPower.EffectLease lease = leases.remove(effect);
            if (lease != null) {
               restoreEffectLease(player, waterEffectType(effect), lease);
            }
         }

         if (leases.isEmpty()) {
            this.waterEffectLeases.remove(player.getUuid());
         }
      }
   }

   private void cleanupLandWeakness(ServerPlayerEntity player) {
      Map<PoseidonPower.LandEffect, PoseidonPower.EffectLease> leases = this.landEffectLeases.get(player.getUuid());
      if (leases != null) {
         PoseidonPower.EffectLease lease = leases.remove(PoseidonPower.LandEffect.WEAKNESS);
         if (lease != null) {
            restoreEffectLease(player, landEffectType(PoseidonPower.LandEffect.WEAKNESS), lease);
         }

         if (leases.isEmpty()) {
            this.landEffectLeases.remove(player.getUuid());
         }
      }
   }

   private void cleanupAllManagedEffects(ServerPlayerEntity player) {
      this.cleanupWaterEffects(player, EnumSet.allOf(PoseidonPower.WaterEffect.class));
      this.cleanupLandWeakness(player);
   }

   private static void restoreEffectLease(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, PoseidonPower.EffectLease lease) {
      StatusEffectInstance current = player.getStatusEffect(type);
      boolean stillOurs = current == null || current.getAmplifier() == lease.appliedAmplifier && current.getDuration() <= 42;
      if (stillOurs) {
         if (current != null) {
            player.removeStatusEffect(type);
         }

         if (lease.previous != null) {
            int duration = lease.previous.getDuration();
            if (duration >= 0) {
               duration -= lease.elapsedTicks;
            }

            if (duration != 0 && duration >= -1) {
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

   private void tickCooldown(Map<UUID, Integer> map, UUID id) {
      int remaining = map.getOrDefault(id, 0);
      if (remaining <= 1) {
         map.remove(id);
      } else {
         map.put(id, remaining - 1);
      }
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      YPConfig config = YPConfig.getInstance();
      if (this.tsunamiCooldowns.getOrDefault(id, 0) > 0) {
         player.sendMessage(Text.literal("Tsunami recharging: " + this.tsunamiCooldowns.get(id) / 20 + "s").formatted(Formatting.AQUA), true);
      } else {
         ServerWorld water = player.getEntityWorld();
         if (water instanceof ServerWorld) {
            BlockPos waterx = this.findNearestWater(water, player.getBlockPos(), Math.min(100.0, config.poseidon_tsunami_search_radius));
            if (waterx == null) {
               player.sendMessage(Text.literal("No body of water found within 100 blocks.").formatted(Formatting.YELLOW), true);
            } else {
               Vec3d center = Vec3d.ofCenter(waterx);
               double radius = Math.max(1.0, config.poseidon_tsunami_wave_radius);
               int duration = Math.max(10, config.poseidon_tsunami_wave_duration_ticks);
               this.activeTsunamiWaves.put(id, new PoseidonPower.TsunamiWaveState(player, water, center, radius, duration));
               EpicVfx.cue(
                  water, player.getId(), "poseidon", "water_wave", center.x, center.y, center.z, center.subtract(player.getEntityPos()), 6.0F, 36, 0.2F
               );
               player.addStatusEffect(
                  new StatusEffectInstance(
                     StatusEffects.REGENERATION,
                     Math.max(1, config.poseidon_tsunami_buff_ticks),
                     Math.max(0, config.poseidon_tsunami_buff_regeneration_amplifier),
                     false,
                     true,
                     true
                  )
               );
               player.addStatusEffect(
                  new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, Math.max(1, config.poseidon_tsunami_buff_ticks), 2, false, true, true)
               );
               player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, Math.max(1, config.poseidon_tsunami_buff_ticks), 1, false, true, true));
               water.playSound(null, waterx, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.WEATHER, 4.0F, 0.55F);
               player.sendMessage(
                  Text.literal("POSEIDON, LORD OF THE SEVEN SEAS — THE TIDAL WRATH IS UNLEASHED!").formatted(Formatting.AQUA, Formatting.BOLD), true
               );
            }
         }
      }
   }

   private void tickTsunamiWave(ServerPlayerEntity tickingPlayer) {
      UUID ownerId = tickingPlayer.getUuid();
      PoseidonPower.TsunamiWaveState state = this.activeTsunamiWaves.get(ownerId);
      if (state != null) {
         if (state.world.getServer() != null && state.age < state.durationTicks) {
            state.age++;
            double previousRadius = state.maxRadius * Math.max(0, state.age - 1) / state.durationTicks;
            double currentRadius = state.maxRadius * state.age / state.durationTicks;
            this.applyTsunamiWaveHits(state, previousRadius, currentRadius);
            if (state.age % 3 == 1 || state.age == state.durationTicks) {
               this.renderTsunamiWaveFrame(state, currentRadius);
            }

            if (state.age >= state.durationTicks) {
               state.world
                  .playSound(
                     null,
                     BlockPos.ofFloored(state.center.x, state.center.y, state.center.z),
                     SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED,
                     SoundCategory.WEATHER,
                     3.0F,
                     0.7F
                  );
               this.activeTsunamiWaves.remove(ownerId);
            }
         } else {
            this.activeTsunamiWaves.remove(ownerId);
            state.world
               .playSound(
                  null,
                  BlockPos.ofFloored(state.center.x, state.center.y, state.center.z),
                  SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED,
                  SoundCategory.WEATHER,
                  2.5F,
                  0.85F
               );
         }
      }
   }

   private void applyTsunamiWaveHits(PoseidonPower.TsunamiWaveState state, double previousRadius, double currentRadius) {
      double queryRadius = Math.max(1.0, currentRadius + 2.0);
      Box area = new Box(
         state.center.x - queryRadius,
         state.center.y - 3.0,
         state.center.z - queryRadius,
         state.center.x + queryRadius,
         state.center.y + 8.0,
         state.center.z + queryRadius
      );
      YPConfig config = YPConfig.getInstance();

      for (LivingEntity target : state.world
         .getEntitiesByClass(LivingEntity.class, area, entity -> entity != state.caster && entity.isAlive() && !entity.isRemoved())) {
         UUID targetId = target.getUuid();
         if (!state.hitTargets.contains(targetId)
            && !(target instanceof ServerPlayerEntity other && AllianceManager.get(state.world.getServer()).isAlly(state.caster, other))) {
            Vec3d offset = target.getEntityPos().subtract(state.center);
            double distance = offset.horizontalLength();
            if (!(distance > state.maxRadius) && !(distance + 1.35 < previousRadius) && !(distance > currentRadius + 1.35)) {
               state.hitTargets.add(targetId);
               if (config.poseidon_tsunami_damage > 0.0) {
                  target.damage(state.world, state.world.getDamageSources().playerAttack(state.caster), (float)config.poseidon_tsunami_damage);
               }

               int stunTicks = Math.max(0, config.poseidon_tsunami_stun_ticks);
               target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, stunTicks, 10, false, true, true));
               target.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, stunTicks, 128, false, true, true));
               Vec3d horizontal = new Vec3d(offset.x, 0.0, offset.z);
               Vec3d push = horizontal.horizontalLengthSquared() < 1.0E-5 ? new Vec3d(0.0, 0.0, 1.0) : horizontal.normalize();
               target.setVelocity(push.multiply(1.8).add(0.0, 0.8, 0.0));
            }
         }
      }
   }

   private void renderTsunamiWaveFrame(PoseidonPower.TsunamiWaveState state, double radius) {
      int points = Math.max(18, Math.min(48, (int)Math.ceil((Math.PI * 2) * Math.max(1.0, radius) / 2.2)));
      double crestHeight = 3.4;

      for (int i = 0; i < points; i++) {
         double angle = (Math.PI * 2) * i / points;
         double x = state.center.x + Math.cos(angle) * radius;
         double z = state.center.z + Math.sin(angle) * radius;
         double crestY = state.center.y + crestHeight + Math.sin(angle * 3.0 + state.age * 0.42) * 0.55;
         state.world.spawnParticles(ParticleTypes.SPLASH, true, true, x, state.center.y + 1.0, z, 4, 0.3, 1.7, 0.3, 0.16);
         if (i % 3 == 0) {
            state.world.spawnParticles(ParticleTypes.FALLING_WATER, true, true, x, crestY, z, 2, 0.25, 0.65, 0.25, 0.06);
         }

         if (i % 6 == 0) {
            state.world.spawnParticles(ParticleTypes.BUBBLE, true, true, x, state.center.y + 0.25, z, 2, 0.4, 0.3, 0.4, 0.12);
            state.world.spawnParticles(ParticleTypes.CLOUD, true, true, x, crestY + 0.2, z, 1, 0.25, 0.4, 0.25, 0.03);
         }
      }

      if (state.age <= 1) {
         EpicVfx.shockwave(state.world, state.center.x, state.center.y, state.center.z, 8.0);
         EpicVfx.ring(state.world, ParticleTypes.SPLASH, state.center.x, state.center.y + 0.3, state.center.z, 0.5, 0.25, 32, 0.05);
         EpicVfx.column(state.world, ParticleTypes.SPLASH, state.center.x, state.center.y, state.center.z, 7.0, 16);
      }

      if (state.age <= 6) {
         for (int y = 1; y <= 5; y++) {
            state.world.spawnParticles(ParticleTypes.SPLASH, true, true, state.center.x, state.center.y + y, state.center.z, 7, 0.35, 0.3, 0.35, 0.12);
            state.world.spawnParticles(ParticleTypes.SPLASH, true, true, state.center.x - 1.35, state.center.y + y, state.center.z, 4, 0.25, 0.3, 0.25, 0.1);
            state.world.spawnParticles(ParticleTypes.SPLASH, true, true, state.center.x + 1.35, state.center.y + y, state.center.z, 4, 0.25, 0.3, 0.25, 0.1);
         }
      }
   }

   private BlockPos findNearestWater(ServerWorld world, BlockPos origin, double maxDistance) {
      int max = (int)Math.ceil(maxDistance);
      BlockPos best = null;
      double bestDistance = maxDistance * maxDistance;

      for (int radius = 0; radius <= max; radius += 4) {
         int samples = Math.max(16, (int)(radius * 1.5));

         for (int i = 0; i < samples; i++) {
            double angle = (Math.PI * 2) * i / samples;
            int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
            int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);

            for (int dy = -8; dy <= 8; dy++) {
               BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
               if (world.isChunkLoaded(ChunkPos.toLong(pos)) && world.getBlockState(pos).getFluidState().isIn(FluidTags.WATER)) {
                  double distance = pos.getSquaredDistance(origin);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = pos.toImmutable();
                  }
               }
            }
         }

         if (best != null && radius * radius > bestDistance) {
            break;
         }
      }

      return best;
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
      boolean meleeOrTrident = source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.TRIDENT);
      return meleeOrTrident && isWaterConditionMet(player) ? amount * 1.2F : amount;
   }

   private void applyLandPenalty(ServerPlayerEntity player) {
      EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      if (speed != null && speed.getModifier(LAND_SPEED_ID) == null) {
         speed.addPersistentModifier(new EntityAttributeModifier(LAND_SPEED_ID, -0.1, Operation.ADD_MULTIPLIED_TOTAL));
      }
   }

   private void removeLandPenalty(ServerPlayerEntity player) {
      EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.removeModifier(LAND_SPEED_ID);
      }
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.cleanupAllManagedEffects(player);
      this.activeTsunamiWaves.remove(player.getUuid());
      this.swapCooldowns.remove(player.getUuid());
      this.tsunamiCooldowns.remove(player.getUuid());
      this.removeLandPenalty(player);
      this.cleanupHighSpeedMode(player);
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.cleanupAllManagedEffects(player);
      this.removeLandPenalty(player);
      this.cleanupHighSpeedMode(player);
      this.activeTsunamiWaves.remove(player.getUuid());
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.cleanupAllManagedEffects(player);
      this.removeLandPenalty(player);
      this.cleanupHighSpeedMode(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      this.cleanupAllManagedEffects(player);
      this.removeLandPenalty(player);
      this.cleanupHighSpeedMode(player);
      this.ensureTrident(player);
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      this.handleTridentSwap(player);
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (this.highSpeedModeEnabled.remove(id)) {
         this.removeHighSpeedModifier(player);
         ServerWorld var5 = player.getEntityWorld();
         if (var5 instanceof ServerWorld) {
            EpicVfx.implosion(var5, ParticleTypes.SPLASH, player.getX(), player.getY() + 0.6, player.getZ(), 1.4, 16, 0.14);
            EpicVfx.cue(var5, player.getId(), "poseidon", "aura", player.getX(), player.getY() + 0.6, player.getZ(), Vec3d.ZERO, 0.8F, 16, 0.0F);
         }

         player.sendMessage(Text.literal("Poseidon High-Speed Swim disabled."), true);
      } else {
         this.highSpeedModeEnabled.add(id);
         ServerWorld var4 = player.getEntityWorld();
         if (var4 instanceof ServerWorld) {
            EpicVfx.activation(var4, player.getX(), player.getY(), player.getZ(), 0.8);
            EpicVfx.ring(var4, ParticleTypes.SPLASH, player.getX(), player.getY() + 0.2, player.getZ(), 0.35, 0.14, 20, 0.06);
            EpicVfx.cue(var4, player.getId(), "poseidon", "overdrive", player.getX(), player.getY(), player.getZ(), Vec3d.ZERO, 0.9F, 24, 0.0F);
         }

         player.sendMessage(Text.literal("Poseidon High-Speed Swim enabled. Dive in and start swimming to surge forward!").formatted(Formatting.AQUA), true);
         this.tickHighSpeedMode(player);
      }
   }

   private void tickHighSpeedMode(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      boolean shouldApply = this.highSpeedModeEnabled.contains(id) && (player.isSwimming() || player.isSubmergedInWater());
      EntityAttributeInstance movement = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      if (movement != null) {
         if (shouldApply && this.highSpeedModifierApplied.add(id)) {
            double multiplier = Math.max(0.0, YPConfig.getInstance().poseidon_high_speed_swim_multiplier);
            movement.addTemporaryModifier(new EntityAttributeModifier(HIGH_SPEED_SWIM_ID, multiplier, Operation.ADD_MULTIPLIED_TOTAL));
            player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 0.8F, 1.3F);
            ServerWorld var8 = player.getEntityWorld();
            if (var8 instanceof ServerWorld) {
               EpicVfx.ring(var8, ParticleTypes.SPLASH, player.getX(), player.getY() + 0.2, player.getZ(), 0.35, 0.12, 20, 0.08);
            }
         } else if (!shouldApply) {
            this.removeHighSpeedModifier(player);
         }
      }
   }

   private void removeHighSpeedModifier(ServerPlayerEntity player) {
      EntityAttributeInstance movement = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      if (movement != null) {
         movement.removeModifier(HIGH_SPEED_SWIM_ID);
      }

      this.highSpeedModifierApplied.remove(player.getUuid());
   }

   private void cleanupHighSpeedMode(ServerPlayerEntity player) {
      this.highSpeedModeEnabled.remove(player.getUuid());
      this.removeHighSpeedModifier(player);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      this.handleTridentSwap(player);
   }

   private void handleTridentSwap(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (this.swapCooldowns.getOrDefault(uuid, 0) <= 0) {
         ItemStack trident = player.getMainHandStack();
         if (trident.isOf(Items.TRIDENT) && YPItemData.hasFlag(trident, "YPPoseidonTrident")) {
            boolean currentlyRiptide = this.hasRiptide(player, trident);
            boolean newRiptideMode = !currentlyRiptide;
            this.applyMaxedTridentEnchantments(player, trident, newRiptideMode);
            if (newRiptideMode) {
               player.sendMessage(
                  Text.literal("Poseidon Trident: Riptide III | Impaling V | Unbreaking III | Mending I | Vanishing I").formatted(Formatting.AQUA), true
               );
            } else {
               player.sendMessage(
                  Text.literal("Poseidon Trident: Loyalty III + Channeling I | Impaling V | Unbreaking III | Mending I | Vanishing I")
                     .formatted(Formatting.GOLD),
                  true
               );
            }

            this.swapCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().poseidon_trident_swap_cooldown_ticks));
            ServerWorld var7 = player.getEntityWorld();
            if (var7 instanceof ServerWorld) {
               EpicVfx.ring(
                  var7,
                  newRiptideMode ? ParticleTypes.SPLASH : ParticleTypes.ELECTRIC_SPARK,
                  player.getX(),
                  player.getY() + 1.0,
                  player.getZ(),
                  0.25,
                  0.08,
                  16,
                  0.02
               );
            }

            var7 = player.getEntityWorld();
            if (var7 instanceof ServerWorld) {
               EpicVfx.cue(var7, player.getId(), "poseidon", "swap", player.getX(), player.getY() + 1.0, player.getZ(), Vec3d.ZERO, 0.8F, 18, 0.0F);
            }

            player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0F, 1.0F);
         }
      }
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.ensureTrident(player);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.ensureTrident(player);
   }

   private boolean hasRiptide(ServerPlayerEntity player, ItemStack trident) {
      return EnchantmentHelper.getLevel(player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.RIPTIDE), trident) > 0;
   }

   private void applyMaxedTridentEnchantments(ServerPlayerEntity player, ItemStack trident, boolean riptideMode) {
      Registry<Enchantment> enchantmentRegistry = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
      trident.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
      EnchantmentHelper.apply(trident, builder -> {
         builder.add(enchantmentRegistry.getOrThrow(Enchantments.IMPALING), 5);
         builder.add(enchantmentRegistry.getOrThrow(Enchantments.UNBREAKING), 3);
         builder.add(enchantmentRegistry.getOrThrow(Enchantments.MENDING), 1);
         builder.add(enchantmentRegistry.getOrThrow(Enchantments.VANISHING_CURSE), 1);
         if (riptideMode) {
            builder.add(enchantmentRegistry.getOrThrow(Enchantments.RIPTIDE), 3);
         } else {
            builder.add(enchantmentRegistry.getOrThrow(Enchantments.LOYALTY), 3);
            builder.add(enchantmentRegistry.getOrThrow(Enchantments.CHANNELING), 1);
         }
      });
   }

   private boolean ensureTrident(ServerPlayerEntity player) {
      boolean hasTrident = false;

      for (int i = 0; i < player.getInventory().size(); i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (stack.isOf(Items.TRIDENT) && YPItemData.hasFlag(stack, "YPPoseidonTrident")) {
            hasTrident = true;
            this.applyMaxedTridentEnchantments(player, stack, this.hasRiptide(player, stack));
            this.tridentMakeUnbreakable(stack);
         }
      }

      if (!hasTrident) {
         ItemStack trident = new ItemStack(Items.TRIDENT);
         this.applyMaxedTridentEnchantments(player, trident, true);
         YPItemData.setFlag(trident, "YPPoseidonTrident", true);
         this.tridentMakeUnbreakable(trident);
         player.giveOrDropStack(trident);
         return true;
      } else {
         return false;
      }
   }

   @Override
   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      return this.ensureTrident(player) ? List.of("Poseidon Trident") : List.of();
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.swapCooldowns.remove(playerId);
      this.tsunamiCooldowns.remove(playerId);
   }

   private void tridentMakeUnbreakable(ItemStack trident) {
      trident.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
   }

   @Override
   public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
      return stack.isOf(Items.TRIDENT) && YPItemData.hasFlag(stack, "YPPoseidonTrident") || stack.isOf(Items.SHIELD);
   }

   private static final class EffectLease {
      final StatusEffectInstance previous;
      final int appliedAmplifier;
      int elapsedTicks;

      EffectLease(StatusEffectInstance previous, int appliedAmplifier) {
         this.previous = previous == null ? null : new StatusEffectInstance(previous);
         this.appliedAmplifier = appliedAmplifier;
      }
   }

   private static enum LandEffect {
      WEAKNESS;
   }

   private static final class TsunamiWaveState {
      private final ServerPlayerEntity caster;
      private final ServerWorld world;
      private final Vec3d center;
      private final double maxRadius;
      private final int durationTicks;
      private final Set<UUID> hitTargets = new HashSet<>();
      private int age;

      private TsunamiWaveState(ServerPlayerEntity caster, ServerWorld world, Vec3d center, double maxRadius, int durationTicks) {
         this.caster = caster;
         this.world = world;
         this.center = center;
         this.maxRadius = maxRadius;
         this.durationTicks = durationTicks;
      }
   }

   private static enum WaterEffect {
      WATER_BREATHING,
      DOLPHINS_GRACE,
      REGENERATION,
      HASTE,
      RESISTANCE,
      STRENGTH;
   }
}
