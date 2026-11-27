package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.VanillaMeleeTracker;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;

public class IceEmperorPower extends Power {
   private final Map<UUID, Integer> absoluteZeroCooldowns = new HashMap<>();
   private final Map<UUID, Integer> meteorCooldowns = new HashMap<>();
   private final Map<UUID, Integer> guardSummonCooldowns = new HashMap<>();
   private final Map<UUID, Set<UUID>> guardBearIds = new HashMap<>();
   private final Map<UUID, Integer> guardRecoveryTicks = new HashMap<>();
   private final Map<UUID, Integer> playerAuraTicks = new HashMap<>();
   private final Map<UUID, IceEmperorPower.IceMeteor> activeMeteors = new HashMap<>();
   private final Map<UUID, IceEmperorPower.Snowstorm> activeSnowstorms = new HashMap<>();
   private final Map<UUID, IceEmperorPower.HeatBlast> activeHeatBlasts = new HashMap<>();
   private static final DustParticleEffect HEAT_ORANGE = new DustParticleEffect(16742936, 1.25F);
   private static final DustParticleEffect HEAT_RED = new DustParticleEffect(14234656, 1.05F);
   private static final DustParticleEffect HEAT_WHITE = new DustParticleEffect(16774102, 0.85F);
   private final Map<UUID, Long> lastAuraFreezeDamageTicks = new HashMap<>();
   private long lastCleanupTick = Long.MIN_VALUE;
   private final Map<Entity, IceEmperorPower.IceFrozenEntity> iceFrozenEntities = new IdentityHashMap<>();
   private final Map<IceEmperorPower.TemporaryBlockKey, IceEmperorPower.TemporaryBlock> temporaryBlocks = new HashMap<>();
   private static final Identifier ARMOURED_SPEED_ID = Identifier.of("yppowers", "ice_emperor_armoured_speed");
   private static final Identifier ASSASSIN_SPEED_ID = Identifier.of("yppowers", "ice_emperor_assassin_speed");
   private static final Identifier ASSASSIN_HEALTH_ID = Identifier.of("yppowers", "ice_emperor_assassin_health");
   private final Map<UUID, StatusEffectInstance> formResistanceBaseline = new HashMap<>();
   private final Set<UUID> formResistanceSnapshotTaken = new HashSet<>();
   private final Map<UUID, Integer> formResistanceElapsedTicks = new HashMap<>();
   private final Map<UUID, Integer> formResistanceAppliedAmplifier = new HashMap<>();
   private final Set<UUID> formOwnedInvisibility = new HashSet<>();
   private final Map<UUID, Boolean> stealthLastDetected = new HashMap<>();
   private final Map<UUID, Integer> stealthUpdateTicks = new HashMap<>();
   private final Map<UUID, RegistryKey<World>> formLastDimension = new HashMap<>();
   private final Map<UUID, Boolean> formActiveSyncState = new HashMap<>();

   @Override
   public PowerType getType() {
      return PowerType.ICE_EMPEROR;
   }

   public static void serverTick(MinecraftServer server) {
      if (PowerManager.get(server).getPowerInstance(PowerType.ICE_EMPEROR) instanceof IceEmperorPower ice && ice.lastCleanupTick != server.getTicks()) {
         ice.lastCleanupTick = server.getTicks();
         ice.tickTemporaryBlocks();
         ice.tickFrozenEntities(server);
         ice.tickIceMeteors(server);
         ice.tickHeatBlasts(server);
         if (server.getTicks() % 200 == 0) {
            ice.cleanupAuraFreezeDamageTicks(server.getTicks());
         }
      }
   }

   public static boolean isEntityFrozen(Entity entity) {
      return entity != null && entity.getEntityWorld() instanceof ServerWorld world && world.getServer() != null
         ? PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR) instanceof IceEmperorPower ice
            && ice.iceFrozenEntities.containsKey(entity)
         : false;
   }

   public static Vec3d getFrozenVelocity(Entity entity) {
      if (entity == null || !(entity.getEntityWorld() instanceof ServerWorld world && world.getServer() != null)) {
         return null;
      } else if (PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR) instanceof IceEmperorPower ice) {
         IceEmperorPower.IceFrozenEntity frozen = ice.iceFrozenEntities.get(entity);
         return frozen == null ? null : frozen.velocity;
      } else {
         return null;
      }
   }

   private void freezeEntity(Entity entity, long durationTicks, MinecraftServer server) {
      long expiresAt = server.getTicks() + durationTicks;
      IceEmperorPower.IceFrozenEntity existing = this.iceFrozenEntities.get(entity);
      if (existing != null) {
         existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
      } else {
         Vec3d originalVelocity = TimeWarperPower.getFrozenVelocity(entity);
         if (originalVelocity == null) {
            originalVelocity = SeriousPunchPower.getFrozenVelocity(entity);
         }

         if (originalVelocity == null) {
            originalVelocity = entity.getVelocity();
         }

         this.iceFrozenEntities.put(entity, new IceEmperorPower.IceFrozenEntity(entity, originalVelocity, expiresAt));
      }

      entity.setVelocity(Vec3d.ZERO);
      ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
   }

   private void tickFrozenEntities(MinecraftServer server) {
      long now = server.getTicks();
      Iterator<Entry<Entity, IceEmperorPower.IceFrozenEntity>> iterator = this.iceFrozenEntities.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<Entity, IceEmperorPower.IceFrozenEntity> entry = iterator.next();
         Entity entity = entry.getKey();
         IceEmperorPower.IceFrozenEntity frozen = entry.getValue();
         if (entity.isRemoved() || !entity.isAlive()) {
            iterator.remove();
         } else if (now >= frozen.expiresAt) {
            iterator.remove();
            if (!TimeWarperPower.isEntityFrozen(entity) && !SeriousPunchPower.isEntityFrozen(entity)) {
               entity.setVelocity(frozen.velocity);
               ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
            }
         } else {
            entity.setPosition(frozen.position);
            entity.setVelocity(Vec3d.ZERO);
            entity.setYaw(frozen.yaw);
            entity.setPitch(frozen.pitch);
            ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
         }
      }
   }

   private static IceEmperorPower.IceForm configuredDefaultForm(YPConfig config) {
      IceEmperorPower.IceForm requested = IceEmperorPower.IceForm.parse(config.ice_emperor_default_form);
      if (isFormEnabled(requested, config)) {
         return requested;
      } else {
         for (IceEmperorPower.IceForm form : IceEmperorPower.IceForm.values()) {
            if (isFormEnabled(form, config)) {
               return form;
            }
         }

         return IceEmperorPower.IceForm.ARMOURED;
      }
   }

   private static boolean isFormEnabled(IceEmperorPower.IceForm form, YPConfig config) {
      return switch (form) {
         case ARMOURED -> config.ice_emperor_form_armoured_enabled;
         case STEALTH -> config.ice_emperor_form_stealth_enabled;
         case ASSASSIN -> config.ice_emperor_form_assassin_enabled;
      };
   }

   private IceEmperorPower.IceForm selectedForm(ServerPlayerEntity player) {
      return player.getEntityWorld().getServer() == null
         ? IceEmperorPower.IceForm.ARMOURED
         : IceEmperorPower.IceForm.parse(PowerManager.get(player.getEntityWorld().getServer()).getIceEmperorForm(player.getUuid()));
   }

   private void syncFormState(ServerPlayerEntity player, boolean active) {
      if (player.getEntityWorld().getServer() != null) {
         this.formActiveSyncState.put(player.getUuid(), active);
         YPNetworking.syncIceEmperorForm(player.getEntityWorld().getServer(), player.getUuid(), this.selectedForm(player).name(), active);
      }
   }

   public void activateHeatBlast(ServerPlayerEntity caster) {
      if (caster != null && !caster.isRemoved() && caster.isAlive()) {
         ServerWorld blast = caster.getEntityWorld();
         if (blast instanceof ServerWorld && blast.getServer() != null && !GraceManager.isActive(blast.getServer())) {
            if (PowerManager.get(blast.getServer()).getPowerType(caster.getUuid()) != PowerType.ICE_EMPEROR) {
               return;
            }

            IceEmperorPower.HeatBlast blastx = new IceEmperorPower.HeatBlast(blast, caster.getUuid(), caster.getEntityPos().add(0.0, 0.8, 0.0));
            this.activeHeatBlasts.put(caster.getUuid(), blastx);
            blast.playSound(null, caster.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.7F, 0.55F);
            blast.playSound(null, caster.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 0.65F);
            EpicVfx.impact(blast, caster.getX(), caster.getY() + 0.7, caster.getZ(), 1.8);
            blast.spawnParticles(HEAT_WHITE, true, true, caster.getX(), caster.getY() + 0.8, caster.getZ(), 18, 0.45, 0.35, 0.45, 0.08);
            blast.spawnParticles(ParticleTypes.FLAME, true, true, caster.getX(), caster.getY() + 0.8, caster.getZ(), 24, 0.4, 0.3, 0.4, 0.06);
            return;
         }
      }
   }

   private void tickHeatBlasts(MinecraftServer server) {
      if (!this.activeHeatBlasts.isEmpty()) {
         Iterator<Entry<UUID, IceEmperorPower.HeatBlast>> iterator = this.activeHeatBlasts.entrySet().iterator();

         while (iterator.hasNext()) {
            IceEmperorPower.HeatBlast blast = iterator.next().getValue();
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(blast.caster);
            if (caster != null && caster.isAlive() && !caster.isRemoved() && caster.getEntityWorld() == blast.world && !GraceManager.isActive(server)) {
               blast.ticks++;
               YPConfig config = YPConfig.getInstance();
               double maxRadius = config.ice_emperor_heat_blast_radius;
               double progress = Math.min(1.0, blast.ticks / 12.0);
               double radius = maxRadius * progress;
               double y = blast.center.y;
               Box bounds = new Box(blast.center.x - radius, y - 3.5, blast.center.z - radius, blast.center.x + radius, y + 3.5, blast.center.z + radius);
               double radiusSquared = radius * radius;

               for (LivingEntity target : blast.world
                  .getEntitiesByClass(
                     LivingEntity.class,
                     bounds,
                     entity -> entity.isAlive() && !entity.isRemoved() && !blast.affected.contains(entity.getUuid()) && PowerCombatUtil.canHarm(caster, entity)
                  )) {
                  double dx = target.getX() - blast.center.x;
                  double dz = target.getZ() - blast.center.z;
                  if (!(dx * dx + dz * dz > radiusSquared)) {
                     blast.affected.add(target.getUuid());
                     target.setOnFireFor(config.ice_emperor_heat_blast_fire_seconds);
                     target.addStatusEffect(
                        new StatusEffectInstance(
                           StatusEffects.SLOWNESS,
                           config.ice_emperor_heat_blast_slowness_ticks,
                           config.ice_emperor_heat_blast_slowness_amplifier,
                           false,
                           true,
                           true
                        )
                     );
                  }
               }

               int samples = 24;

               for (int i = 0; i < samples; i++) {
                  double angle = (Math.PI * 2) * i / samples;
                  double x = blast.center.x + Math.cos(angle) * radius;
                  double z = blast.center.z + Math.sin(angle) * radius;
                  double waveY = y + 0.1 + Math.sin(angle * 2.0 + blast.ticks * 0.35) * 0.18;
                  blast.world.spawnParticles(i % 3 == 0 ? HEAT_RED : HEAT_ORANGE, true, true, x, waveY, z, 1, 0.025, 0.04, 0.025, 0.004);
                  if (i % 4 == 0 && blast.ticks % 2 == 0) {
                     blast.world.spawnParticles(HEAT_WHITE, true, true, x, waveY + 0.04, z, 1, 0.015, 0.025, 0.015, 0.002);
                  }
               }

               if (blast.ticks % 3 == 0) {
                  blast.world
                     .spawnParticles(ParticleTypes.FLAME, true, true, blast.center.x, y, blast.center.z, 8, 0.35 + radius * 0.025, 0.18, 0.35 + radius * 0.025, 0.035);
               }

               if (blast.ticks >= 12) {
                  iterator.remove();
               }
            } else {
               iterator.remove();
            }
         }
      }
   }

   public void cycleForm(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         PowerManager manager = PowerManager.get(server);
         YPConfig config = YPConfig.getInstance();
         if (manager.getPowerType(player.getUuid()) == PowerType.ICE_EMPEROR) {
            if (!GraceManager.blockAbility(player)) {
               if (config.ice_emperor_forms_enabled && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
                  IceEmperorPower.IceForm current = this.selectedForm(player);
                  IceEmperorPower.IceForm next = current;

                  for (int step = 1; step <= IceEmperorPower.IceForm.values().length; step++) {
                     IceEmperorPower.IceForm candidate = IceEmperorPower.IceForm.values()[(current.ordinal() + step) % IceEmperorPower.IceForm.values().length];
                     if (isFormEnabled(candidate, config)) {
                        next = candidate;
                        break;
                     }
                  }

                  if (next == current) {
                     player.sendMessage(Text.literal("No other Ice Emperor forms are enabled.").formatted(Formatting.YELLOW), true);
                  } else {
                     this.applyFormSelection(player, next, manager, config);
                  }
               } else {
                  player.sendMessage(Text.literal("Ice Emperor Forms are currently disabled.").formatted(Formatting.YELLOW), true);
               }
            }
         }
      }
   }

   public List<String> getEnabledFormIds() {
      YPConfig config = YPConfig.getInstance();
      return !config.ice_emperor_forms_enabled
         ? List.of()
         : Arrays.stream(IceEmperorPower.IceForm.values()).filter(form -> isFormEnabled(form, config)).map(Enum::name).toList();
   }

   public String getSelectedFormId(ServerPlayerEntity player) {
      return this.selectedForm(player).name();
   }

   public boolean selectFormFromMenu(ServerPlayerEntity player, String requested) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         PowerManager manager = PowerManager.get(server);
         YPConfig config = YPConfig.getInstance();
         if (manager.getPowerType(player.getUuid()) == PowerType.ICE_EMPEROR
            && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
            && !GraceManager.blockAbility(player)
            && config.ice_emperor_forms_enabled) {
            IceEmperorPower.IceForm next;
            try {
               next = IceEmperorPower.IceForm.valueOf(requested == null ? "" : requested.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var8) {
               return false;
            }

            if (!isFormEnabled(next, config)) {
               player.sendMessage(Text.literal("That Ice Emperor form is disabled.").formatted(Formatting.YELLOW), true);
               return false;
            } else if (next == this.selectedForm(player)) {
               return true;
            } else {
               this.applyFormSelection(player, next, manager, config);
               return true;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void applyFormSelection(ServerPlayerEntity player, IceEmperorPower.IceForm next, PowerManager manager, YPConfig config) {
      this.clearFormEffects(player);
      manager.setIceEmperorForm(player.getUuid(), next.name());
      this.formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
      if (config.ice_emperor_forms_action_bar_enabled) {
         player.sendMessage(
            Text.literal("Ice Emperor Form: ")
               .formatted(Formatting.AQUA)
               .append(
                  Text.literal(next.displayName())
                     .formatted(
                        next == IceEmperorPower.IceForm.ASSASSIN
                           ? Formatting.RED
                           : (next == IceEmperorPower.IceForm.STEALTH ? Formatting.GRAY : Formatting.BLUE)
                     )
               ),
            true
         );
      }

      ServerWorld var6 = player.getEntityWorld();
      if (var6 instanceof ServerWorld) {
         EpicVfx.cue(var6, player.getId(), "ice_emperor", "phase", player.getX(), player.getY() + 1.0, player.getZ(), Vec3d.ZERO, 1.0F, 24, 0.0F);
         if (config.ice_emperor_forms_particles_enabled) {
            var6.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.55, 0.8, 0.55, 0.02);
            var6.spawnParticles(ParticleTypes.POOF, true, true, player.getX(), player.getY() + 0.9, player.getZ(), 5, 0.25, 0.35, 0.25, 0.01);
            EpicVfx.helix(var6, ParticleTypes.SNOWFLAKE, player.getX(), player.getY(), player.getZ(), 2.4, 0.9, 2.0, 24, 0.06);
            EpicVfx.ring(var6, ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.1, player.getZ(), 0.4, 0.12, 20, 0.02);
         }

         if (config.ice_emperor_forms_sound_enabled) {
            var6.playSound(
               null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.55F, next == IceEmperorPower.IceForm.ASSASSIN ? 1.35F : 0.9F
            );
         }
      }

      this.syncFormState(player, true);
   }

   private void tickForms(ServerPlayerEntity player, ServerWorld world) {
      UUID id = player.getUuid();
      YPConfig config = YPConfig.getInstance();
      PowerManager manager = PowerManager.get(world.getServer());
      RegistryKey<World> priorDimension = this.formLastDimension.put(id, world.getRegistryKey());
      if (priorDimension != null && !priorDimension.equals(world.getRegistryKey())) {
         this.clearFormEffects(player);
      }

      boolean formsActive = config.ice_emperor_forms_enabled
         && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
         && !GraceManager.isActive(world.getServer());
      Boolean previouslyActive = this.formActiveSyncState.put(id, formsActive);
      if (previouslyActive == null || previouslyActive != formsActive) {
         YPNetworking.syncIceEmperorForm(world.getServer(), id, this.selectedForm(player).name(), formsActive);
      }

      if (!formsActive) {
         this.clearFormEffects(player);
      } else {
         IceEmperorPower.IceForm form = this.selectedForm(player);
         if (!isFormEnabled(form, config)) {
            IceEmperorPower.IceForm replacement = configuredDefaultForm(config);
            if (!isFormEnabled(replacement, config)) {
               this.clearFormEffects(player);
               return;
            }

            this.clearFormEffects(player);
            manager.setIceEmperorForm(id, replacement.name());
            form = replacement;
            if (config.ice_emperor_forms_action_bar_enabled) {
               player.sendMessage(Text.literal("Ice Emperor Form changed to: " + replacement.displayName()).formatted(Formatting.AQUA), true);
            }

            YPNetworking.syncIceEmperorForm(world.getServer(), id, replacement.name(), true);
         }

         this.ensureMultiplier(
            player,
            EntityAttributes.MOVEMENT_SPEED,
            ARMOURED_SPEED_ID,
            form == IceEmperorPower.IceForm.ARMOURED ? config.ice_emperor_armoured_movement_multiplier - 1.0 : 0.0
         );
         this.ensureMultiplier(
            player,
            EntityAttributes.MOVEMENT_SPEED,
            ASSASSIN_SPEED_ID,
            form == IceEmperorPower.IceForm.ASSASSIN ? config.ice_emperor_assassin_movement_multiplier - 1.0 : 0.0
         );
         this.ensureMultiplier(
            player,
            EntityAttributes.MAX_HEALTH,
            ASSASSIN_HEALTH_ID,
            form == IceEmperorPower.IceForm.ASSASSIN ? config.ice_emperor_assassin_max_health_multiplier - 1.0 : 0.0
         );
         player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
         if (form == IceEmperorPower.IceForm.ARMOURED) {
            this.tickArmouredResistance(player, config);
         } else {
            this.clearArmouredResistance(player);
         }

         if (form == IceEmperorPower.IceForm.STEALTH) {
            this.tickStealth(player, world, config);
         } else {
            this.clearStealth(player);
         }

         if (config.ice_emperor_forms_particles_enabled && form == IceEmperorPower.IceForm.ARMOURED && player.age % 12 == 0) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, player.getX(), player.getY() + 0.9, player.getZ(), 2, 0.35, 0.45, 0.35, 0.005);
         }

         if (config.ice_emperor_forms_particles_enabled
            && form == IceEmperorPower.IceForm.ASSASSIN
            && config.ice_emperor_assassin_ice_trail_enabled
            && player.isSprinting()
            && player.getVelocity().x * player.getVelocity().x + player.getVelocity().z * player.getVelocity().z > 0.0025
            && player.age % 4 == 0) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, player.getX(), player.getY() + 0.08, player.getZ(), 4, 0.18, 0.025, 0.18, 0.01);
            world.spawnParticles(
               new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), true, true,
               player.getX(),
               player.getY() + 0.04,
               player.getZ(),
               1,
               0.12,
               0.01,
               0.12,
               0.01
            );
         }
      }
   }

   private void tickArmouredResistance(ServerPlayerEntity player, YPConfig config) {
      UUID id = player.getUuid();
      if (this.formResistanceSnapshotTaken.add(id)) {
         StatusEffectInstance previous = player.getStatusEffect(StatusEffects.RESISTANCE);
         this.formResistanceBaseline.put(id, previous == null ? null : new StatusEffectInstance(previous));
         this.formResistanceElapsedTicks.put(id, 0);
      } else {
         this.formResistanceElapsedTicks.merge(id, 1, Integer::sum);
      }

      int targetAmplifier = config.ice_emperor_armoured_resistance_amplifier;
      StatusEffectInstance current = player.getStatusEffect(StatusEffects.RESISTANCE);
      Integer previouslyAppliedAmplifier = this.formResistanceAppliedAmplifier.get(id);
      boolean looksLikeOurShortRefresh = current != null
         && previouslyAppliedAmplifier != null
         && current.getAmplifier() == previouslyAppliedAmplifier
         && current.getDuration() <= 8;
      if (this.formResistanceSnapshotTaken.contains(id) && current != null && !looksLikeOurShortRefresh) {
         this.formResistanceBaseline.put(id, new StatusEffectInstance(current));
         this.formResistanceElapsedTicks.put(id, 0);
      }

      if (current == null || current.getAmplifier() <= targetAmplifier) {
         if (current == null || current.getAmplifier() < targetAmplifier || current.getAmplifier() == targetAmplifier && current.getDuration() <= 2) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 8, targetAmplifier, false, false, true));
            this.formResistanceAppliedAmplifier.put(id, targetAmplifier);
         }
      }
   }

   private void clearArmouredResistance(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      boolean hadSnapshot = this.formResistanceSnapshotTaken.remove(id);
      int elapsed = this.formResistanceElapsedTicks.getOrDefault(id, 0);
      Integer appliedAmplifier = this.formResistanceAppliedAmplifier.remove(id);
      StatusEffectInstance current = player.getStatusEffect(StatusEffects.RESISTANCE);
      boolean currentLooksOwned = current != null && appliedAmplifier != null && current.getAmplifier() == appliedAmplifier && current.getDuration() <= 8;
      if (currentLooksOwned) {
         player.removeStatusEffect(StatusEffects.RESISTANCE);
      }

      if (hadSnapshot && (currentLooksOwned || current == null)) {
         StatusEffectInstance baseline = this.formResistanceBaseline.get(id);
         restoreResistance(player, baseline, elapsed);
      }

      this.formResistanceBaseline.remove(id);
      this.formResistanceElapsedTicks.remove(id);
   }

   private static void restoreResistance(ServerPlayerEntity player, StatusEffectInstance saved, int elapsedTicks) {
      if (saved != null) {
         int duration = saved.getDuration();
         int remaining = duration < 0 ? duration : duration - Math.max(0, elapsedTicks);
         if (remaining != 0 && remaining >= -1) {
            player.addStatusEffect(
               new StatusEffectInstance(
                  saved.getEffectType(), remaining, saved.getAmplifier(), saved.isAmbient(), saved.shouldShowParticles(), saved.shouldShowIcon()
               )
            );
         }
      }
   }

   private void tickStealth(ServerPlayerEntity player, ServerWorld world, YPConfig config) {
      UUID id = player.getUuid();
      int elapsed = this.stealthUpdateTicks.getOrDefault(id, 0) + 1;
      int interval = Math.max(1, config.ice_emperor_stealth_update_interval_ticks);
      boolean hasPriorDetection = this.stealthLastDetected.containsKey(id);
      if (elapsed < interval && hasPriorDetection) {
         this.stealthUpdateTicks.put(id, elapsed);
      } else {
         this.stealthUpdateTicks.put(id, 0);
         boolean detected = false;
         double radius = Math.max(1.0, config.ice_emperor_stealth_detection_radius);
         double radiusSquared = radius * radius;
         AllianceManager alliances = AllianceManager.get(world.getServer());

         for (ServerPlayerEntity other : world.getPlayers()) {
            if (other != player
               && other.isAlive()
               && !other.isSpectator()
               && !(other.squaredDistanceTo(player) > radiusSquared)
               && (config.ice_emperor_stealth_allies_count || !alliances.isAlly(player, other))) {
               detected = true;
               break;
            }
         }

         Boolean oldDetected = this.stealthLastDetected.put(id, detected);
         if (!detected && !player.isInvisible()) {
            player.setInvisible(true);
            this.formOwnedInvisibility.add(id);
         } else if (detected && this.formOwnedInvisibility.remove(id)) {
            player.setInvisible(false);
         }

         if ((oldDetected == null || oldDetected != detected) && config.ice_emperor_forms_particles_enabled) {
            world.spawnParticles(
               detected ? ParticleTypes.POOF : ParticleTypes.SNOWFLAKE, true, true,
               player.getX(),
               player.getY() + 1.0,
               player.getZ(),
               detected ? 8 : 12,
               0.35,
               0.6,
               0.35,
               0.01
            );
         }
      }
   }

   private void clearStealth(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (this.formOwnedInvisibility.remove(id)) {
         player.setInvisible(false);
      }

      this.stealthLastDetected.remove(id);
      this.stealthUpdateTicks.remove(id);
   }

   private void ensureMultiplier(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute, Identifier id, double amount) {
      EntityAttributeInstance instance = player.getAttributeInstance(attribute);
      if (instance != null) {
         EntityAttributeModifier modifier = instance.getModifier(id);
         if (Math.abs(amount) < 1.0E-6) {
            if (modifier != null) {
               instance.removeModifier(id);
            }
         } else {
            if (modifier == null || Math.abs(modifier.value() - amount) > 1.0E-6 || modifier.operation() != Operation.ADD_MULTIPLIED_TOTAL) {
               if (modifier != null) {
                  instance.removeModifier(id);
               }

               instance.addTemporaryModifier(new EntityAttributeModifier(id, amount, Operation.ADD_MULTIPLIED_TOTAL));
            }
         }
      }
   }

   private void removeFormModifiers(ServerPlayerEntity player) {
      for (Object[] pair : new Object[][]{
         {EntityAttributes.MOVEMENT_SPEED, ARMOURED_SPEED_ID},
         {EntityAttributes.MOVEMENT_SPEED, ASSASSIN_SPEED_ID},
         {EntityAttributes.MAX_HEALTH, ASSASSIN_HEALTH_ID}
      }) {
         RegistryEntry<EntityAttribute> attribute = (RegistryEntry<EntityAttribute>)pair[0];
         EntityAttributeInstance instance = player.getAttributeInstance(attribute);
         if (instance != null) {
            instance.removeModifier((Identifier)pair[1]);
         }
      }

      player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
   }

   private void clearFormEffects(ServerPlayerEntity player) {
      this.removeFormModifiers(player);
      this.clearArmouredResistance(player);
      this.clearStealth(player);
   }

   private void setConfiguredDefault(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         PowerManager.get(player.getEntityWorld().getServer()).setIceEmperorForm(player.getUuid(), configuredDefaultForm(YPConfig.getInstance()).name());
      }
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.clearFormEffects(player);
      this.formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
      this.setConfiguredDefault(player);
      if (player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         boolean active = YPConfig.getInstance().ice_emperor_forms_enabled
            && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
            && !GraceManager.isActive(player.getEntityWorld().getServer());
         this.syncFormState(player, active);
      }
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.clearFormEffects(player);
      this.formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
      if (player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (!YPConfig.getInstance().ice_emperor_form_persist_through_reconnect || !manager.hasIceEmperorForm(player.getUuid())) {
            this.setConfiguredDefault(player);
         }

         boolean active = YPConfig.getInstance().ice_emperor_forms_enabled
            && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
            && manager.getPowerType(player.getUuid()) == PowerType.ICE_EMPEROR
            && !GraceManager.isActive(player.getEntityWorld().getServer());
         this.syncFormState(player, active);
      }

      if (player.getEntityWorld().getServer() != null && !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         player.removeStatusEffect(StatusEffects.SPEED);
         player.removeStatusEffect(StatusEffects.WEAKNESS);
      }
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.discardOwnedGuards(player);
      this.clearFormEffects(player);
      this.formLastDimension.remove(player.getUuid());
      this.playerAuraTicks.remove(player.getUuid());
      if (!YPConfig.getInstance().ice_emperor_form_persist_through_reconnect) {
         this.setConfiguredDefault(player);
      }

      if (player.getEntityWorld().getServer() != null) {
         this.syncFormState(player, false);
      }

      this.formActiveSyncState.remove(player.getUuid());
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.discardOwnedGuards(player);
      this.clearFormEffects(player);
      this.formLastDimension.remove(player.getUuid());
      if (!YPConfig.getInstance().ice_emperor_form_persist_through_death) {
         this.setConfiguredDefault(player);
      }

      if (player.getEntityWorld().getServer() != null) {
         this.syncFormState(player, false);
      }
   }

   private void discardOwnedGuards(ServerPlayerEntity player) {
      UUID ownerId = player.getUuid();
      Set<UUID> ids = this.guardBearIds.remove(ownerId);
      this.guardRecoveryTicks.remove(ownerId);
      if (ids != null && player.getEntityWorld().getServer() != null) {
         for (ServerWorld world : player.getEntityWorld().getServer().getWorlds()) {
            for (UUID id : ids) {
               if (world.getEntity(id) instanceof PolarBearEntity bear && bear.getCommandTags().contains(guardOwnerTag(ownerId))) {
                  bear.discard();
               }
            }
         }
      }
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      this.clearFormEffects(player);
      this.formLastDimension.put(player.getUuid(), player.getEntityWorld().getRegistryKey());
      if (!YPConfig.getInstance().ice_emperor_form_persist_through_death) {
         this.setConfiguredDefault(player);
      }

      if (player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         boolean active = YPConfig.getInstance().ice_emperor_forms_enabled
            && manager.isPowerEnabled(player, PowerType.ICE_EMPEROR)
            && !GraceManager.isActive(player.getEntityWorld().getServer());
         this.syncFormState(player, active);
      }

      if (player.getEntityWorld().getServer() != null && !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         player.removeStatusEffect(StatusEffects.SPEED);
         player.removeStatusEffect(StatusEffects.WEAKNESS);
      }
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null) {
         boolean enabled = PowerManager.get(player.getEntityWorld().getServer()).togglePowerEnabled(player, PowerType.ICE_EMPEROR);
         if (!enabled) {
            this.playerAuraTicks.remove(player.getUuid());
            this.clearFormEffects(player);
            player.removeStatusEffect(StatusEffects.SPEED);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
            player.sendMessage(
               Text.literal("Ice Emperor's powers and Glacial Field are disabled. Press R to re-enable them.").formatted(Formatting.YELLOW), true
            );
         } else {
            player.sendMessage(Text.literal("Ice Emperor's powers and Glacial Field are enabled.").formatted(Formatting.AQUA), true);
         }

         ServerWorld var4 = player.getEntityWorld();
         if (var4 instanceof ServerWorld) {
            if (enabled) {
               EpicVfx.activation(var4, player.getX(), player.getY(), player.getZ(), 0.9);
            } else {
               EpicVfx.implosion(var4, ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.7, player.getZ(), 1.8, 18, 0.12);
            }
         }

         this.syncFormState(player, enabled && YPConfig.getInstance().ice_emperor_forms_enabled && !GraceManager.isActive(player.getEntityWorld().getServer()));
      }
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      this.summonPolarBearGuards(player, false);
   }

   public void onTertiaryActivateWithEnergyState(ServerPlayerEntity player, boolean lowEnergy) {
      this.summonPolarBearGuards(player, lowEnergy);
   }

   private void summonPolarBearGuards(ServerPlayerEntity player, boolean lowEnergy) {
      ServerWorld ownerId = player.getEntityWorld();
      if (ownerId instanceof ServerWorld) {
         ServerWorld world = ownerId;
         if (ownerId.getServer() != null) {
            if (!PowerManager.get(ownerId.getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
               player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
               return;
            }

            UUID ownerIdx = player.getUuid();
            int cooldown = this.guardSummonCooldowns.getOrDefault(ownerIdx, 0);
            if (cooldown > 0) {
               player.sendMessage(Text.literal("Ice Bear Guards cooldown: " + (cooldown + 19) / 20 + "s.").formatted(Formatting.YELLOW), true);
               return;
            }

            Set<UUID> ids = this.guardBearIds.computeIfAbsent(ownerIdx, ignored -> new HashSet<>());
            this.recoverNearbyGuards(player, ownerId, ids);
            int desired = Math.max(1, YPConfig.getInstance().ice_emperor_guard_count);
            int active = this.countLiveGuards(ownerId, ids);
            if (active >= desired) {
               player.sendMessage(Text.literal("Your polar-bear guards are already at full strength.").formatted(Formatting.AQUA), true);
               return;
            }

            int spawned = 0;
            int hostileSpawned = 0;

            for (int i = active; i < desired; i++) {
               double angle = (double)i / desired * Math.PI * 2.0 + Math.toRadians(player.getYaw());
               double x = player.getX() + Math.cos(angle) * 1.8;
               double z = player.getZ() + Math.sin(angle) * 1.8;
               BlockPos spawnPos = BlockPos.ofFloored(x, player.getY(), z);
               PolarBearEntity bear = EntityType.POLAR_BEAR.spawn(world, spawnPos, SpawnReason.MOB_SUMMONED);
               if (bear != null) {
                  bear.refreshPositionAndAngles(x, player.getY(), z, player.getYaw(), 0.0F);
                  bear.addCommandTag(guardOwnerTag(ownerIdx));
                  bear.addCommandTag("yppowers_owner_" + ownerIdx);
                  SummoningVfx.play(world, bear);
                  boolean rogue = lowEnergy && world.getRandom().nextDouble() * 100.0 < YPConfig.getInstance().ice_emperor_guard_hostile_chance_percent;
                  if (lowEnergy) {
                     weakenLowEnergyGuard(bear);
                  }

                  if (!rogue && !lowEnergy) {
                     SummonTuning.strengthen(player, bear);
                  }

                  if (rogue) {
                     bear.addCommandTag(guardHostileTag());
                     bear.setCustomName(Text.literal("Rogue Frost Guard").formatted(Formatting.RED, Formatting.BOLD));
                     bear.setTarget(player);
                     world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, true, true, x, player.getY() + 1.1, z, 8, 0.45, 0.5, 0.45, 0.01);
                  } else {
                     bear.setCustomName(Text.literal("Ice Emperor's Guard").formatted(Formatting.AQUA, Formatting.BOLD));
                  }

                  bear.setCustomNameVisible(rogue);
                  ids.add(bear.getUuid());
                  spawned++;
                  if (rogue) {
                     hostileSpawned++;
                  }
               }
            }

            if (spawned > 0) {
               this.guardSummonCooldowns.put(ownerIdx, Math.max(0, YPConfig.getInstance().ice_emperor_guard_cooldown_ticks));
               EpicVfx.cue(world, player.getId(), "ice_emperor", "summon", player.getX(), player.getY() + 0.5, player.getZ(), Vec3d.ZERO, 1.1F, 28, 0.0F);
               world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_POLAR_BEAR_WARNING, SoundCategory.PLAYERS, 1.4F, 0.75F);
               world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, player.getX(), player.getY() + 1.0, player.getZ(), 65, 1.5, 1.0, 1.5, 0.045);
               world.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), true, true,
                  player.getX(),
                  player.getY() + 0.8,
                  player.getZ(),
                  24,
                  1.0,
                  0.6,
                  1.0,
                  0.08
               );
               EpicVfx.shockwave(world, player.getX(), player.getY(), player.getZ(), 5.0);
               EpicVfx.ring(world, ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.2, player.getZ(), 0.6, 0.1, 24, 0.02);
               if (lowEnergy) {
                  String suffix = hostileSpawned > 0
                     ? " " + hostileSpawned + " ROGUE BEAR(S) HAVE TURNED ON YOU!"
                     : " The guards are weakened by your depleted Energy.";
                  player.sendMessage(
                     Text.literal("THE FROST GUARD ANSWERS IN WEAKNESS — " + spawned + " bear(s)." + suffix)
                        .formatted(hostileSpawned > 0 ? Formatting.RED : Formatting.YELLOW, Formatting.BOLD),
                     true
                  );
               } else {
                  player.sendMessage(
                     Text.literal("THE FROST GUARD ANSWERS — " + spawned + " polar bear(s) bound to you.").formatted(Formatting.AQUA, Formatting.BOLD), true
                  );
               }
            } else {
               player.sendMessage(Text.literal("The Frost Guard could not find room to manifest.").formatted(Formatting.RED), true);
            }

            return;
         }
      }
   }

   private static String guardOwnerTag(UUID ownerId) {
      return "yppowers_ice_guard_owner:" + ownerId;
   }

   private static String guardHostileTag() {
      return "yppowers_ice_guard_rogue";
   }

   private static void weakenLowEnergyGuard(PolarBearEntity bear) {
      YPConfig config = YPConfig.getInstance();
      EntityAttributeInstance speed = bear.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
      if (speed != null) {
         speed.setBaseValue(Math.max(0.01, speed.getBaseValue() * config.ice_emperor_guard_low_energy_speed_multiplier));
      }

      EntityAttributeInstance attack = bear.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
      if (attack != null) {
         attack.setBaseValue(Math.max(0.1, attack.getBaseValue() * config.ice_emperor_guard_low_energy_damage_multiplier));
      }
   }

   private void recoverNearbyGuards(ServerPlayerEntity owner, ServerWorld world, Set<UUID> ids) {
      int recovery = this.guardRecoveryTicks.getOrDefault(owner.getUuid(), 0);
      if (recovery > 0) {
         this.guardRecoveryTicks.put(owner.getUuid(), recovery - 1);
      } else {
         this.guardRecoveryTicks.put(owner.getUuid(), 100);
         Box scan = owner.getBoundingBox().expand(128.0);

         for (PolarBearEntity bear : world.getEntitiesByClass(
            PolarBearEntity.class, scan, candidate -> candidate.getCommandTags().contains(guardOwnerTag(owner.getUuid())) && candidate.isAlive()
         )) {
            ids.add(bear.getUuid());
         }
      }
   }

   private int countLiveGuards(ServerWorld world, Set<UUID> ids) {
      int alive = 0;
      Iterator<UUID> iterator = ids.iterator();

      while (iterator.hasNext()) {
         UUID id = iterator.next();
         Entity entity = world.getEntity(id);
         if (entity == null) {
            alive++;
         } else if (entity instanceof PolarBearEntity bear
            && bear.isAlive()
            && bear.getCommandTags().stream().anyMatch(tag -> tag.startsWith("yppowers_ice_guard_owner:"))) {
            alive++;
         } else {
            iterator.remove();
         }
      }

      return alive;
   }

   private void tickIceGuards(ServerPlayerEntity owner, ServerWorld world) {
      if (world.getServer() != null) {
         Set<UUID> ids = this.guardBearIds.computeIfAbsent(owner.getUuid(), ignored -> new HashSet<>());
         this.recoverNearbyGuards(owner, world, ids);

         for (UUID id : new ArrayList<>(ids)) {
            if (world.getEntity(id) instanceof PolarBearEntity bear && !bear.isRemoved() && bear.isAlive()) {
               if (bear.getCommandTags().contains(guardHostileTag())) {
                  if (owner.isAlive() && bear.getTarget() != owner) {
                     bear.setTarget(owner);
                  }
               } else {
                  LivingEntity target = owner.getAttacker();
                  if (target == null || !target.isAlive() || this.isGuardAlly(owner, target)) {
                     target = owner.getAttacking();
                  }

                  if (target != null && (!target.isAlive() || target == owner || this.isGuardAlly(owner, target))) {
                     target = null;
                  }

                  LivingEntity current = bear.getTarget();
                  if (current != null && (!current.isAlive() || current == owner || this.isGuardAlly(owner, current))) {
                     bear.setTarget(null);
                     current = null;
                  }

                  if (target != null && bear.getTarget() != target) {
                     bear.setTarget(target);
                  }

                  if (bear.getTarget() == null) {
                     double distance = bear.squaredDistanceTo(owner);
                     if (distance > 16.0) {
                        bear.getNavigation().startMovingTo(owner, 1.25);
                     } else {
                        bear.getNavigation().stop();
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isGuardAlly(ServerPlayerEntity owner, LivingEntity target) {
      return target instanceof ServerPlayerEntity other && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other);
   }

   public static void onIceGuardDeath(LivingEntity deadEntity) {
      if (deadEntity instanceof PolarBearEntity bear && bear.getEntityWorld() instanceof ServerWorld world && world.getServer() != null) {
         String prefix = "yppowers_ice_guard_owner:";
         UUID ownerId = null;

         for (String tag : bear.getCommandTags()) {
            if (tag.startsWith(prefix)) {
               try {
                  ownerId = UUID.fromString(tag.substring(prefix.length()));
               } catch (IllegalArgumentException var9) {
               }
               break;
            }
         }

         if (ownerId != null) {
            if (PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR) instanceof IceEmperorPower ice) {
               Set<UUID> ids = ice.guardBearIds.get(ownerId);
               if (ids != null) {
                  ids.remove(bear.getUuid());
               }
            }

            ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
            if (owner != null && owner.isAlive()) {
               ServerWorld damage = owner.getEntityWorld();
               if (damage instanceof ServerWorld) {
                  float damagex = (float)Math.max(0.0, YPConfig.getInstance().ice_emperor_guard_death_damage);
                  if (damagex > 0.0F) {
                     owner.damage(damage, damage.getDamageSources().magic(), damagex);
                     owner.sendMessage(
                        Text.literal("A Frost Guard has fallen. The bond tears through you for " + damagex + " damage.")
                           .formatted(Formatting.AQUA, Formatting.RED),
                        true
                     );
                     damage.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, owner.getX(), owner.getY() + 1.0, owner.getZ(), 30, 0.7, 0.8, 0.7, 0.03);
                  }

                  return;
               }
            }
         }
      }
   }

   public static boolean shouldGuardIgnore(Entity guard, ServerPlayerEntity target) {
      if (guard instanceof PolarBearEntity bear && bear.getEntityWorld() instanceof ServerWorld world && world.getServer() != null) {
         String prefix = "yppowers_ice_guard_owner:";

         for (String tag : bear.getCommandTags()) {
            if (tag.startsWith(prefix)) {
               try {
                  UUID ownerId = UUID.fromString(tag.substring(prefix.length()));
                  if (ownerId.equals(target.getUuid())) {
                     return !bear.getCommandTags().contains(guardHostileTag());
                  }

                  ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
                  return owner != null && AllianceManager.get(world.getServer()).isAlly(owner, target);
               } catch (IllegalArgumentException var9) {
                  return false;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      ServerWorld world = player.getEntityWorld();
      this.tickForms(player, world);
      int cd = this.absoluteZeroCooldowns.getOrDefault(uuid, 0);
      if (cd > 0) {
         this.absoluteZeroCooldowns.put(uuid, cd - 1);
      }

      int meteorCd = this.meteorCooldowns.getOrDefault(uuid, 0);
      if (meteorCd > 0) {
         this.meteorCooldowns.put(uuid, meteorCd - 1);
      }

      int guardCd = this.guardSummonCooldowns.getOrDefault(uuid, 0);
      if (guardCd > 0) {
         this.guardSummonCooldowns.put(uuid, guardCd - 1);
      }

      this.tickIceGuards(player, world);
      if (world.getServer() != null && PowerManager.get(world.getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         int radius = Math.max(1, (int)Math.ceil(YPConfig.getInstance().ice_emperor_aura_radius));
         if (player.getFrozenTicks() > 0) {
            player.setFrozenTicks(0);
         }

         int auraTicks = this.playerAuraTicks.merge(uuid, 1, Integer::sum);
         if (auraTicks >= 5) {
            this.playerAuraTicks.put(uuid, 0);
            this.applyGlacialAura(player, world, radius);
            this.spawnVisibleGlacialField(player, world, radius);
         }

         Vec3d velocity = player.getVelocity();
         double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
         boolean sprintGliding = player.isSprinting() && horizontalSpeedSquared > 0.0025;
         if (sprintGliding) {
            double trailX = player.getX() - velocity.x * 1.15;
            double trailZ = player.getZ() - velocity.z * 1.15;
            double trailY = player.getY() + 0.06;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, trailX, trailY, trailZ, 3, 0.09, 0.025, 0.09, 0.002);
            if (player.age % 2 == 0) {
               world.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), true, true, trailX, trailY, trailZ, 1, 0.06, 0.015, 0.06, 0.015
               );
            }

            if (player.isOnGround()) {
               BlockPos underfoot = player.getBlockPos().down();
               this.placeTrailIceIfSafe(world, underfoot);
               BlockPos behind = BlockPos.ofFloored(trailX, player.getY(), trailZ).down();
               if (!behind.equals(underfoot)) {
                  this.placeTrailIceIfSafe(world, behind);
               }
            }
         }

         BlockState under = world.getBlockState(player.getBlockPos().down());
         if (isIce(under) || sprintGliding) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 0, false, false, true));
         }

         if (world.getRegistryKey() == World.NETHER) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false, true));
         }
      }
   }

   private void placeTrailIceIfSafe(ServerWorld world, BlockPos pos) {
      IceEmperorPower.TemporaryBlockKey key = new IceEmperorPower.TemporaryBlockKey(world.getRegistryKey(), pos);
      if (this.temporaryBlocks.containsKey(key)) {
         this.placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), 60);
      } else {
         BlockState state = world.getBlockState(pos);
         if (!state.isOf(Blocks.HEAVY_CORE) || !ThorPower.isProtectedHeavyCore(world, pos)) {
            if (!isIce(state) && state.isOpaque() && !state.hasBlockEntity() && !(state.getHardness(world, pos) < 0.0F) && !state.isOf(Blocks.BEDROCK)) {
               this.placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), 60);
            }
         }
      }
   }

   private void spawnVisibleGlacialField(ServerPlayerEntity player, ServerWorld world, int radius) {
      double x = player.getX();
      double z = player.getZ();
      double[] heights = new double[]{0.15, 1.0, 1.85};

      for (double height : heights) {
         for (int angle = 0; angle < 360; angle += 30) {
            double radians = Math.toRadians(angle + player.age * 3 % 360);
            double px = x + Math.cos(radians) * radius;
            double pz = z + Math.sin(radians) * radius;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, px, player.getY() + height, pz, 1, 0.025, 0.04, 0.025, 0.005);
         }
      }

      for (int angle = 0; angle < 360; angle += 45) {
         double radians = Math.toRadians(-angle + player.age * 2 % 360);
         double innerRadius = radius * 0.58;
         world.spawnParticles(
            ParticleTypes.SNOWFLAKE, true, true, x + Math.cos(radians) * innerRadius, player.getY() + 1.0, z + Math.sin(radians) * innerRadius, 1, 0.02, 0.03, 0.02, 0.002
         );
      }
   }

   private void applyGlacialAura(ServerPlayerEntity player, ServerWorld world, int radius) {
      BlockPos center = player.getBlockPos();

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               BlockPos pos = center.add(x, y, z);
               if (!(pos.getSquaredDistance(center) > radius * radius)) {
                  BlockState state = world.getBlockState(pos);
                  if (state.isOf(Blocks.WATER)) {
                     this.placeTemporary(world, pos, Blocks.FROSTED_ICE.getDefaultState(), 100);
                  } else if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
                     world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                  }
               }
            }
         }
      }

      Box box = player.getBoundingBox().expand(radius);
      long now = world.getServer().getTicks();
      YPConfig config = YPConfig.getInstance();
      int damageInterval = Math.max(1, config.ice_emperor_aura_freeze_damage_interval_ticks);
      float freezeDamage = (float)Math.max(0.0, config.ice_emperor_aura_freeze_damage);

      for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, box, e -> e.isAlive() && e.squaredDistanceTo(player) <= radius * radius)) {
         mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, false, true));
         long lastDamageTick = this.lastAuraFreezeDamageTicks.computeIfAbsent(mob.getUuid(), ignored -> now);
         if (freezeDamage > 0.0F && now - lastDamageTick >= damageInterval) {
            mob.damage(world, world.getDamageSources().freeze(), freezeDamage);
            this.lastAuraFreezeDamageTicks.put(mob.getUuid(), now);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, mob.getX(), mob.getY() + 1.0, mob.getZ(), 6, 0.22, 0.24, 0.22, 0.04);
            world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_GLASS_HIT, SoundCategory.HOSTILE, 0.35F, 1.8F);
         }

         world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, mob.getX(), mob.getY() + 1.0, mob.getZ(), 2, 0.2, 0.2, 0.2, 0.05);
      }
   }

   private void cleanupAuraFreezeDamageTicks(long now) {
      this.lastAuraFreezeDamageTicks.entrySet().removeIf(entry -> now - entry.getValue() > 1200L);
   }

   private static boolean isIce(BlockState state) {
      return state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.ICE) || state.isOf(Blocks.BLUE_ICE) || state.isOf(Blocks.FROSTED_ICE);
   }

   private void placeTemporary(ServerWorld world, BlockPos pos, BlockState placed, int ticks) {
      IceEmperorPower.TemporaryBlockKey key = new IceEmperorPower.TemporaryBlockKey(world.getRegistryKey(), pos);
      IceEmperorPower.TemporaryBlock active = this.temporaryBlocks.get(key);
      if (active != null) {
         active.ticks = Math.max(active.ticks, ticks);
         if (world.getBlockState(pos).isOf(active.placed.getBlock())) {
            world.setBlockState(pos, placed, 3);
            active.placed = placed;
         }
      } else {
         BlockState original = world.getBlockState(pos);
         this.temporaryBlocks.put(key, new IceEmperorPower.TemporaryBlock(world, pos, original, placed, ticks));
         world.setBlockState(pos, placed, 3);
      }
   }

   private void tickTemporaryBlocks() {
      Iterator<Entry<IceEmperorPower.TemporaryBlockKey, IceEmperorPower.TemporaryBlock>> iterator = this.temporaryBlocks.entrySet().iterator();

      while (iterator.hasNext()) {
         IceEmperorPower.TemporaryBlock block = iterator.next().getValue();
         if (--block.ticks <= 0) {
            if (block.world.getBlockState(block.pos).isOf(block.placed.getBlock())) {
               block.world.setBlockState(block.pos, block.original, 3);
            }

            iterator.remove();
         }
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, float amount) {
      if (player.getEntityWorld().getServer() != null && PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         return isIce(player.getEntityWorld().getBlockState(player.getBlockPos().down())) ? amount * 1.2F : amount;
      } else {
         return amount;
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
      amount = this.modifyOutgoingDamage(player, target, amount);
      if (source != null
         && source.isOf(DamageTypes.PLAYER_ATTACK)
         && source.getAttacker() == player
         && VanillaMeleeTracker.isActive(player)
         && player.getEntityWorld().getServer() != null
         && !GraceManager.isActive(player.getEntityWorld().getServer())
         && YPConfig.getInstance().ice_emperor_forms_enabled
         && PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         YPConfig config = YPConfig.getInstance();

         return switch (this.selectedForm(player)) {
            case ARMOURED -> amount * (float)config.ice_emperor_armoured_outgoing_damage_multiplier;
            case STEALTH -> amount;
            case ASSASSIN -> amount * (float)config.ice_emperor_assassin_melee_damage_multiplier;
         };
      } else {
         return amount;
      }
   }

   @Override
   public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
      if (player.getEntityWorld().getServer() == null || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         return amount;
      } else if (source.isOf(DamageTypes.FREEZE)) {
         return 0.0F;
      } else {
         return !source.isIn(DamageTypeTags.IS_FIRE)
               && !source.isOf(DamageTypes.LAVA)
               && !source.isOf(DamageTypes.ON_FIRE)
               && !source.isOf(DamageTypes.IN_FIRE)
            ? amount
            : amount * 1.3F;
      }
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null && PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         UUID uuid = player.getUuid();
         int cooldown = this.meteorCooldowns.getOrDefault(uuid, 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Ice Meteor on cooldown: " + (cooldown + 19) / 20 + "s"), true);
         } else {
            ServerWorld world = player.getEntityWorld();
            BlockPos target = player.getBlockPos().offset(player.getHorizontalFacing(), 18);
            double x = target.getX() + 0.5;
            double z = target.getZ() + 0.5;
            int surfaceY = world.getTopY(Type.MOTION_BLOCKING, target.getX(), target.getZ());
            double impactY = Math.max((double)(world.getBottomY() + 2), surfaceY + 1.0);
            IceEmperorPower.IceMeteor meteor = new IceEmperorPower.IceMeteor(world, uuid, x, Math.max(impactY + 36.0, player.getY() + 36.0), z, impactY);

            for (int i = 0; i < 64; i++) {
               double yy = 1.0 - 2.0 * (i + 0.5) / 64.0;
               double ring = Math.sqrt(Math.max(0.0, 1.0 - yy * yy));
               double angle = i * Math.PI * (3.0 - Math.sqrt(5.0));
               Entity core = EntityType.SNOWBALL.create(world, SpawnReason.TRIGGERED);
               if (core != null) {
                  core.setNoGravity(true);
                  core.setPosition(x + Math.cos(angle) * ring * 3.0, meteor.y + yy * 3.0, z + Math.sin(angle) * ring * 3.0);
                  if (world.spawnEntity(core)) {
                     meteor.coreEntities.add(core);
                  }
               }
            }

            this.activeMeteors.put(uuid, meteor);
            this.meteorCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().ice_emperor_meteor_cooldown_ticks));
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.2F, 0.55F);
            EpicVfx.cue(world, player.getId(), "ice_emperor", "telegraph", x, impactY, z, Vec3d.ZERO, 1.3F, 36, 0.0F);
            EpicVfx.activation(world, x, impactY, z, 1.3);
            EpicVfx.column(world, ParticleTypes.SNOWFLAKE, x, impactY, z, 18.0, 14);
            player.sendMessage(Text.literal("ICE METEOR INCOMING!").formatted(Formatting.AQUA, Formatting.BOLD), true);
         }
      } else {
         player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
      }
   }

   private void tickIceMeteors(MinecraftServer server) {
      Iterator<Entry<UUID, IceEmperorPower.IceMeteor>> meteorIterator = this.activeMeteors.entrySet().iterator();

      while (meteorIterator.hasNext()) {
         IceEmperorPower.IceMeteor meteor = meteorIterator.next().getValue();
         meteor.y--;

         for (Entity entity : meteor.coreEntities) {
            if (!entity.isRemoved()) {
               double dx = entity.getX() - meteor.x;
               double dy = entity.getY() - (meteor.y + (entity.getY() - meteor.y));
               double dz = entity.getZ() - meteor.z;
               entity.setPosition(meteor.x + dx, meteor.y + (entity.getY() - (meteor.y + 1.35)), meteor.z + dz);
            }
         }

         for (int i = 0; i < 48; i++) {
            double yy = 1.0 - 2.0 * (i + 0.5) / 48.0;
            double ring = Math.sqrt(Math.max(0.0, 1.0 - yy * yy));
            double angle = i * Math.PI * (3.0 - Math.sqrt(5.0)) + server.getTicks() * 0.045;
            meteor.world
               .spawnParticles(
                  ParticleTypes.SNOWFLAKE, true, true,
                  meteor.x + Math.cos(angle) * ring * 4.1,
                  meteor.y + yy * 4.1,
                  meteor.z + Math.sin(angle) * ring * 4.1,
                  1,
                  0.04,
                  0.04,
                  0.04,
                  0.01
               );
         }

         meteor.world
            .spawnParticles(
               new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), true, true, meteor.x, meteor.y, meteor.z, 12, 1.2, 1.2, 1.2, 0.08
            );
         if (meteor.y <= meteor.impactY) {
            for (Entity entityx : meteor.coreEntities) {
               if (!entityx.isRemoved()) {
                  entityx.discard();
               }
            }

            this.impactIceMeteor(meteor);
            meteorIterator.remove();
         }
      }

      Iterator<Entry<UUID, IceEmperorPower.Snowstorm>> stormIterator = this.activeSnowstorms.entrySet().iterator();

      while (stormIterator.hasNext()) {
         IceEmperorPower.Snowstorm storm = stormIterator.next().getValue();
         if (--storm.ticks <= 0) {
            stormIterator.remove();
         } else {
            for (int i = 0; i < 12; i++) {
               double angle = Math.random() * Math.PI * 2.0;
               double radius = Math.random() * 12.0;
               storm.world
                  .spawnParticles(
                     ParticleTypes.SNOWFLAKE, true, true,
                     storm.x + Math.cos(angle) * radius,
                     storm.y + Math.random() * 8.0,
                     storm.z + Math.sin(angle) * radius,
                     1,
                     0.12,
                     0.08,
                     0.12,
                     0.02
                  );
            }
         }
      }
   }

   private void impactIceMeteor(IceEmperorPower.IceMeteor meteor) {
      YPConfig config = YPConfig.getInstance();
      double radius = Math.max(1.0, Math.min(128.0, config.ice_emperor_meteor_radius));
      int blockRadius = (int)Math.ceil(radius);
      BlockPos center = BlockPos.ofFloored(meteor.x, meteor.impactY, meteor.z);

      for (int dx = -blockRadius; dx <= blockRadius; dx++) {
         for (int dz = -blockRadius; dz <= blockRadius; dz++) {
            if (!((double)dx * dx + (double)dz * dz > radius * radius)) {
               int topY = meteor.world.getTopY(Type.MOTION_BLOCKING, center.getX() + dx, center.getZ() + dz);
               BlockPos ground = new BlockPos(center.getX() + dx, topY - 1, center.getZ() + dz);
               BlockPos cover = ground.up();
               BlockState state = meteor.world.getBlockState(cover);
               if (state.isReplaceable() && !state.hasBlockEntity() && meteor.world.getBlockState(ground).isOpaque()) {
                  this.placeTemporary(meteor.world, cover, Blocks.PACKED_ICE.getDefaultState(), Math.max(20, config.ice_emperor_meteor_ice_duration_ticks));
               }
            }
         }
      }

      ServerPlayerEntity caster = meteor.world.getServer().getPlayerManager().getPlayer(meteor.caster);
      AllianceManager alliances = AllianceManager.get(meteor.world.getServer());
      Box affected = new Box(meteor.x - radius, meteor.impactY - 8.0, meteor.z - radius, meteor.x + radius, meteor.impactY + 12.0, meteor.z + radius);
      int effectTicks = Math.max(20, config.ice_emperor_meteor_effect_duration_ticks);

      for (LivingEntity target : meteor.world
         .getEntitiesByClass(
            LivingEntity.class,
            affected,
            e -> e.isAlive()
               && e.squaredDistanceTo(meteor.x, meteor.impactY, meteor.z) <= radius * radius
               && e != caster
               && (caster == null || !alliances.isAlly(caster, e))
         )) {
         target.damage(meteor.world, meteor.world.getDamageSources().freeze(), (float)Math.max(0.0, config.ice_emperor_meteor_damage));
         target.setFrozenTicks(Math.max(target.getFrozenTicks(), effectTicks));
         target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, effectTicks, 3, false, true, true));
         target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, effectTicks, 0, false, true, true));
      }

      if (caster != null) {
         caster.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, effectTicks, 1, false, true, true));
      }

      this.activeSnowstorms.put(meteor.caster, new IceEmperorPower.Snowstorm(meteor.world, meteor.caster, meteor.x, meteor.impactY, meteor.z, effectTicks));
      meteor.world.spawnParticles(ParticleTypes.EXPLOSION, true, true, meteor.x, meteor.impactY + 1.0, meteor.z, 8, 2.5, 1.0, 2.5, 0.05);
      EpicVfx.cue(
         meteor.world,
         caster == null ? -1 : caster.getId(),
         "ice_emperor",
         "frost_impact",
         meteor.x,
         meteor.impactY + 1.0,
         meteor.z,
         Vec3d.ZERO,
         1.8F,
         32,
         0.3F
      );
      EpicVfx.impact(meteor.world, meteor.x, meteor.impactY + 1.0, meteor.z, 1.7);
      meteor.world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, meteor.x, meteor.impactY + 1.0, meteor.z, 140, radius / 3.0, 1.8, radius / 3.0, 0.12);
      meteor.world.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0F, 0.6F);
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      if (player.getEntityWorld().getServer() != null && PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
         UUID uuid = player.getUuid();
         int cooldown = this.absoluteZeroCooldowns.getOrDefault(uuid, 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Absolute Zero on cooldown: " + cooldown / 20 + "s"), true);
         } else {
            ServerWorld world = player.getEntityWorld();
            AllianceManager alliances = AllianceManager.get(world.getServer());
            Box box = player.getBoundingBox().expand(10.0);

            for (LivingEntity target : world.getEntitiesByClass(
               LivingEntity.class, box, e -> e != player && e.squaredDistanceTo(player) <= 100.0 && !alliances.isAlly(player, e)
            )) {
               target.damage(world, world.getDamageSources().magic(), 4.0F);
               if (!target.isRemoved() && target.isAlive()) {
                  target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3, false, false, true));
                  this.freezeEntity(target, Math.max(1, YPConfig.getInstance().ice_emperor_freeze_duration_ticks), world.getServer());
                  BlockPos center = target.getBlockPos();

                  for (int x = -1; x <= 1; x++) {
                     for (int y = 0; y <= 2; y++) {
                        for (int z = -1; z <= 1; z++) {
                           boolean shell = x == -1 || x == 1 || z == -1 || z == 1 || y == 0 || y == 2;
                           if (shell) {
                              BlockPos pos = center.add(x, y, z);
                              if (world.getBlockState(pos).isReplaceable() && !world.getBlockState(pos).hasBlockEntity()) {
                                 this.placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), YPConfig.getInstance().ice_emperor_freeze_duration_ticks);
                              }
                           }
                        }
                     }
                  }

                  world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, target.getX(), target.getY() + 1.0, target.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
                  world.spawnParticles(
                     new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), true, true,
                     target.getX(),
                     target.getY() + 1.0,
                     target.getZ(),
                     20,
                     0.5,
                     0.5,
                     0.5,
                     0.1
                  );
               }
            }

            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0F, 0.5F);
            EpicVfx.shockwave(world, player.getX(), player.getY(), player.getZ(), 10.0);
            EpicVfx.ring(world, ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.5, player.getZ(), 0.5, 0.45, 40, 0.02);
            EpicVfx.column(world, ParticleTypes.SNOWFLAKE, player.getX(), player.getY(), player.getZ(), 8.0, 16);
            EpicVfx.flash(world, player.getX(), player.getY() + 1.0, player.getZ());

            for (int angle = 0; angle < 360; angle += 10) {
               double radians = Math.toRadians(angle);
               world.spawnParticles(
                  ParticleTypes.SNOWFLAKE, true, true,
                  player.getX() + Math.cos(radians) * 10.0,
                  player.getY() + 0.5,
                  player.getZ() + Math.sin(radians) * 10.0,
                  1,
                  0.0,
                  0.0,
                  0.0,
                  0.1
               );
            }

            this.absoluteZeroCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().ice_emperor_cooldown_ticks));
         }
      } else {
         player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
      }
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.activeHeatBlasts.remove(player.getUuid());
      this.clearFormEffects(player);
      this.formLastDimension.remove(player.getUuid());
      if (player.getEntityWorld().getServer() != null) {
         PowerManager.get(player.getEntityWorld().getServer()).clearIceEmperorForm(player.getUuid());
         YPNetworking.syncIceEmperorForm(player.getEntityWorld().getServer(), player.getUuid(), "ARMOURED", false);
      }

      this.formActiveSyncState.remove(player.getUuid());
      this.absoluteZeroCooldowns.remove(player.getUuid());
      this.meteorCooldowns.remove(player.getUuid());
      this.guardSummonCooldowns.remove(player.getUuid());
      this.discardOwnedGuards(player);
      MinecraftServer server = player.getEntityWorld().getServer();
      IceEmperorPower.IceMeteor meteor = this.activeMeteors.remove(player.getUuid());
      if (meteor != null) {
         for (Entity entity : meteor.coreEntities) {
            if (!entity.isRemoved()) {
               entity.discard();
            }
         }
      }

      this.activeSnowstorms.remove(player.getUuid());
      this.playerAuraTicks.remove(player.getUuid());
      player.removeStatusEffect(StatusEffects.SPEED);
      player.removeStatusEffect(StatusEffects.WEAKNESS);
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.absoluteZeroCooldowns.remove(playerId);
      this.meteorCooldowns.remove(playerId);
      this.guardSummonCooldowns.remove(playerId);
   }

   private static final class HeatBlast {
      final ServerWorld world;
      final UUID caster;
      final Vec3d center;
      final Set<UUID> affected = new HashSet<>();
      int ticks;

      HeatBlast(ServerWorld world, UUID caster, Vec3d center) {
         this.world = world;
         this.caster = caster;
         this.center = center;
      }
   }

   public static enum IceForm {
      ARMOURED("Armoured"),
      STEALTH("Stealth"),
      ASSASSIN("Assassin");

      private final String displayName;

      private IceForm(String displayName) {
         this.displayName = displayName;
      }

      public String displayName() {
         return this.displayName;
      }

      public static IceEmperorPower.IceForm parse(String value) {
         if (value == null) {
            return ARMOURED;
         } else {
            try {
               return valueOf(value);
            } catch (IllegalArgumentException var2) {
               return ARMOURED;
            }
         }
      }
   }

   private static final class IceFrozenEntity {
      final Vec3d position;
      final Vec3d velocity;
      final float yaw;
      final float pitch;
      long expiresAt;

      IceFrozenEntity(Entity entity, Vec3d originalVelocity, long expiresAt) {
         this.position = entity.getEntityPos();
         this.velocity = originalVelocity;
         this.yaw = entity.getYaw();
         this.pitch = entity.getPitch();
         this.expiresAt = expiresAt;
      }
   }

   private static final class IceMeteor {
      final ServerWorld world;
      final UUID caster;
      final double x;
      final double z;
      double y;
      final double impactY;
      final List<Entity> coreEntities = new ArrayList<>();

      IceMeteor(ServerWorld world, UUID caster, double x, double y, double z, double impactY) {
         this.world = world;
         this.caster = caster;
         this.x = x;
         this.y = y;
         this.z = z;
         this.impactY = impactY;
      }
   }

   private static final class Snowstorm {
      final ServerWorld world;
      final UUID caster;
      final double x;
      final double y;
      final double z;
      int ticks;

      Snowstorm(ServerWorld world, UUID caster, double x, double y, double z, int ticks) {
         this.world = world;
         this.caster = caster;
         this.x = x;
         this.y = y;
         this.z = z;
         this.ticks = ticks;
      }
   }

   private static final class TemporaryBlock {
      final ServerWorld world;
      final BlockPos pos;
      final BlockState original;
      BlockState placed;
      int ticks;

      TemporaryBlock(ServerWorld world, BlockPos pos, BlockState original, BlockState placed, int ticks) {
         this.world = world;
         this.pos = pos.toImmutable();
         this.original = original;
         this.placed = placed;
         this.ticks = ticks;
      }
   }

   private static final class TemporaryBlockKey {
      final RegistryKey<World> dimension;
      final BlockPos pos;

      TemporaryBlockKey(RegistryKey<World> dimension, BlockPos pos) {
         this.dimension = dimension;
         this.pos = pos.toImmutable();
      }

      @Override
      public boolean equals(Object other) {
         return other instanceof IceEmperorPower.TemporaryBlockKey key && this.dimension.equals(key.dimension) && this.pos.equals(key.pos);
      }

      @Override
      public int hashCode() {
         return 31 * this.dimension.hashCode() + this.pos.hashCode();
      }
   }
}
