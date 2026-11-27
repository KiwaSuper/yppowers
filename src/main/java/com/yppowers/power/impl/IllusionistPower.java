package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.RitualManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class IllusionistPower extends Power {
   private static final int CRATER_TICKS = 16;
   private static final Map<UUID, Integer> PENDING_GRAVITY_RESTORE = new HashMap<>();
   private static final Map<UUID, IllusionistPower.Erasure> ACTIVE_ERASURES = new HashMap<>();
   private static final Map<UUID, IllusionistPower.PullState> ACTIVE_PULLS = new HashMap<>();
   private final Map<UUID, Integer> erasureCooldowns = new HashMap<>();
   private final Map<UUID, Integer> gripCooldowns = new HashMap<>();
   private final Map<UUID, Integer> pullCooldowns = new HashMap<>();
   private final Set<UUID> erasureArmed = new HashSet<>();
   private final Map<UUID, Entity> grips = new HashMap<>();
   private final Map<UUID, Double> gripDistances = new HashMap<>();
   private final Map<UUID, Integer> gripPassiveClock = new HashMap<>();
   private final Map<UUID, IllusionistPower.CloudFlight> cloudFlights = new HashMap<>();
   private final Map<UUID, Integer> cloudFlightCooldowns = new HashMap<>();

   @Override
   public PowerType getType() {
      return PowerType.ILLUSIONIST;
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      if (this.erasureArmed.remove(player.getUuid())) {
         player.sendMessage(Text.literal("Spatial Erasure cancelled."), true);
      } else {
         this.erasureArmed.add(player.getUuid());
         player.sendMessage(Text.literal("Spatial Erasure armed — right-click the center block of a 3×3×3 area.").formatted(Formatting.LIGHT_PURPLE), true);
      }
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      this.toggleGrip(player);
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      this.toggleCloudFlight(player);
   }

   @Override
   public boolean canUseKey(ServerPlayerEntity player, int keyType) {
      if (keyType == 2 && !this.cloudFlights.containsKey(player.getUuid())) {
         int cooldown = this.cloudFlightCooldowns.getOrDefault(player.getUuid(), 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Cloud Flight cooldown: " + (cooldown + 19) / 20 + "s"), true);
            return false;
         } else {
            return true;
         }
      } else {
         return true;
      }
   }

   public boolean isCloudFlightActive(UUID playerId) {
      return this.cloudFlights.containsKey(playerId);
   }

   private void toggleCloudFlight(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (this.cloudFlights.containsKey(id)) {
         this.endCloudFlight(player, true, true);
      } else if (!GraceManager.blockAbility(player)) {
         if (player.isSpectator()) {
            player.sendMessage(Text.literal("Cloud Flight cannot be used in spectator mode."), true);
         } else {
            YPConfig config = YPConfig.getInstance();
            this.cloudFlights.put(id, new IllusionistPower.CloudFlight(player));
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
            ServerWorld var5 = player.getEntityWorld();
            if (var5 instanceof ServerWorld) {
               var5.spawnParticles(ParticleTypes.CLOUD, true, true, player.getX(), player.getY() - 0.65, player.getZ(), 32, 1.05, 0.22, 1.05, 0.025);
               var5.spawnParticles(ParticleTypes.END_ROD, true, true, player.getX(), player.getY() - 0.45, player.getZ(), 12, 0.65, 0.12, 0.65, 0.015);
               var5.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 0.9F, 1.5F);
            }

            player.sendMessage(Text.literal("Cloud Flight activated — V again to descend safely.").formatted(Formatting.AQUA), true);
         }
      }
   }

   private void endCloudFlight(ServerPlayerEntity player, boolean announce, boolean safeDescent) {
      UUID id = player.getUuid();
      IllusionistPower.CloudFlight state = this.cloudFlights.remove(id);
      if (state != null) {
         if (announce) {
            this.cloudFlightCooldowns.put(id, Math.max(0, YPConfig.getInstance().illusionist_cloud_flight_cooldown_ticks));
         }

         player.getAbilities().allowFlying = state.previousAllowFlying;
         player.getAbilities().flying = state.previousFlying;
         player.sendAbilitiesUpdate();
         if (safeDescent && player.isAlive() && !player.isCreative() && !player.isSpectator()) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0));
         }

         if (announce) {
            player.sendMessage(Text.literal("Your cloud disperses."), true);
         }

         ServerWorld var7 = player.getEntityWorld();
         if (var7 instanceof ServerWorld) {
            var7.spawnParticles(ParticleTypes.CLOUD, true, true, player.getX(), player.getY() - 0.45, player.getZ(), 22, 0.8, 0.18, 0.8, 0.02);
            var7.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.55F, 0.8F);
         }
      }
   }

   private void tickCloudFlight(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      int cooldown = this.cloudFlightCooldowns.getOrDefault(id, 0);
      if (cooldown > 0) {
         if (cooldown == 1) {
            this.cloudFlightCooldowns.remove(id);
         } else {
            this.cloudFlightCooldowns.put(id, cooldown - 1);
         }
      }

      IllusionistPower.CloudFlight state = this.cloudFlights.get(id);
      if (state != null) {
         if (!player.isAlive()) {
            this.endCloudFlight(player, false, false);
         } else if (player.getEntityWorld().getServer() != null && GraceManager.isActive(player.getEntityWorld().getServer())) {
            this.endCloudFlight(player, true, true);
         } else {
            if (!player.getAbilities().allowFlying) {
               player.getAbilities().allowFlying = true;
               player.sendAbilitiesUpdate();
            }

            ServerWorld cloudY = player.getEntityWorld();
            if (cloudY instanceof ServerWorld) {
               ServerWorld world = cloudY;
               if (player.age % 2 == 0) {
                  double cloudYx = player.getY() - 0.62;
                  double rotation = player.age * 0.055;

                  for (int i = 0; i < 12; i++) {
                     double angle = rotation + (Math.PI * 2) * i / 12.0;
                     double radius = 1.0 + 0.12 * Math.sin(angle * 3.0);
                     double x = player.getX() + Math.cos(angle) * radius;
                     double z = player.getZ() + Math.sin(angle) * radius;
                     world.spawnParticles(ParticleTypes.CLOUD, true, true, x, cloudYx + 0.08 * Math.sin(angle * 2.0), z, 1, 0.045, 0.035, 0.045, 0.002);
                     if (i % 3 == 0) {
                        world.spawnParticles(ParticleTypes.SNOWFLAKE, true, true, x, cloudYx + 0.08, z, 1, 0.025, 0.025, 0.025, 0.001);
                     }
                  }

                  world.spawnParticles(ParticleTypes.CLOUD, true, true, player.getX(), cloudYx - 0.05, player.getZ(), 2, 0.32, 0.08, 0.32, 0.003);
               }
            }
         }
      }
   }

   @Override
   public void onQuaternaryActivate(ServerPlayerEntity player) {
      this.throwGrip(player);
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (!this.grips.containsKey(id)) {
         player.sendMessage(Text.literal("Grab an entity first with H."), true);
      } else {
         double next = Math.min(10.0, Math.max(2.0, this.gripDistances.getOrDefault(id, YPConfig.getInstance().illusionist_grip_default_distance) + 1.0));
         this.gripDistances.put(id, next);
         player.sendMessage(Text.literal("Psychokinetic Grip distance: " + next + " blocks"), true);
      }
   }

   public boolean trySpatialErasure(ServerPlayerEntity player, BlockPos center) {
      ServerWorld owner = player.getEntityWorld();
      if (owner instanceof ServerWorld) {
         ServerWorld world = owner;
         if (this.erasureArmed.remove(player.getUuid())) {
            UUID ownerx = player.getUuid();
            int cooldown = this.erasureCooldowns.getOrDefault(ownerx, 0);
            if (cooldown > 0) {
               player.sendMessage(Text.literal("Spatial Erasure cooldown: " + (cooldown + 19) / 20 + "s"), true);
               return true;
            }

            List<IllusionistPower.SavedBlock> saved = new ArrayList<>();

            for (int dx = -1; dx <= 1; dx++) {
               for (int dy = -1; dy <= 1; dy++) {
                  for (int dz = -1; dz <= 1; dz++) {
                     BlockPos pos = center.add(dx, dy, dz);
                     BlockState state = world.getBlockState(pos);
                     if (!state.isAir()) {
                        if (state.isOf(Blocks.BEDROCK)
                           || state.isOf(Blocks.END_PORTAL)
                           || state.isOf(Blocks.END_PORTAL_FRAME)
                           || state.isOf(Blocks.NETHER_PORTAL)
                           || world.getBlockEntity(pos) != null
                           || state.getHardness(world, pos) < 0.0F
                           || this.isReserved(world, pos)) {
                           player.sendMessage(
                              Text.literal("Spatial Erasure cancelled: protected, container, or overlapping block found.").formatted(Formatting.RED), true
                           );
                           return true;
                        }

                        saved.add(new IllusionistPower.SavedBlock(world, pos.toImmutable(), state));
                     }
                  }
               }
            }

            for (IllusionistPower.SavedBlock b : saved) {
               world.setBlockState(b.pos(), Blocks.AIR.getDefaultState(), 3);
            }

            ACTIVE_ERASURES.put(ownerx, new IllusionistPower.Erasure(saved, Math.max(1, YPConfig.getInstance().illusionist_spatial_erasure_duration_ticks)));
            this.erasureCooldowns.put(ownerx, Math.max(0, YPConfig.getInstance().illusionist_spatial_erasure_cooldown_ticks));
            world.spawnParticles(ParticleTypes.PORTAL, true, true, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 70, 1.5, 1.5, 1.5, 0.35);
            EpicVfx.activation(world, center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 1.4);
            EpicVfx.implosion(world, ParticleTypes.REVERSE_PORTAL, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, 5.0, 24, 0.3);
            EpicVfx.cue(
               world, player.getId(), "illusionist", "phase", center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, Vec3d.ZERO, 1.0F, 24, 0.0F
            );
            world.playSound(null, center, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0F, 0.8F);
            return true;
         }
      }

      return false;
   }

   private boolean isReserved(ServerWorld world, BlockPos pos) {
      for (IllusionistPower.Erasure e : ACTIVE_ERASURES.values()) {
         for (IllusionistPower.SavedBlock b : e.blocks) {
            if (b.world() == world && b.pos().equals(pos)) {
               return true;
            }
         }
      }

      return false;
   }

   private void toggleGrip(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      if (this.grips.containsKey(id)) {
         this.releaseGrip(player, false);
      } else if (player.getEntityWorld().getServer() != null && EnergyManager.get(player.getEntityWorld().getServer()).getEnergy(id) <= 1.0E-9) {
         player.sendMessage(Text.literal("You need Energy to maintain Psychokinetic Grip."), true);
      } else {
         int cd = this.gripCooldowns.getOrDefault(id, 0);
         if (cd > 0) {
            player.sendMessage(Text.literal("Grip release cooldown: " + (cd + 19) / 20 + "s"), true);
         } else {
            Entity target = this.findLookEntity(player, Math.max(1.0, YPConfig.getInstance().illusionist_grip_range));
            if (target == null) {
               player.sendMessage(Text.literal("Look at an entity or dropped item within 15 blocks."), true);
            } else {
               this.grips.put(id, target);
               this.gripDistances.put(id, Math.max(2.0, Math.min(10.0, YPConfig.getInstance().illusionist_grip_default_distance)));
               ServerWorld var6 = player.getEntityWorld();
               if (var6 instanceof ServerWorld) {
                  EpicVfx.activation(var6, target.getX(), target.getY(), target.getZ(), 0.8);
                  EpicVfx.cue(
                     var6,
                     player.getId(),
                     "illusionist",
                     "aura",
                     target.getX(),
                     target.getY() + target.getHeight() * 0.5,
                     target.getZ(),
                     Vec3d.ZERO,
                     0.8F,
                     20,
                     0.0F
                  );
               }

               player.sendMessage(
                  Text.literal("Psychokinetic Grip engaged. B throws; R adjusts distance; H releases.").formatted(Formatting.LIGHT_PURPLE), true
               );
               player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7F, 1.5F);
            }
         }
      }
   }

   private Entity findLookEntity(ServerPlayerEntity player, double range) {
      ServerWorld start = player.getEntityWorld();
      if (!(start instanceof ServerWorld)) {
         return null;
      } else {
         Vec3d var20 = player.getEyePos();
         Vec3d dir = PowerCombatUtil.safeLook(player);
         Box box = player.getBoundingBox().stretch(dir.multiply(range)).expand(1.2);
         Entity best = null;
         double bestDistance = range;
         double bestDot = 0.94;

         for (Entity e : start.getOtherEntities(
            player,
            box,
            x -> x.isAlive()
               && (x instanceof LivingEntity || x instanceof ItemEntity || x instanceof ExperienceOrbEntity)
               && !(x instanceof ServerPlayerEntity ally && AllianceManager.get(start.getServer()).isAlly(player, ally))
         )) {
            Vec3d toward = e.getBoundingBox().getCenter().subtract(var20);
            double d = toward.length();
            if (!(d < 0.1) && !(d > range)) {
               double dot = dir.dotProduct(toward.normalize());
               if (dot > bestDot || Math.abs(dot - bestDot) < 1.0E-4 && d < bestDistance) {
                  best = e;
                  bestDot = dot;
                  bestDistance = d;
               }
            }
         }

         return best;
      }
   }

   private void throwGrip(ServerPlayerEntity player) {
      Entity target = this.grips.get(player.getUuid());
      if (target == null) {
         player.sendMessage(Text.literal("No held target."), true);
      } else {
         this.releaseGrip(player, true);
      }
   }

   private void releaseGrip(ServerPlayerEntity player, boolean thrown) {
      UUID id = player.getUuid();
      Entity target = this.grips.remove(id);
      this.gripDistances.remove(id);
      if (target != null && !target.isRemoved() && thrown) {
         Vec3d v = PowerCombatUtil.safeLook(player).multiply(Math.max(0.1, YPConfig.getInstance().illusionist_grip_throw_multiplier));
         target.setVelocity(v);
         ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
         RitualManager.trackThrownMob(player, target);
         if (target instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20, 0, false, false));
         }

         if (target.getEntityWorld() instanceof ServerWorld world) {
            EpicVfx.sphereBurst(world, ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(), 24, 0.3);
         }

         player.sendMessage(Text.literal("Telekinetic throw released."), true);
      } else if (target != null && !target.isRemoved()) {
         target.setVelocity(Vec3d.ZERO);
         ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
      }

      this.gripCooldowns.put(id, Math.max(0, YPConfig.getInstance().illusionist_grip_release_cooldown_ticks));
   }

   public static void onOwnerDamaged(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.ILLUSIONIST) instanceof IllusionistPower illusionist) {
            illusionist.breakGrip(player, "Psychokinetic Grip broken: you took damage.");
         }
      }
   }

   public static void onOwnerEnergyDepleted(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.ILLUSIONIST) instanceof IllusionistPower illusionist) {
            illusionist.breakGrip(player, "Psychokinetic Grip released: Energy depleted.");
         }
      }
   }

   private void breakGrip(ServerPlayerEntity player, String message) {
      if (this.grips.containsKey(player.getUuid())) {
         this.releaseGrip(player, false);
         player.sendMessage(Text.literal(message).formatted(Formatting.YELLOW), true);
      }
   }

   private void celestialPull(ServerPlayerEntity player) {
      ServerWorld id = player.getEntityWorld();
      if (id instanceof ServerWorld) {
         UUID var33 = player.getUuid();
         int cd = this.pullCooldowns.getOrDefault(var33, 0);
         if (cd > 0) {
            player.sendMessage(Text.literal("Celestial Pull cooldown: " + (cd + 19) / 20 + "s"), true);
         } else {
            Vec3d look = PowerCombatUtil.safeLook(player);
            Vec3d endpoint = player.getEyePos().add(look.multiply(40.0));
            BlockHitResult hit = id.raycast(new RaycastContext(player.getEyePos(), endpoint, ShapeType.OUTLINE, FluidHandling.NONE, player));
            BlockPos targetPos = hit.getType() == Type.BLOCK ? hit.getBlockPos() : BlockPos.ofFloored(endpoint);
            int ground = id.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, targetPos.getX(), targetPos.getZ());
            Vec3d center = new Vec3d(targetPos.getX() + 0.5, Math.max(id.getBottomY() + 1, ground), targetPos.getZ() + 0.5);
            if (!id.getWorldBorder().contains(BlockPos.ofFloored(center))) {
               player.sendMessage(Text.literal("Celestial Pull target is outside the world border."), true);
            } else {
               YPConfig cfg = YPConfig.getInstance();
               double moonRadius = Math.max(2.0, Math.min(14.0, cfg.illusionist_celestial_moon_radius * Math.max(0.5, cfg.illusionist_celestial_moon_scale)));
               double moonHeight = Math.max(Math.max(10.0, cfg.illusionist_celestial_moon_height), moonRadius * 2.8);
               double maxMoonY = id.getBottomY() + id.getHeight() - moonRadius - 2.0;
               Vec3d moon = new Vec3d(center.x, Math.min(center.y + moonHeight, maxMoonY), center.z);
               int duration = Math.max(1, Math.min(560, cfg.illusionist_celestial_pull_duration_ticks));
               IllusionistPower.PullState state = new IllusionistPower.PullState(
                  var33,
                  id,
                  center,
                  moon,
                  moonRadius,
                  moon.y - center.y,
                  Math.max(Math.max(cfg.illusionist_celestial_crater_radius, cfg.illusionist_celestial_pull_radius), moonRadius * 1.8),
                  cfg.illusionist_celestial_weightless_radius,
                  duration
               );
               List<BlockPos> shell = new ArrayList<>();
               int reach = (int)Math.ceil(moonRadius);

               for (int dx = -reach; dx <= reach; dx++) {
                  for (int dy = -reach; dy <= reach; dy++) {
                     for (int dz = -reach; dz <= reach; dz++) {
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (!(d > moonRadius) && !(d < moonRadius - 1.15) && (dx + dy + dz & 1) == 0) {
                           shell.add(BlockPos.ofFloored(moon.x + dx, moon.y + dy, moon.z + dz));
                        }
                     }
                  }
               }

               double centerY = moon.y;
               shell.sort(Comparator.comparingDouble(pos -> {
                  double angle = Math.atan2(pos.getZ() + 0.5 - moon.z, pos.getX() + 0.5 - moon.x) / (Math.PI * 2) + 0.5;
                  double height = (pos.getY() + 0.5 - centerY + moonRadius) / (2.0 * moonRadius);
                  return height * 3.0 + angle;
               }));
               int budget = Math.max(100, cfg.illusionist_celestial_moon_max_blocks);
               if (shell.size() > budget) {
                  List<BlockPos> thinned = new ArrayList<>(budget);
                  double step = (double)shell.size() / budget;

                  for (double i = 0.0; i < shell.size() && thinned.size() < budget; i += step) {
                     thinned.add(shell.get((int)i));
                  }

                  shell = thinned;
               }

               state.totalPlanned = shell.size();
               state.assembleBatch = Math.max(8, (int)Math.ceil(shell.size() / 24.0));
               state.removeBatch = Math.max(40, (int)Math.ceil(shell.size() / 8.0));
               state.pending.addAll(shell);
               ACTIVE_PULLS.put(var33, state);
               applyWeightless(id.getServer(), state, player);
               this.pullCooldowns.put(var33, Math.max(0, cfg.illusionist_celestial_pull_cooldown_ticks));
               id.spawnParticles(ParticleTypes.PORTAL, true, true, moon.x, moon.y, moon.z, 90, 2.0, 2.0, 2.0, 0.25);
               EpicVfx.column(id, ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, Math.min(40.0, moon.y - center.y), 30);
               EpicVfx.shockwave(id, center.x, center.y, center.z, 8.0);
               EpicVfx.cue(id, player.getId(), "illusionist", "black_hole", moon.x, moon.y, moon.z, Vec3d.ZERO, 6.5F, 40, 0.0F);
               id.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 2.0F, 0.5F);
               player.sendMessage(Text.literal("Celestial Pull: gravity well forming."), true);
            }
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      decrement(this.erasureCooldowns, id);
      decrement(this.gripCooldowns, id);
      decrement(this.pullCooldowns, id);
      this.tickCloudFlight(player);
      if (player.getEntityWorld().getServer() != null && EnergyManager.get(player.getEntityWorld().getServer()).getEnergy(id) <= 1.0E-9) {
         this.breakGrip(player, "Psychokinetic Grip released: Energy depleted.");
      }

      Entity held = this.grips.get(id);
      if (held != null) {
         if (!held.isRemoved()
            && held.isAlive()
            && held.getEntityWorld() == player.getEntityWorld()
            && !(player.squaredDistanceTo(held) > Math.pow(YPConfig.getInstance().illusionist_grip_range + 3.0, 2.0))) {
            Vec3d destination = player.getEyePos().add(PowerCombatUtil.safeLook(player).multiply(this.gripDistances.getOrDefault(id, 5.0)));
            Vec3d velocity = destination.subtract(held.getEntityPos()).multiply(0.35);
            held.setVelocity(velocity);
            ((EntityVelocityAccessor)held).invokeYppowersScheduleVelocityUpdate();
            if (player.age % 5 == 0) {
               ServerWorld var7 = player.getEntityWorld();
               if (var7 instanceof ServerWorld) {
                  EpicVfx.beam(var7, ParticleTypes.ENCHANT, player.getEyePos(), held.getBoundingBox().getCenter(), 2.5);
               }
            }
         } else {
            this.grips.remove(id);
            this.gripDistances.remove(id);
         }
      }

      int passive = this.gripPassiveClock.getOrDefault(id, 0) + 1;
      if (passive >= 2) {
         passive = 0;
         this.pullNearbyItems(player);
      }

      this.gripPassiveClock.put(id, passive);
   }

   private void pullNearbyItems(ServerPlayerEntity player) {
      ServerWorld r = player.getEntityWorld();
      if (r instanceof ServerWorld) {
         double var8 = Math.max(0, YPConfig.getInstance().illusionist_grip_passive_radius);

         for (Entity e : r.getOtherEntities(player, player.getBoundingBox().expand(var8), x -> x instanceof ItemEntity || x instanceof ExperienceOrbEntity)) {
            if (!(e.squaredDistanceTo(player) > var8 * var8)) {
               Vec3d delta = player.getEntityPos().add(0.0, 0.5, 0.0).subtract(e.getEntityPos());
               if (!(delta.lengthSquared() < 0.01)) {
                  e.addVelocity(delta.normalize().multiply(0.1));
                  ((EntityVelocityAccessor)e).invokeYppowersScheduleVelocityUpdate();
               }
            }
         }
      }
   }

   public static void serverTick(MinecraftServer server) {
      Iterator<Entry<UUID, IllusionistPower.Erasure>> erasures = ACTIVE_ERASURES.entrySet().iterator();

      while (erasures.hasNext()) {
         Entry<UUID, IllusionistPower.Erasure> entry = erasures.next();
         IllusionistPower.Erasure erasure = entry.getValue();
         if (--erasure.ticks <= 0) {
            for (IllusionistPower.SavedBlock b : erasure.blocks) {
               if (b.world().isChunkLoaded(ChunkPos.toLong(b.pos())) && b.world().getBlockState(b.pos()).isAir()) {
                  b.world().setBlockState(b.pos(), b.state(), 3);
               }
            }

            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(entry.getKey());
            if (!GraceManager.isActive(server)) {
               Set<UUID> processed = new HashSet<>();

               for (IllusionistPower.SavedBlock savedBlock : erasure.blocks) {
                  ServerWorld world = savedBlock.world();
                  Box cell = new Box(savedBlock.pos());

                  for (Entity entity : world.getOtherEntities(null, cell.expand(0.35), ex -> ex instanceof LivingEntity && ex.isAlive())) {
                     if (processed.add(entity.getUuid())
                        && entity instanceof LivingEntity living
                        && !(
                           entity instanceof ServerPlayerEntity targetPlayer
                              && (owner == null || targetPlayer == owner || AllianceManager.get(server).isAlly(owner, targetPlayer))
                        )
                        && (owner == null || !PowerCombatUtil.isFriendlyMinion(owner, entity))) {
                        living.damage(world, world.getDamageSources().magic(), (float)Math.max(0.0, YPConfig.getInstance().illusionist_spatial_erasure_damage));
                        living.addStatusEffect(
                           new StatusEffectInstance(StatusEffects.SLOWNESS, Math.max(1, YPConfig.getInstance().illusionist_spatial_erasure_slowness_ticks), 3)
                        );
                     }
                  }
               }
            }

            erasures.remove();
         }
      }

      if (!PENDING_GRAVITY_RESTORE.isEmpty() && server.getTicks() % 20 == 0) {
         Iterator<Entry<UUID, Integer>> pending = PENDING_GRAVITY_RESTORE.entrySet().iterator();

         while (pending.hasNext()) {
            Entry<UUID, Integer> entry = pending.next();
            if (restoreGravity(server, entry.getKey(), true) == null && entry.getValue() - 20 > 0) {
               entry.setValue(entry.getValue() - 20);
            } else {
               pending.remove();
            }
         }
      }

      Iterator<Entry<UUID, IllusionistPower.PullState>> pulls = ACTIVE_PULLS.entrySet().iterator();

      while (pulls.hasNext()) {
         Entry<UUID, IllusionistPower.PullState> entry = pulls.next();
         IllusionistPower.PullState state = entry.getValue();
         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(state.owner);
         if (owner == null || !owner.isAlive() || owner.getEntityWorld() != state.world) {
            abortPull(server, state);
            pulls.remove();
         } else if (state.blastAge < 0) {
            state.age++;

            for (int i = 0; i < state.assembleBatch && !state.pending.isEmpty(); i++) {
               FallingBlockEntity moonBlock = spawnMoonBlock(state.world, state.pending.poll(), state.blocks.size());
               if (moonBlock != null) {
                  state.blocks.add(moonBlock);
               }
            }

            if (--state.ticks <= 0) {
               beginCrater(server, state);
            } else {
               if (state.age % 10 == 1) {
                  applyWeightless(server, state, owner);
               }

               double r = state.craterRadius;
               Box area = new Box(state.center.x - r, state.center.y - r, state.center.z - r, state.center.x + r, state.center.y + r, state.center.z + r);
               Vec3d hover = new Vec3d(state.center.x, state.center.y + Math.min(10.0, state.moonHeight * 0.45), state.center.z);

               for (Entity e : state.world
                  .getOtherEntities(
                     owner,
                     area,
                     x -> !(x instanceof ServerPlayerEntity) && x instanceof LivingEntity && x.isAlive() && !PowerCombatUtil.isFriendlyMinion(owner, x)
                  )) {
                  if (!(e.squaredDistanceTo(state.center) > r * r)) {
                     state.captured.add(e.getUuid());
                     Vec3d delta = hover.subtract(e.getEntityPos());
                     Vec3d v = delta.lengthSquared() < 2.25 ? Vec3d.ZERO : delta.multiply(0.1);
                     if (v.lengthSquared() > 1.44) {
                        v = v.normalize().multiply(1.2);
                     }

                     e.setVelocity(v);
                     ((EntityVelocityAccessor)e).invokeYppowersScheduleVelocityUpdate();
                  }
               }

               if (state.age % 4 == 0) {
                  state.world
                     .spawnParticles(
                        ParticleTypes.LARGE_SMOKE, true, true,
                        state.moon.x,
                        state.moon.y,
                        state.moon.z,
                        10,
                        state.moonRadius * 0.6,
                        state.moonRadius * 0.6,
                        state.moonRadius * 0.6,
                        0.01
                     );
                  state.world
                     .spawnParticles(
                        ParticleTypes.END_ROD, true, true,
                        state.moon.x,
                        state.moon.y,
                        state.moon.z,
                        4,
                        state.moonRadius * 0.7,
                        state.moonRadius * 0.7,
                        state.moonRadius * 0.7,
                        0.02
                     );
               }

               if (state.age % 12 == 0) {
                  EpicVfx.column(
                     state.world, ParticleTypes.REVERSE_PORTAL, state.center.x, state.center.y, state.center.z, Math.min(30.0, state.moonHeight), 12
                  );
               }

               tickMoonVfx(state);
            }
         } else if (tickCrater(state)) {
            pulls.remove();
         }
      }
   }

   private static void tickMoonVfx(IllusionistPower.PullState state) {
      ServerWorld world = state.world;
      Vec3d moon = state.moon;
      Vec3d c = state.center;
      double r = state.moonRadius;
      double progress = Math.min(1.0, (double)state.age / Math.max(1, state.age + state.ticks));
      double spin = state.age * 0.12;
      int corona = (int)(6.0 + 10.0 * progress);

      for (int k = 0; k < corona; k++) {
         double theta = spin + (Math.PI * 2) * k / corona;
         double phi = Math.PI * (0.15 + 0.7 * (k * 0.6180339887 % 1.0));
         world.spawnParticles(
            k % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD, true, true,
            moon.x + Math.cos(theta) * Math.sin(phi) * r * 1.05,
            moon.y + Math.cos(phi) * r * 1.05,
            moon.z + Math.sin(theta) * Math.sin(phi) * r * 1.05,
            0,
            -Math.sin(theta),
            0.0,
            Math.cos(theta),
            0.04
         );
      }

      if (state.age % 6 == 0) {
         double shadow = r * (1.5 - 0.6 * progress);
         EpicVfx.ring(world, ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.15, c.z, shadow, 0.0, 24, 0.0);
         EpicVfx.helix(world, ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, Math.max(4.0, moon.y - r - c.y), Math.min(6.0, r * 0.6), 2.0, 22, 0.06);
      }

      if (state.ticks == 26) {
         world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 5.0F, 0.6F);
      }

      if (state.ticks <= 14) {
         double f = 1.0 - state.ticks / 14.0;
         double y = moon.y - r + (c.y + 1.0 - (moon.y - r)) * f;
         EpicVfx.ring(world, ParticleTypes.FLAME, moon.x, y, moon.z, r * 0.55, 0.02, 20, -0.04);
         world.spawnParticles(ParticleTypes.LAVA, true, true, moon.x, moon.y - r, moon.z, 3, r * 0.5, 0.3, r * 0.5, 0.0);
         world.spawnParticles(ParticleTypes.LARGE_SMOKE, true, true, moon.x, moon.y - r, moon.z, 6, r * 0.5, 0.3, r * 0.5, 0.02);
      }
   }

   private static FallingBlockEntity spawnMoonBlock(ServerWorld world, BlockPos pos, int index) {
      if (!world.getBlockState(pos).isAir()) {
         return null;
      } else {
         BlockState block = index % 7 == 0
            ? Blocks.CRYING_OBSIDIAN.getDefaultState()
            : (
               index % 5 == 0
                  ? Blocks.MAGMA_BLOCK.getDefaultState()
                  : (index % 3 == 0 ? Blocks.OBSIDIAN.getDefaultState() : Blocks.BLACKSTONE.getDefaultState())
            );
         FallingBlockEntity fallingBlock = FallingBlockEntity.spawnFromBlock(world, pos, block);
         if (fallingBlock == null) {
            return null;
         } else {
            fallingBlock.setNoGravity(true);
            fallingBlock.setVelocity(Vec3d.ZERO);
            fallingBlock.setDestroyedOnLanding();
            return fallingBlock;
         }
      }
   }

   private static void applyWeightless(MinecraftServer server, IllusionistPower.PullState state, ServerPlayerEntity owner) {
      double wr = state.weightlessRadius;
      AllianceManager allies = AllianceManager.get(server);
      Box box = new Box(state.center.x - wr, state.center.y - wr, state.center.z - wr, state.center.x + wr, state.center.y + wr, state.center.z + wr);

      for (Entity e : state.world.getOtherEntities(owner, box, x -> x.isAlive() && !x.hasNoGravity() && (x instanceof LivingEntity || x instanceof ItemEntity))) {
         if (!(e.squaredDistanceTo(state.center) > wr * wr)
            && !(e instanceof ServerPlayerEntity other && allies.isAlly(owner, other))
            && !PowerCombatUtil.isFriendlyMinion(owner, e)) {
            e.setNoGravity(true);
            state.weightless.add(e.getUuid());
         }
      }
   }

   private static Entity restoreGravity(MinecraftServer server, UUID id, boolean slowFall) {
      for (ServerWorld world : server.getWorlds()) {
         Entity e = world.getEntity(id);
         if (e != null) {
            e.setNoGravity(false);
            if (slowFall && e instanceof LivingEntity living && living.isAlive()) {
               living.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
            }

            return e;
         }
      }

      return null;
   }

   private static void beginCrater(MinecraftServer server, IllusionistPower.PullState state) {
      state.blastAge = 0;
      YPConfig cfg = YPConfig.getInstance();
      float damage = (float)Math.max(0.0, Math.max(cfg.illusionist_celestial_pull_damage, cfg.illusionist_celestial_crater_damage));

      for (UUID capturedId : state.captured) {
         if (state.world.getEntity(capturedId) instanceof LivingEntity living && living.isAlive()) {
            living.damage(state.world, state.world.getDamageSources().magic(), damage);
         }
      }

      for (UUID id : state.weightless) {
         boolean wasCaptured = state.captured.contains(id);
         Entity e = restoreGravity(server, id, !wasCaptured);
         if (e == null) {
            PENDING_GRAVITY_RESTORE.put(id, 72000);
         } else if (wasCaptured && e.isAlive()) {
            e.setVelocity(0.0, -1.8, 0.0);
            ((EntityVelocityAccessor)e).invokeYppowersScheduleVelocityUpdate();
         }
      }

      ServerWorld world = state.world;
      Vec3d c = state.center;
      world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 5.0F, 0.5F);
      world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 6.0F, 0.6F);
      world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 4.0F, 0.7F);
      world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
      world.spawnParticles(ParticleTypes.SONIC_BOOM, true, true, c.x, c.y + 1.0, c.z, 1, 0.0, 0.0, 0.0, 0.0);
      EpicVfx.flash(world, c.x, c.y + 1.0, c.z);
      EpicVfx.sphereBurst(world, ParticleTypes.PORTAL, c.x, c.y + 1.0, c.z, 60, 0.7);
      EpicVfx.sphereBurst(world, ParticleTypes.END_ROD, c.x, c.y + 1.0, c.z, 40, 0.5);
      EpicVfx.column(world, ParticleTypes.END_ROD, c.x, c.y, c.z, 18.0, 18);
      EpicVfx.shockwave(world, c.x, c.y, c.z, state.craterRadius);
      ServerPlayerEntity owner = server.getPlayerManager().getPlayer(state.owner);
      EpicVfx.cue(world, owner == null ? -1 : owner.getId(), "illusionist", "detonation", c.x, c.y + 1.0, c.z, Vec3d.ZERO, 1.8F, 28, 0.35F);
   }

   private static boolean tickCrater(IllusionistPower.PullState state) {
      int age = state.blastAge++;
      int removed = 0;

      for (Iterator<FallingBlockEntity> blocks = state.blocks.iterator(); blocks.hasNext() && removed < state.removeBatch; removed++) {
         FallingBlockEntity shard = blocks.next();
         state.world
            .spawnParticles(
               new BlockStateParticleEffect(ParticleTypes.BLOCK, shard.getBlockState()), true, true, shard.getX(), shard.getY(), shard.getZ(), 3, 0.35, 0.35, 0.35, 0.12
            );
         shard.discard();
         blocks.remove();
      }

      ServerWorld world = state.world;
      Vec3d c = state.center;
      if (age % 2 == 0 && age <= 16) {
         double progress = Math.min(1.0, (age + 1) / 16.0);
         double r = state.craterRadius * (1.0 - Math.pow(1.0 - progress, 2.0));
         int points = (int)Math.max(16.0, Math.min(40.0, 16.0 + r * 2.0));
         BlockState ground = world.getBlockState(BlockPos.ofFloored(c.x, c.y - 1.0, c.z));
         if (ground.isAir()) {
            ground = Blocks.DEEPSLATE.getDefaultState();
         }

         for (int i = 0; i < points; i++) {
            double a = (Math.PI * 2) * i / points;
            world.spawnParticles(ParticleTypes.CLOUD, true, true, c.x + Math.cos(a) * r, c.y + 0.2, c.z + Math.sin(a) * r, 1, 0.1, 0.05, 0.1, 0.01);
            if (i % 2 == 0) {
               world.spawnParticles(
                  new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), true, true,
                  c.x + Math.cos(a) * r * 0.9,
                  c.y + 0.3,
                  c.z + Math.sin(a) * r * 0.9,
                  2,
                  0.3,
                  0.3,
                  0.3,
                  0.15
               );
            }
         }

         if (age == 2 || age == 6) {
            for (int k = 0; k < 2; k++) {
               double a = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2);
               double d = ThreadLocalRandom.current().nextDouble(0.2, 0.8) * state.craterRadius;
               LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
               if (bolt != null) {
                  bolt.setCosmetic(true);
                  bolt.refreshPositionAfterTeleport(c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d);
                  world.spawnEntity(bolt);
               }
            }
         }
      }

      return age >= 16 && state.blocks.isEmpty();
   }

   private static void abortPull(MinecraftServer server, IllusionistPower.PullState state) {
      for (FallingBlockEntity b : state.blocks) {
         b.discard();
      }

      state.blocks.clear();

      for (UUID id : state.weightless) {
         if (restoreGravity(server, id, true) == null) {
            PENDING_GRAVITY_RESTORE.put(id, 72000);
         }
      }
   }

   public static void shutdown(MinecraftServer server) {
      if (PowerManager.get(server).getPowerInstance(PowerType.ILLUSIONIST) instanceof IllusionistPower illusionist) {
         for (UUID id : new ArrayList<>(illusionist.cloudFlights.keySet())) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null) {
               illusionist.endCloudFlight(player, false, false);
            }
         }

         illusionist.cloudFlights.clear();
         illusionist.cloudFlightCooldowns.clear();
      }

      for (IllusionistPower.Erasure erasure : ACTIVE_ERASURES.values()) {
         for (IllusionistPower.SavedBlock b : erasure.blocks) {
            if (b.world().isChunkLoaded(ChunkPos.toLong(b.pos())) && b.world().getBlockState(b.pos()).isAir()) {
               b.world().setBlockState(b.pos(), b.state(), 3);
            }
         }
      }

      ACTIVE_ERASURES.clear();

      for (IllusionistPower.PullState state : ACTIVE_PULLS.values()) {
         abortPull(server, state);
      }

      ACTIVE_PULLS.clear();
      PENDING_GRAVITY_RESTORE.keySet().removeIf(idx -> restoreGravity(server, idx, false) != null);
   }

   private static void decrement(Map<UUID, Integer> map, UUID id) {
      int v = map.getOrDefault(id, 0);
      if (v > 0) {
         map.put(id, v - 1);
      }
   }

   public boolean tryThrowGripOnAttack(ServerPlayerEntity player) {
      if (!this.grips.containsKey(player.getUuid())) {
         return false;
      } else {
         this.throwGrip(player);
         return true;
      }
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.cleanup(player);
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.cleanup(player);
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.cleanup(player);
   }

   private void cleanup(ServerPlayerEntity player) {
      UUID id = player.getUuid();
      this.endCloudFlight(player, false, false);
      this.erasureArmed.remove(id);
      Entity held = this.grips.remove(id);
      this.gripDistances.remove(id);
      this.gripPassiveClock.remove(id);
      if (held != null && held.isAlive()) {
         held.setVelocity(Vec3d.ZERO);
      }

      this.erasureCooldowns.remove(id);
      this.gripCooldowns.remove(id);
      this.pullCooldowns.remove(id);
      this.cloudFlightCooldowns.remove(id);
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.erasureCooldowns.remove(playerId);
      this.gripCooldowns.remove(playerId);
      this.pullCooldowns.remove(playerId);
      this.cloudFlightCooldowns.remove(playerId);
   }

   private static final class CloudFlight {
      final boolean previousAllowFlying;
      final boolean previousFlying;

      CloudFlight(ServerPlayerEntity player) {
         this.previousAllowFlying = player.getAbilities().allowFlying;
         this.previousFlying = player.getAbilities().flying;
      }
   }

   private static final class Erasure {
      final List<IllusionistPower.SavedBlock> blocks;
      int ticks;

      Erasure(List<IllusionistPower.SavedBlock> blocks, int ticks) {
         this.blocks = blocks;
         this.ticks = ticks;
      }
   }

   private static final class PullState {
      final UUID owner;
      final ServerWorld world;
      final Vec3d center;
      final Vec3d moon;
      final double moonRadius;
      final double craterRadius;
      final double weightlessRadius;
      final double moonHeight;
      final List<FallingBlockEntity> blocks = new ArrayList<>();
      final ArrayDeque<BlockPos> pending = new ArrayDeque<>();
      final Set<UUID> captured = new HashSet<>();
      final Set<UUID> weightless = new HashSet<>();
      int ticks;
      int age;
      int blastAge = -1;
      int assembleBatch = 14;
      int removeBatch = 40;
      int totalPlanned;

      PullState(
         UUID owner, ServerWorld world, Vec3d center, Vec3d moon, double moonRadius, double moonHeight, double craterRadius, double weightlessRadius, int ticks
      ) {
         this.owner = owner;
         this.world = world;
         this.center = center;
         this.moon = moon;
         this.moonRadius = moonRadius;
         this.moonHeight = moonHeight;
         this.craterRadius = craterRadius;
         this.weightlessRadius = weightlessRadius;
         this.ticks = ticks;
      }
   }

   private record SavedBlock(ServerWorld world, BlockPos pos, BlockState state) {
   }
}
