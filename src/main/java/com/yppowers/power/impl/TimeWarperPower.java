package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.YPItemData;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class TimeWarperPower extends Power {
   private static final int REWIND_TICKS = 500;
   private final Map<UUID, Integer> timeStopActive = new HashMap<>();
   private final Map<UUID, List<TimeWarperPower.FrozenEntity>> frozenEntities = new HashMap<>();
   private final Map<UUID, Long> timeStopCooldown = new HashMap<>();
   private final Map<UUID, Long> timeWarpCooldown = new HashMap<>();
   private final Map<UUID, Deque<TimeWarperPower.PlayerSnapshot>> playerHistories = new HashMap<>();
   private final Map<UUID, TimeWarperPower.BlackHoleState> blackHoles = new HashMap<>();
   private final Map<UUID, Long> blackHoleHoldHeartbeats = new HashMap<>();
   private static final Map<UUID, Integer> TIME_SHARD_LIFETIME = new HashMap<>();
   private static final Set<UUID> GOLDEN_SHARD_IDS = new HashSet<>();
   private final Map<UUID, List<TimeWarperPower.TimeClone>> timeClones = new HashMap<>();
   private final Map<UUID, Entity> lastOwnerTargets = new HashMap<>();
   private final List<TimeWarperPower.BlackHoleImplosionVfx> blackHoleImplosionVfx = new ArrayList<>();
   private final List<TimeWarperPower.BlackHoleBlast> blackHoleBlasts = new ArrayList<>();
   private static final int BLACK_HOLE_IMPLOSION_VFX_TICKS = 5;
   private static final int BLACK_HOLE_OUTWARD_BURST_TICKS = 6;
   private static final TintedParticleEffect BLACK_HOLE_DARK_PARTICLE = TintedParticleEffect.create(ParticleTypes.ENTITY_EFFECT, 526348);
   private static final Map<Entity, Integer> FREEZE_COUNTS = new IdentityHashMap<>();
   private static final Map<Integer, Integer> TIME_STOP_VISUAL_COUNTS = new HashMap<>();

   private static void retainFrozen(Entity entity) {
      FREEZE_COUNTS.merge(entity, 1, Integer::sum);
   }

   private static void retainTimeStopVisual(Entity entity) {
      if (entity instanceof ServerPlayerEntity player) {
         int entityId = player.getId();
         int count = TIME_STOP_VISUAL_COUNTS.getOrDefault(entityId, 0);
         TIME_STOP_VISUAL_COUNTS.put(entityId, count + 1);
         if (count == 0) {
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server != null) {
               YPNetworking.broadcastTimeStopVisual(server, entityId, true);
            }
         }
      }
   }

   private static void releaseTimeStopVisual(Entity entity) {
      if (entity instanceof ServerPlayerEntity player) {
         int entityId = player.getId();
         Integer count = TIME_STOP_VISUAL_COUNTS.get(entityId);
         if (count != null && count > 1) {
            TIME_STOP_VISUAL_COUNTS.put(entityId, count - 1);
         } else {
            TIME_STOP_VISUAL_COUNTS.remove(entityId);
            MinecraftServer server = player.getEntityWorld().getServer();
            if (server != null) {
               YPNetworking.broadcastTimeStopVisual(server, entityId, false);
            }
         }
      }
   }

   public static void syncTimeStopVisualsTo(ServerPlayerEntity recipient) {
      if (recipient != null) {
         for (int entityId : TIME_STOP_VISUAL_COUNTS.keySet()) {
            YPNetworking.syncTimeStopVisualTo(recipient, entityId);
         }
      }
   }

   public static void clearTimeStopVisualState() {
      TIME_STOP_VISUAL_COUNTS.clear();
   }

   private static void releaseFrozen(Entity entity) {
      Integer count = FREEZE_COUNTS.get(entity);
      if (count != null && count > 1) {
         FREEZE_COUNTS.put(entity, count - 1);
      } else {
         FREEZE_COUNTS.remove(entity);
      }
   }

   private static double blackHolePowerFraction(TimeWarperPower.BlackHoleState hole, YPConfig config) {
      return Math.max(0.0, Math.min(1.0, hole.absorbed / Math.max(1.0, config.time_warper_black_hole_items_for_max_power)));
   }

   private static double blackHoleMultiplier(TimeWarperPower.BlackHoleState hole, YPConfig config) {
      return 1.0 + (Math.max(1.0, config.time_warper_black_hole_max_power_multiplier) - 1.0) * blackHolePowerFraction(hole, config);
   }

   private static int damageWithoutMobDrops(ServerWorld world, LivingEntity target, float amount) {
      boolean mob = target instanceof MobEntity;
      Box dropBox = target.getBoundingBox().expand(4.0);
      Set<Integer> before = new HashSet<>();
      if (mob) {
         for (ItemEntity existing : world.getEntitiesByClass(ItemEntity.class, dropBox, i -> true)) {
            before.add(existing.getId());
         }
      }

      target.damage(world, world.getDamageSources().magic(), amount);
      int removed = 0;
      if (mob && !target.isAlive()) {
         for (ItemEntity drop : world.getEntitiesByClass(ItemEntity.class, dropBox, i -> !before.contains(i.getId()))) {
            removed += drop.getStack().getCount();
            drop.discard();
         }
      }

      return removed;
   }

   private static void cosmeticLightning(ServerWorld world, double x, double y, double z) {
      LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
      if (bolt != null) {
         bolt.setCosmetic(true);
         bolt.refreshPositionAfterTeleport(x, y, z);
         world.spawnEntity(bolt);
      }
   }

   @Override
   public PowerType getType() {
      return PowerType.TIME_WARPER;
   }

   public static void serverTick(MinecraftServer server) {
      if (PowerManager.get(server).getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower timeWarper) {
         timeWarper.tickServer(server);
      }
   }

   public static void updateBlackHoleHold(ServerPlayerEntity player, boolean held) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         if (PowerManager.get(server).getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower timeWarper) {
            UUID id = player.getUuid();
            if (!held) {
               timeWarper.blackHoleHoldHeartbeats.remove(id);
            } else {
               PowerManager manager = PowerManager.get(server);
               if (manager.getPowerType(id) == PowerType.TIME_WARPER
                  && manager.isPowerEnabled(player, PowerType.TIME_WARPER)
                  && AwakeningManager.isAwakened(manager, id, PowerType.TIME_WARPER)
                  && timeWarper.isRealityForm(player)
                  && player.isAlive()
                  && !GraceManager.isActive(server)) {
                  timeWarper.blackHoleHoldHeartbeats.put(id, (long)server.getTicks());
               } else {
                  timeWarper.blackHoleHoldHeartbeats.remove(id);
               }
            }
         }
      }
   }

   public static void clearBlackHoleRuntimeState(MinecraftServer server) {
      if (server != null) {
         if (PowerManager.get(server).getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower timeWarper) {
            timeWarper.blackHoles.clear();
            timeWarper.blackHoleHoldHeartbeats.clear();
            timeWarper.blackHoleImplosionVfx.clear();
            timeWarper.blackHoleBlasts.clear();
         }
      }
   }

   private void tickServer(MinecraftServer server) {
      if (server.getTicks() % 20 == 0) {
         Set<UUID> onlineIds = new HashSet<>();

         for (ServerPlayerEntity online : server.getPlayerManager().getPlayerList()) {
            onlineIds.add(online.getUuid());
         }

         this.playerHistories.keySet().retainAll(onlineIds);
      }

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         Deque<TimeWarperPower.PlayerSnapshot> history = this.playerHistories.computeIfAbsent(player.getUuid(), k -> new ArrayDeque<>(501));
         history.addLast(new TimeWarperPower.PlayerSnapshot(player));

         while (history.size() > 501) {
            history.removeFirst();
         }
      }

      Iterator<Entry<UUID, Integer>> stops = this.timeStopActive.entrySet().iterator();

      while (stops.hasNext()) {
         Entry<UUID, Integer> stop = stops.next();
         UUID casterId = stop.getKey();
         int remaining = stop.getValue() - 1;
         List<TimeWarperPower.FrozenEntity> frozen = this.frozenEntities.get(casterId);
         if (remaining <= 0) {
            if (frozen != null) {
               for (TimeWarperPower.FrozenEntity fe : frozen) {
                  releaseFrozen(fe.entity);
                  releaseTimeStopVisual(fe.entity);
                  if (fe.entity.isAlive()
                     && !fe.entity.isRemoved()
                     && !FREEZE_COUNTS.containsKey(fe.entity)
                     && !SeriousPunchPower.isEntityFrozen(fe.entity)
                     && !IceEmperorPower.isEntityFrozen(fe.entity)) {
                     fe.entity.setVelocity(fe.velocity);
                     ((EntityVelocityAccessor)fe.entity).invokeYppowersScheduleVelocityUpdate();
                  }
               }
            }

            this.frozenEntities.remove(casterId);
            stops.remove();
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
            if (caster != null) {
               caster.sendMessage(Text.literal("Time Stop ended."), true);
            }
         } else {
            stop.setValue(remaining);
            if (frozen != null) {
               ServerPlayerEntity caster = server.getPlayerManager().getPlayer(casterId);
               if (caster != null && server.getTicks() % 2 == 0) {
                  Set<Entity> known = Collections.newSetFromMap(new IdentityHashMap<>());

                  for (TimeWarperPower.FrozenEntity fex : frozen) {
                     known.add(fex.entity);
                  }

                  AllianceManager alliances = AllianceManager.get(server);

                  for (ServerWorld world : server.getWorlds()) {
                     for (Entity entity : world.iterateEntities()) {
                        if (!known.contains(entity)
                           && entity != caster
                           && !entity.isRemoved()
                           && !(entity instanceof ServerPlayerEntity other && alliances.isAlly(caster, other))) {
                           frozen.add(new TimeWarperPower.FrozenEntity(entity));
                           known.add(entity);
                           retainFrozen(entity);
                           retainTimeStopVisual(entity);
                        }
                     }
                  }
               }

               for (TimeWarperPower.FrozenEntity fex : frozen) {
                  if (fex.entity.isAlive() && !fex.entity.isRemoved()) {
                     fex.entity.setPosition(fex.position);
                     fex.entity.setVelocity(Vec3d.ZERO);
                     fex.entity.setYaw(fex.yaw);
                     fex.entity.setPitch(fex.pitch);
                     ((EntityVelocityAccessor)fex.entity).invokeYppowersScheduleVelocityUpdate();
                     if (server.getTicks() % 5 == 0 && fex.entity.getEntityWorld() instanceof ServerWorld world) {
                        world.spawnParticles(ParticleTypes.WITCH, true, true, fex.position.x, fex.position.y + 1.0, fex.position.z, 2, 0.2, 0.2, 0.2, 0.0);
                     }
                  }
               }
            }
         }
      }

      this.tickBlackHoles(server);
      this.blackHoleHoldHeartbeats.entrySet().removeIf(entry -> server.getTicks() - entry.getValue() > 40L);
      this.tickTimeClones(server);
      tickTimeShards(server);
   }

   public static boolean isEntityFrozen(Entity entity) {
      return entity != null && FREEZE_COUNTS.containsKey(entity);
   }

   public static Vec3d getFrozenVelocity(Entity entity) {
      if (entity == null || !(entity.getEntityWorld() instanceof ServerWorld world && world.getServer() != null)) {
         return null;
      } else if (!(PowerManager.get(world.getServer()).getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower warper)) {
         return null;
      } else {
         for (List<TimeWarperPower.FrozenEntity> list : warper.frozenEntities.values()) {
            for (TimeWarperPower.FrozenEntity frozen : list) {
               if (frozen.entity == entity) {
                  return frozen.velocity;
               }
            }
         }

         return null;
      }
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      if (this.isRealityForm(player)) {
         this.summonTimeClone(player, false);
      } else {
         UUID uuid = player.getUuid();
         long currentTicks = player.getEntityWorld().getServer().getTicks();
         long cooldown = Math.max(0, YPConfig.getInstance().time_stop_cooldown_ticks);
         Long lastUse = this.timeStopCooldown.get(uuid);
         if (lastUse != null && currentTicks - lastUse < cooldown) {
            player.sendMessage(Text.literal("Time Stop on cooldown! " + (cooldown - (currentTicks - lastUse)) / 20L + "s"), true);
         } else if (this.timeStopActive.containsKey(uuid)) {
            player.sendMessage(Text.literal("Time Stop is already active."), true);
         } else {
            this.timeStopCooldown.put(uuid, currentTicks);
            this.timeStopActive.put(uuid, YPConfig.getInstance().time_stop_max_duration_ticks);
            List<TimeWarperPower.FrozenEntity> frozen = new ArrayList<>();
            AllianceManager alliances = AllianceManager.get(player.getEntityWorld().getServer());

            for (ServerWorld world : player.getEntityWorld().getServer().getWorlds()) {
               for (Entity entity : world.iterateEntities()) {
                  if (entity != player && !entity.isRemoved() && !(entity instanceof ServerPlayerEntity other && alliances.isAlly(player, other))) {
                     frozen.add(new TimeWarperPower.FrozenEntity(entity));
                     retainFrozen(entity);
                     retainTimeStopVisual(entity);
                  }
               }
            }

            this.frozenEntities.put(uuid, frozen);
            player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0F, 1.0F);
            player.sendMessage(Text.literal("Time Stop!"), true);
            ServerWorld var15 = player.getEntityWorld();
            if (var15 instanceof ServerWorld) {
               EpicVfx.shockwave(var15, player.getX(), player.getY(), player.getZ(), 14.0);
               EpicVfx.ring(var15, ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 0.5, 0.55, 40, 0.0);
               EpicVfx.helix(var15, ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY(), player.getZ(), 3.0, 1.2, 3.0, 30, 0.1);
               EpicVfx.column(var15, ParticleTypes.END_ROD, player.getX(), player.getY(), player.getZ(), 8.0, 14);
               EpicVfx.flash(var15, player.getX(), player.getY() + 1.0, player.getZ());
               EpicVfx.cue(var15, player.getId(), "time_warper", "phase", player.getX(), player.getY(), player.getZ(), Vec3d.ZERO, 1.3F, 32, 0.0F);
            }
         }
      }
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         ServerWorld config = player.getEntityWorld();
         if (config instanceof ServerWorld) {
            if (this.blackHoles.containsKey(player.getUuid())) {
               player.sendMessage(Text.literal("A Black Hole is already charging.").formatted(Formatting.YELLOW), true);
               return;
            }

            if (GraceManager.isActive(server)) {
               GraceManager.blockAbility(player);
               return;
            }

            YPConfig configx = YPConfig.getInstance();
            boolean terrainEnabled = configx.time_warper_black_hole_terrain_damage && configx.time_warper_black_hole_max_blocks_per_tick > 0;
            if (terrainEnabled) {
               AwakeningManager.requestBlackHoleTerrainConfirmation(player);
               return;
            }

            this.startBlackHole(player);
            return;
         }
      }
   }

   public static void confirmBlackHoleTerrain(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null && player.isAlive()) {
         MinecraftServer server = player.getEntityWorld().getServer();
         PowerManager manager = PowerManager.get(server);
         if (manager.getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower timeWarper
            && manager.getPowerType(player.getUuid()) == PowerType.TIME_WARPER
            && manager.isPowerEnabled(player, PowerType.TIME_WARPER)
            && !GraceManager.isActive(server)) {
            timeWarper.startBlackHole(player);
         }
      }
   }

   private void startBlackHole(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         ServerWorld config = player.getEntityWorld();
         if (config instanceof ServerWorld && !this.blackHoles.containsKey(player.getUuid())) {
            YPConfig configx = YPConfig.getInstance();
            Vec3d look = player.getRotationVec(1.0F).normalize();
            Vec3d center = player.getEyePos().add(look.multiply(8.0));
            boolean terrainEnabled = configx.time_warper_black_hole_terrain_damage && configx.time_warper_black_hole_max_blocks_per_tick > 0;
            this.blackHoles
               .put(
                  player.getUuid(),
                  new TimeWarperPower.BlackHoleState(player.getUuid(), config, center, configx.time_warper_black_hole_terrain_radius, terrainEnabled)
               );
            EnergyManager.get(server).startBlackHoleCooldown(player);
            double sacrifice = Math.max(0.0, Math.min(0.95, configx.time_warper_black_hole_health_sacrifice_percent));
            player.setHealth(Math.max(1.0F, (float)(player.getHealth() * (1.0 - sacrifice))));
            player.sendMessage(Text.literal("BLACK HOLE — reality is collapsing...").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
            config.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 2.0F, 0.5F);
            EpicVfx.cue(config, player.getId(), "time_warper", "black_hole", center.x, center.y, center.z, look, 7.5F, 80, 0.0F);
            return;
         }
      }
   }

   private void tickBlackHoles(MinecraftServer server) {
      if (this.blackHoles.isEmpty()) {
         this.tickBlackHoleImplosionVfx();
      } else {
         Iterator<Entry<UUID, TimeWarperPower.BlackHoleState>> iterator = this.blackHoles.entrySet().iterator();
         YPConfig config = YPConfig.getInstance();

         while (iterator.hasNext()) {
            TimeWarperPower.BlackHoleState hole = iterator.next().getValue();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(hole.ownerId);
            if (owner != null
               && owner.isAlive()
               && owner.getEntityWorld().getServer() != null
               && owner.getEntityWorld().getRegistryKey().equals(hole.world.getRegistryKey())
               && PowerManager.get(server).getPowerType(owner.getUuid()) == PowerType.TIME_WARPER
               && !GraceManager.isActive(server)) {
               int charge = Math.max(1, config.time_warper_black_hole_charge_ticks);
               int pull = Math.max(1, config.time_warper_black_hole_pull_ticks);
               if (hole.imploded) {
                  this.processBlackHoleTerrain(hole, owner, config);
                  if (!hole.finalBlastResolved && this.tickBlackHoleRecollapse(hole, config)) {
                     this.finishBlackHole(hole, owner, config);
                     hole.finalBlastResolved = true;
                     hole.recollapseTargets.clear();
                  }

                  if (hole.finalBlastResolved
                     && (!config.time_warper_black_hole_terrain_damage || hole.terrainFinished)
                     && !this.tickAwakenedBlackHoleSustain(hole, owner, config, server.getTicks())) {
                     iterator.remove();
                  }
               } else {
                  hole.elapsedTicks++;
                  boolean charging = hole.elapsedTicks <= charge;
                  double progress = charging ? (double)hole.elapsedTicks / charge : Math.min(1.0, (double)(hole.elapsedTicks - charge) / pull);
                  double visualProgress = Math.min(1.0, (double)hole.elapsedTicks / (charge + pull));
                  double maxVisualRadius = Math.max(2.5, config.time_warper_black_hole_entity_radius * 0.45);
                  double powerFraction = blackHolePowerFraction(hole, config);
                  double ballRadius = (0.35 + maxVisualRadius * Math.sqrt(visualProgress)) * (1.0 + 0.8 * powerFraction);
                  boolean visualFrame = hole.elapsedTicks % 3 == 0;
                  if (visualFrame) {
                     hole.world
                        .spawnParticles(
                           ParticleTypes.LARGE_SMOKE, true, true,
                           hole.center.x,
                           hole.center.y,
                           hole.center.z,
                           8,
                           ballRadius * 0.36,
                           ballRadius * 0.36,
                           ballRadius * 0.36,
                           0.003
                        );
                     hole.world
                        .spawnParticles(
                           ParticleTypes.ASH, true, true, hole.center.x, hole.center.y, hole.center.z, 4, ballRadius * 0.3, ballRadius * 0.3, ballRadius * 0.3, 0.001
                        );
                  }

                  if (hole.elapsedTicks % 4 == 0) {
                     int orbPoints = (int)Math.min(120.0, 48.0 + ballRadius * 4.0);
                     EpicVfx.darkOrb(hole.world, hole.center.x, hole.center.y, hole.center.z, ballRadius, orbPoints);
                  }

                  if (visualFrame) {
                     double rotation = server.getTicks() * 0.24;

                     for (int i = 0; i < 5; i++) {
                        double fraction = (i + 1.0) / 5.0;
                        double angle = rotation + fraction * Math.PI * 4.0;
                        double orbit = ballRadius * (0.2 + 0.78 * fraction);
                        double particleY = hole.center.y + Math.sin(angle * 0.72) * ballRadius * 0.58;
                        hole.world
                           .spawnParticles(
                              BLACK_HOLE_DARK_PARTICLE, true, true,
                              hole.center.x + Math.cos(angle) * orbit,
                              particleY,
                              hole.center.z + Math.sin(angle) * orbit,
                              1,
                              0.03,
                              0.03,
                              0.03,
                              0.004
                           );
                     }
                  }

                  if (visualFrame && powerFraction > 0.02) {
                     double diskRadius = ballRadius * (1.2 + 0.8 * powerFraction);
                     int diskPoints = (int)(10.0 + 26.0 * powerFraction);
                     double spin = server.getTicks() * 0.35;

                     for (int i = 0; i < diskPoints; i++) {
                        double a = spin + (Math.PI * 2) * i / diskPoints;
                        hole.world
                           .spawnParticles(
                              ParticleTypes.SOUL_FIRE_FLAME, true, true,
                              hole.center.x + Math.cos(a) * diskRadius,
                              hole.center.y + Math.sin(a) * diskRadius * 0.16,
                              hole.center.z + Math.sin(a) * diskRadius,
                              0,
                              -Math.sin(a),
                              0.0,
                              Math.cos(a),
                              0.06
                           );
                     }

                     if (powerFraction > 0.25) {
                        for (int k = 0; k < 5; k++) {
                           double off = ballRadius * (0.6 + k * 0.5);
                           hole.world.spawnParticles(ParticleTypes.END_ROD, true, true, hole.center.x, hole.center.y + off, hole.center.z, 0, 0.0, 1.0, 0.0, 0.28);
                           hole.world.spawnParticles(ParticleTypes.END_ROD, true, true, hole.center.x, hole.center.y - off, hole.center.z, 0, 0.0, -1.0, 0.0, 0.28);
                        }
                     }
                  }

                  if (charging) {
                     this.processBlackHoleTerrain(hole, owner, config);
                     if (hole.elapsedTicks % 10 == 0) {
                        hole.world
                           .playSound(
                              null,
                              BlockPos.ofFloored(hole.center),
                              SoundEvents.BLOCK_BEACON_POWER_SELECT,
                              SoundCategory.PLAYERS,
                              0.8F,
                              (float)(1.5 - progress * 0.8)
                           );
                     }
                  } else {
                     this.applyBlackHolePull(hole, owner, progress, config);
                     this.processBlackHoleTerrain(hole, owner, config);
                     int shown = (int)hole.absorbed;
                     if (shown != hole.lastDisplayed && hole.elapsedTicks % 5 == 0) {
                        hole.lastDisplayed = shown;
                        owner.sendMessage(
                           Text.literal(String.format(Locale.ROOT, "BLACK HOLE — %d items absorbed — Power x%.2f", shown, blackHoleMultiplier(hole, config)))
                              .formatted(Formatting.DARK_PURPLE),
                           true
                        );
                     }

                     if (hole.elapsedTicks >= charge + pull) {
                        hole.imploded = true;
                        this.beginBlackHoleRecollapse(hole, owner, config);
                     }
                  }
               }
            } else {
               iterator.remove();
               this.blackHoleHoldHeartbeats.remove(hole.ownerId);
            }
         }

         this.tickBlackHoleImplosionVfx();
      }
   }

   private void tickBlackHoleImplosionVfx() {
      this.tickBlackHoleBlasts();
      if (!this.blackHoleImplosionVfx.isEmpty()) {
         Iterator<TimeWarperPower.BlackHoleImplosionVfx> iterator = this.blackHoleImplosionVfx.iterator();

         while (iterator.hasNext()) {
            TimeWarperPower.BlackHoleImplosionVfx effect = iterator.next();
            int frame = effect.age;
            double progress = frame / 4.0;
            double radius = effect.radius * (1.0 - progress);
            double spread = Math.max(0.015, radius * 0.38);
            effect.world
               .spawnParticles(BLACK_HOLE_DARK_PARTICLE, true, true, effect.center.x, effect.center.y, effect.center.z, 32 - frame * 5, spread, spread, spread, 0.002);
            effect.world
               .spawnParticles(
                  ParticleTypes.LARGE_SMOKE, true, true, effect.center.x, effect.center.y, effect.center.z, 18 - frame * 3, spread * 1.1, spread * 1.1, spread * 1.1, 0.001
               );

            for (int i = 0; i < 4; i++) {
               double angle = i * Math.PI / 2.0 + frame * 0.72;
               double orbit = radius * (0.35 + 0.08 * (i % 3));
               double y = effect.center.y + Math.sin(angle * 1.4) * radius * 0.3;
               effect.world
                  .spawnParticles(
                     BLACK_HOLE_DARK_PARTICLE, true, true,
                     effect.center.x + Math.cos(angle) * orbit,
                     y,
                     effect.center.z + Math.sin(angle) * orbit,
                     1,
                     0.01,
                     0.01,
                     0.01,
                     0.001
                  );
            }

            if (frame >= 4) {
               effect.world.spawnParticles(ParticleTypes.EXPLOSION, true, true, effect.center.x, effect.center.y, effect.center.z, 1, 0.0, 0.0, 0.0, 0.0);
               EpicVfx.impact(effect.world, effect.center.x, effect.center.y, effect.center.z, 1.6);
               effect.world
                  .spawnParticles(
                     TintedParticleEffect.create(ParticleTypes.FLASH, 16777215), true, true, effect.center.x, effect.center.y, effect.center.z, 1, 0.0, 0.0, 0.0, 0.0
                  );
               effect.world.spawnParticles(ParticleTypes.END_ROD, true, true, effect.center.x, effect.center.y, effect.center.z, 36, 0.45, 0.45, 0.45, 0.08);
               iterator.remove();
            } else {
               effect.age++;
            }
         }
      }
   }

   private void tickBlackHoleBlasts() {
      if (!this.blackHoleBlasts.isEmpty()) {
         Iterator<TimeWarperPower.BlackHoleBlast> iterator = this.blackHoleBlasts.iterator();

         while (iterator.hasNext()) {
            TimeWarperPower.BlackHoleBlast blast = iterator.next();
            blast.age++;
            if (blast.age >= 0) {
               if (blast.age == 0) {
                  this.detonateBlackHoleBlast(blast);
               }

               double progress = Math.min(1.0, (double)blast.age / blast.duration);
               double r = blast.radius * (1.0 - Math.pow(1.0 - progress, 2.0));
               if (blast.age <= 12 || blast.age % 2 == 0) {
                  int points = (int)Math.max(24.0, Math.min(64.0, 24.0 + r * 3.0));

                  for (int i = 0; i < points; i++) {
                     double a = (Math.PI * 2) * i / points;
                     double ox = Math.cos(a) * r;
                     double oz = Math.sin(a) * r;
                     blast.world.spawnParticles(ParticleTypes.CLOUD, true, true, blast.center.x + ox, blast.center.y + 0.15, blast.center.z + oz, 1, 0.1, 0.05, 0.1, 0.01);
                     if (i % 2 == 0) {
                        blast.world
                           .spawnParticles(
                              ParticleTypes.SOUL_FIRE_FLAME, true, true,
                              blast.center.x + ox * 0.85,
                              blast.center.y + 0.4,
                              blast.center.z + oz * 0.85,
                              1,
                              0.05,
                              0.3,
                              0.05,
                              0.02
                           );
                     }
                  }

                  for (int ring = 1; ring <= 3; ring++) {
                     double elevation = ring * Math.PI / 8.0;
                     double rr = r * Math.cos(elevation);
                     double yy = blast.center.y + r * Math.sin(elevation) * 0.8;

                     for (int ix = 0; ix < points / 2; ix++) {
                        double a = (Math.PI * 4) * ix / points;
                        blast.world
                           .spawnParticles(
                              ParticleTypes.END_ROD, true, true, blast.center.x + Math.cos(a) * rr, yy, blast.center.z + Math.sin(a) * rr, 1, 0.02, 0.02, 0.02, 0.0
                           );
                     }
                  }
               }

               if (blast.multiplier >= 2.0 && blast.age % 4 == 0 && blast.age <= 16) {
                  for (int k = 0; k < (int)blast.multiplier; k++) {
                     double a = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2);
                     double d = ThreadLocalRandom.current().nextDouble(0.2, 0.8) * blast.radius;
                     cosmeticLightning(blast.world, blast.center.x + Math.cos(a) * d, blast.center.y, blast.center.z + Math.sin(a) * d);
                  }
               }

               if (blast.age >= blast.duration) {
                  iterator.remove();
               }
            }
         }
      }
   }

   private void detonateBlackHoleBlast(TimeWarperPower.BlackHoleBlast blast) {
      ServerWorld world = blast.world;
      Vec3d c = blast.center;
      double m = blast.multiplier;
      world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 6.0F, 0.4F);
      world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 8.0F, 0.5F);
      world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 5.0F, 0.6F);

      for (int i = 0; i < Math.min(4, 1 + (int)m); i++) {
         world.spawnParticles(
            ParticleTypes.EXPLOSION_EMITTER, true, true,
            c.x + (i == 0 ? 0.0 : ThreadLocalRandom.current().nextDouble(-2.0, 2.0)),
            c.y + i * 0.6,
            c.z + (i == 0 ? 0.0 : ThreadLocalRandom.current().nextDouble(-2.0, 2.0)),
            1,
            0.0,
            0.0,
            0.0,
            0.0
         );
      }

      world.spawnParticles(ParticleTypes.SONIC_BOOM, true, true, c.x, c.y + 0.5, c.z, 1, 0.0, 0.0, 0.0, 0.0);
      EpicVfx.flash(world, c.x, c.y + 0.5, c.z);
      EpicVfx.sphereBurst(world, ParticleTypes.END_ROD, c.x, c.y + 0.5, c.z, (int)(70.0 * Math.min(2.5, m)), 0.55 * m);
      EpicVfx.sphereBurst(world, ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y + 0.5, c.z, (int)(50.0 * Math.min(2.5, m)), 0.4 * m);
      EpicVfx.column(world, ParticleTypes.END_ROD, c.x, c.y, c.z, 10.0 * m, 24);
      EpicVfx.shockwave(world, c.x, c.y, c.z, blast.radius);
      cosmeticLightning(world, c.x, c.y, c.z);
      ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(blast.ownerId);
      if (owner != null && owner.isAlive()) {
         YPConfig config = YPConfig.getInstance();
         float baseDamage = (float)Math.max(0.0, Math.min(10.0, config.time_warper_black_hole_max_damage));
         Box area = new Box(c.x - blast.radius, c.y - blast.radius, c.z - blast.radius, c.x + blast.radius, c.y + blast.radius, c.z + blast.radius);

         for (LivingEntity target : world.getEntitiesByClass(
            LivingEntity.class, area, e -> e != owner && !e.isRemoved() && e.isAlive() && (e instanceof ServerPlayerEntity || e instanceof MobEntity)
         )) {
            double distance = Math.sqrt(target.squaredDistanceTo(c));
            if (!(distance > blast.radius)
               && (!(target instanceof ServerPlayerEntity) || config.time_warper_black_hole_affect_players)
               && (!(target instanceof MobEntity) || config.time_warper_black_hole_affect_mobs)
               && !(target instanceof ServerPlayerEntity other && AllianceManager.get(world.getServer()).isAlly(owner, other))
               && !PowerCombatUtil.isFriendlyMinion(owner, target)) {
               double falloff = 1.0 - 0.6 * (distance / blast.radius);
               float extra = (float)Math.min(config.time_warper_black_hole_max_blast_damage, baseDamage * (m - 1.0) * 1.5 * falloff);
               if (extra > 0.0F) {
                  damageWithoutMobDrops(world, target, extra);
               }

               Vec3d away = target.getEntityPos().subtract(c);
               if (away.lengthSquared() < 1.0E-5) {
                  away = new Vec3d(0.0, 1.0, 0.0);
               }

               Vec3d push = away.normalize().multiply(0.9 + 0.5 * m * falloff).add(0.0, 0.45 + 0.1 * m, 0.0);
               target.setVelocity(push);
               ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
            }
         }
      }
   }

   private void applyBlackHolePull(TimeWarperPower.BlackHoleState hole, ServerPlayerEntity owner, double progress, YPConfig config) {
      double radius = Math.max(1.0, config.time_warper_black_hole_entity_radius);
      Box area = new Box(
         hole.center.x - radius, hole.center.y - radius, hole.center.z - radius, hole.center.x + radius, hole.center.y + radius, hole.center.z + radius
      );

      for (Entity entity : hole.world
         .getEntitiesByClass(
            Entity.class,
            area,
            e -> e != owner && !e.isRemoved() && (e instanceof LivingEntity || config.time_warper_black_hole_affect_items && e instanceof ItemEntity)
         )) {
         if (!(entity.squaredDistanceTo(hole.center) > radius * radius)
            && (!(entity instanceof ServerPlayerEntity) || config.time_warper_black_hole_affect_players)
            && (!(entity instanceof MobEntity) || config.time_warper_black_hole_affect_mobs)
            && (entity instanceof ServerPlayerEntity || entity instanceof MobEntity || entity instanceof ItemEntity)
            && !(entity instanceof ServerPlayerEntity other && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other))) {
            if (entity instanceof ItemEntity item && item.squaredDistanceTo(hole.center) <= (1.5 + 2.0 * progress) * (1.5 + 2.0 * progress)) {
               hole.absorbed = hole.absorbed + item.getStack().getCount();
               hole.world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, item.getX(), item.getY() + 0.2, item.getZ(), 6, 0.1, 0.1, 0.1, 0.12);
               if (hole.elapsedTicks % 2 == 0) {
                  hole.world
                     .playSound(
                        null,
                        item.getBlockPos(),
                        SoundEvents.ENTITY_ENDERMAN_TELEPORT,
                        SoundCategory.PLAYERS,
                        0.25F,
                        (float)(0.6 + blackHolePowerFraction(hole, config) * 1.2)
                     );
               }

               item.discard();
            } else {
               Vec3d direction = hole.center.subtract(entity.getEntityPos());
               if (!(direction.lengthSquared() < 1.0E-5)) {
                  double distance = Math.sqrt(entity.squaredDistanceTo(hole.center));
                  double proximity = 1.0 - Math.min(1.0, distance / radius);
                  double acceleration = 0.12 + progress * 0.2 + proximity * 0.16;
                  Vec3d impulse = direction.normalize().multiply(acceleration);
                  Vec3d velocity = entity.getVelocity().multiply(0.72).add(impulse);
                  double maxInwardSpeed = 2.0;
                  if (velocity.lengthSquared() > maxInwardSpeed * maxInwardSpeed) {
                     velocity = velocity.normalize().multiply(maxInwardSpeed);
                  }

                  entity.setVelocity(velocity);
                  ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
                  if (hole.elapsedTicks % 4 == 0) {
                     hole.world
                        .spawnParticles(ParticleTypes.PORTAL, true, true, entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(), 2, 0.15, 0.2, 0.15, 0.02);
                  }
               }
            }
         }
      }
   }

   private boolean tickAwakenedBlackHoleSustain(TimeWarperPower.BlackHoleState hole, ServerPlayerEntity owner, YPConfig config, long serverTick) {
      UUID id = owner.getUuid();
      PowerManager manager = PowerManager.get(owner.getEntityWorld().getServer());
      Long lastHold = this.blackHoleHoldHeartbeats.get(id);
      boolean held = lastHold != null && serverTick - lastHold <= 40L;
      if (held
         && owner.isAlive()
         && manager.isPowerEnabled(owner, PowerType.TIME_WARPER)
         && AwakeningManager.isAwakened(manager, id, PowerType.TIME_WARPER)
         && this.isRealityForm(owner)
         && !GraceManager.isActive(owner.getEntityWorld().getServer())) {
         if (!hole.sustainStarted) {
            hole.sustainStarted = true;
            owner.sendMessage(
               Text.literal("The Black Hole is sustained. Keep holding V; its weak gravity field does not destroy terrain.").formatted(Formatting.DARK_PURPLE),
               true
            );
         }

         if (hole.sustainTicks >= Math.max(1, config.time_warper_awakening_sustain_max_ticks)) {
            owner.sendMessage(Text.literal("The Black Hole's awakened sustain limit was reached.").formatted(Formatting.YELLOW), true);
            return false;
         } else {
            hole.sustainTicks++;
            hole.sustainUpkeepTicks++;
            if (hole.sustainUpkeepTicks >= 20) {
               double healthCost = Math.max(0.0, config.time_warper_awakening_sustain_health_per_second);
               double energyCost = Math.max(0.0, config.time_warper_awakening_sustain_energy_per_second);
               if (owner.getHealth() - healthCost < 1.0 || !EnergyManager.get(owner.getEntityWorld().getServer()).trySpendFlightEnergy(owner, energyCost)) {
                  owner.sendMessage(Text.literal("Black Hole sustain ended: it can no longer be maintained safely.").formatted(Formatting.YELLOW), true);
                  return false;
               }

               if (healthCost > 0.0) {
                  owner.setHealth(Math.max(1.0F, owner.getHealth() - (float)healthCost));
               }

               hole.sustainUpkeepTicks = 0;
            }

            this.applySustainedBlackHoleField(hole, owner, config, serverTick);
            return true;
         }
      } else {
         return false;
      }
   }

   private void applySustainedBlackHoleField(TimeWarperPower.BlackHoleState hole, ServerPlayerEntity owner, YPConfig config, long serverTick) {
      double radius = Math.max(1.0, config.time_warper_awakening_sustain_radius);
      Box area = new Box(
         hole.center.x - radius, hole.center.y - radius, hole.center.z - radius, hole.center.x + radius, hole.center.y + radius, hole.center.z + radius
      );

      for (Entity entity : hole.world
         .getEntitiesByClass(
            Entity.class,
            area,
            candidate -> candidate != owner
               && !candidate.isRemoved()
               && (candidate instanceof LivingEntity || config.time_warper_black_hole_affect_items && candidate instanceof ItemEntity)
         )) {
         if (!(entity.squaredDistanceTo(hole.center) > radius * radius)
            && (!(entity instanceof ServerPlayerEntity) || config.time_warper_black_hole_affect_players)
            && (!(entity instanceof MobEntity) || config.time_warper_black_hole_affect_mobs)
            && (entity instanceof ServerPlayerEntity || entity instanceof MobEntity || entity instanceof ItemEntity)
            && !(entity instanceof ServerPlayerEntity other && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other))
            && !(entity instanceof LivingEntity living && PowerCombatUtil.isFriendlyMinion(owner, living))) {
            Vec3d inward = hole.center.subtract(entity.getEntityPos());
            if (!(inward.lengthSquared() < 1.0E-5)) {
               Vec3d velocity = entity.getVelocity().multiply(0.88).add(inward.normalize().multiply(0.025));
               double maximum = 0.85;
               if (velocity.lengthSquared() > maximum * maximum) {
                  velocity = velocity.normalize().multiply(maximum);
               }

               entity.setVelocity(velocity);
               ((EntityVelocityAccessor)entity).invokeYppowersScheduleVelocityUpdate();
            }
         }
      }

      if (serverTick % 5L == 0L) {
         double visualRadius = Math.min(radius, 4.0);
         EpicVfx.darkOrb(hole.world, hole.center.x, hole.center.y, hole.center.z, visualRadius * 0.72, 32);

         for (int i = 0; i < 6; i++) {
            double angle = serverTick * 0.12 + (Math.PI * 2) * i / 6.0;
            hole.world
               .spawnParticles(
                  BLACK_HOLE_DARK_PARTICLE, true, true,
                  hole.center.x + Math.cos(angle) * visualRadius,
                  hole.center.y + Math.sin(angle * 1.5) * 0.6,
                  hole.center.z + Math.sin(angle) * visualRadius,
                  1,
                  0.01,
                  0.01,
                  0.01,
                  0.001
               );
         }
      }
   }

   private void processBlackHoleTerrain(TimeWarperPower.BlackHoleState hole, ServerPlayerEntity owner, YPConfig config) {
      if (TimeWarperDomainManager.isTimeDomain(hole.world)) {
         hole.terrainFinished = true;
      } else if (config.time_warper_black_hole_terrain_damage && config.time_warper_black_hole_max_blocks_per_tick > 0) {
         int processed = 0;
         int max = Math.max(0, Math.min(512, config.time_warper_black_hole_max_blocks_per_tick));

         while (processed < max && !hole.terrainFinished) {
            BlockPos pos = hole.nextTerrainPosition();
            if (pos == null) {
               break;
            }

            processed++;
            if (hole.world.isChunkLoaded(ChunkPos.toLong(pos))) {
               BlockState state = hole.world.getBlockState(pos);
               if (!state.isAir()
                  && !state.isOf(Blocks.BEDROCK)
                  && !state.isOf(Blocks.END_PORTAL_FRAME)
                  && !state.isOf(Blocks.HEAVY_CORE)
                  && !(state.getHardness(hole.world, pos) < 0.0F)) {
                  BlockEntity blockEntity = hole.world.getBlockEntity(pos);
                  if (blockEntity == null
                     && !(
                        pos.getSquaredDistance(hole.center.x, hole.center.y, hole.center.z)
                           > config.time_warper_black_hole_terrain_radius * config.time_warper_black_hole_terrain_radius
                     )) {
                     hole.world.breakBlock(pos, false, owner);
                     hole.absorbed = hole.absorbed + Math.max(0.0, config.time_warper_black_hole_block_item_value);
                  }
               }
            }
         }
      }
   }

   private void beginBlackHoleRecollapse(TimeWarperPower.BlackHoleState hole, ServerPlayerEntity owner, YPConfig config) {
      double multiplier = blackHoleMultiplier(hole, config);
      double radius = Math.max(1.0, config.time_warper_black_hole_implosion_radius) * (1.0 + (multiplier - 1.0) * 0.5);
      Box area = new Box(
         hole.center.x - radius, hole.center.y - radius, hole.center.z - radius, hole.center.x + radius, hole.center.y + radius, hole.center.z + radius
      );

      for (LivingEntity target : hole.world
         .getEntitiesByClass(
            LivingEntity.class, area, e -> e != owner && !e.isRemoved() && e.isAlive() && (e instanceof ServerPlayerEntity || e instanceof MobEntity)
         )) {
         if (!(target.squaredDistanceTo(hole.center) > radius * radius)
            && (!(target instanceof ServerPlayerEntity) || config.time_warper_black_hole_affect_players)
            && (!(target instanceof MobEntity) || config.time_warper_black_hole_affect_mobs)
            && !(target instanceof ServerPlayerEntity other && AllianceManager.get(hole.world.getServer()).isAlly(owner, other))
            && !PowerCombatUtil.isFriendlyMinion(owner, target)) {
            hole.recollapseTargets.put(target.getUuid(), target);
            Vec3d away = target.getEntityPos().subtract(hole.center);
            if (away.lengthSquared() < 1.0E-5) {
               long bits = target.getUuid().getLeastSignificantBits();
               double angle = (bits & 65535L) / 65535.0 * Math.PI * 2.0;
               away = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
            }

            double falloff = 1.0 - 0.5 * Math.min(1.0, target.squaredDistanceTo(hole.center) / (radius * radius));
            Vec3d burst = away.normalize().multiply(1.35 + 0.3 * multiplier * falloff).add(0.0, 0.45 + 0.1 * multiplier * falloff, 0.0);
            target.setVelocity(burst);
            ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
            hole.world.spawnParticles(ParticleTypes.POOF, true, true, target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ(), 5, 0.18, 0.22, 0.18, 0.04);
         }
      }

      hole.outwardBurstTicksRemaining = 6;
      hole.recollapseTicks = 0;
      hole.world
         .spawnParticles(
            ParticleTypes.CLOUD, true, true,
            hole.center.x,
            hole.center.y,
            hole.center.z,
            28,
            Math.min(2.0, radius * 0.12),
            Math.min(1.0, radius * 0.06),
            Math.min(2.0, radius * 0.12),
            0.1
         );
      hole.world.playSound(null, BlockPos.ofFloored(hole.center), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 2.2F, 0.55F);
      owner.sendMessage(Text.literal("BLACK HOLE — the singularity is pulling everything back in...").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
   }

   private boolean tickBlackHoleRecollapse(TimeWarperPower.BlackHoleState hole, YPConfig config) {
      if (hole.outwardBurstTicksRemaining > 0) {
         hole.outwardBurstTicksRemaining--;
         if (hole.outwardBurstTicksRemaining % 2 == 0) {
            for (Entity entity : hole.recollapseTargets.values()) {
               if (entity != null && !entity.isRemoved() && entity.getEntityWorld() == hole.world) {
                  hole.world
                     .spawnParticles(ParticleTypes.CLOUD, true, true, entity.getX(), entity.getY() + entity.getHeight() * 0.5, entity.getZ(), 2, 0.08, 0.08, 0.08, 0.02);
               }
            }
         }

         return false;
      } else {
         int duration = Math.max(1, config.time_warper_black_hole_recollapse_ticks);
         hole.recollapseTicks++;
         double progress = Math.min(1.0, (double)hole.recollapseTicks / duration);
         Iterator<Entry<UUID, Entity>> targets = hole.recollapseTargets.entrySet().iterator();

         while (targets.hasNext()) {
            Entity entityx = targets.next().getValue();
            if (entityx != null
               && !entityx.isRemoved()
               && entityx.getEntityWorld() == hole.world
               && !(entityx instanceof LivingEntity living && !living.isAlive())) {
               Vec3d inward = hole.center.subtract(entityx.getEntityPos().add(0.0, entityx.getHeight() * 0.5, 0.0));
               double distance = inward.length();
               if (distance > 0.08) {
                  double strength = 0.34 + 0.48 * progress;
                  Vec3d velocity = entityx.getVelocity().multiply(0.48).add(inward.multiply(1.0 / distance).multiply(strength));
                  double maxSpeed = 2.65;
                  if (velocity.lengthSquared() > maxSpeed * maxSpeed) {
                     velocity = velocity.normalize().multiply(maxSpeed);
                  }

                  entityx.setVelocity(velocity);
                  ((EntityVelocityAccessor)entityx).invokeYppowersScheduleVelocityUpdate();
               } else {
                  entityx.setVelocity(entityx.getVelocity().multiply(0.25));
                  ((EntityVelocityAccessor)entityx).invokeYppowersScheduleVelocityUpdate();
               }

               if (hole.recollapseTicks % 3 == 0) {
                  hole.world
                     .spawnParticles(
                        ParticleTypes.REVERSE_PORTAL, true, true, entityx.getX(), entityx.getY() + entityx.getHeight() * 0.5, entityx.getZ(), 2, 0.08, 0.1, 0.08, 0.08
                     );
               }
            } else {
               targets.remove();
            }
         }

         if (hole.recollapseTicks % 2 == 0) {
            double swirl = 0.35 + (1.0 - progress) * 3.0;
            double rotation = hole.recollapseTicks * 0.52;

            for (int i = 0; i < 10; i++) {
               double angle = rotation + (Math.PI * 2) * i / 10.0;
               double yOffset = Math.sin(angle * 2.0) * swirl * 0.22;
               hole.world
                  .spawnParticles(
                     BLACK_HOLE_DARK_PARTICLE, true, true,
                     hole.center.x + Math.cos(angle) * swirl,
                     hole.center.y + yOffset,
                     hole.center.z + Math.sin(angle) * swirl,
                     1,
                     0.015,
                     0.015,
                     0.015,
                     0.001
                  );
            }

            hole.world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, hole.center.x, hole.center.y, hole.center.z, 8, 0.25, 0.25, 0.25, 0.08);
         }

         if (hole.recollapseTicks % 10 == 0) {
            hole.world
               .playSound(
                  null, BlockPos.ofFloored(hole.center), SoundEvents.BLOCK_BEACON_AMBIENT, SoundCategory.PLAYERS, 1.2F, (float)(0.45F + progress * 0.45F)
               );
         }

         return hole.recollapseTicks >= duration;
      }
   }

   private void finishBlackHole(TimeWarperPower.BlackHoleState hole, ServerPlayerEntity owner, YPConfig config) {
      double multiplier = blackHoleMultiplier(hole, config);
      double radius = Math.max(1.0, config.time_warper_black_hole_implosion_radius) * (1.0 + (multiplier - 1.0) * 0.5);
      Box area = new Box(
         hole.center.x - radius, hole.center.y - radius, hole.center.z - radius, hole.center.x + radius, hole.center.y + radius, hole.center.z + radius
      );
      float damage = (float)Math.max(0.0, Math.min(10.0, config.time_warper_black_hole_max_damage));

      for (LivingEntity target : hole.world
         .getEntitiesByClass(
            LivingEntity.class, area, e -> e != owner && !e.isRemoved() && e.isAlive() && (e instanceof ServerPlayerEntity || e instanceof MobEntity)
         )) {
         if (!(target.squaredDistanceTo(hole.center) > radius * radius)
            && (!(target instanceof ServerPlayerEntity) || config.time_warper_black_hole_affect_players)
            && (!(target instanceof MobEntity) || config.time_warper_black_hole_affect_mobs)
            && !(target instanceof ServerPlayerEntity other && AllianceManager.get(hole.world.getServer()).isAlly(owner, other))
            && !PowerCombatUtil.isFriendlyMinion(owner, target)) {
            if (damage > 0.0F) {
               hole.absorbed = hole.absorbed + damageWithoutMobDrops(hole.world, target, damage);
            }

            Vec3d direction = target.getEntityPos().subtract(hole.center);
            if (direction.lengthSquared() < 1.0E-5) {
               long bits = target.getUuid().getLeastSignificantBits();
               double angle = (bits & 65535L) / 65535.0 * Math.PI * 2.0;
               direction = new Vec3d(Math.cos(angle), 0.0, Math.sin(angle));
            }

            double falloff = 1.0 - 0.5 * Math.min(1.0, target.squaredDistanceTo(hole.center) / (radius * radius));
            double flingStrength = 1.8 + 0.4 * multiplier * falloff;
            Vec3d launch = direction.normalize().multiply(flingStrength).add(0.0, 0.7 + 0.15 * multiplier * falloff, 0.0);
            double maxLaunchSpeed = 3.2;
            if (launch.lengthSquared() > maxLaunchSpeed * maxLaunchSpeed) {
               launch = launch.normalize().multiply(maxLaunchSpeed);
            }

            target.setVelocity(launch);
            ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
         }
      }

      double vfxRadius = Math.max(2.5, config.time_warper_black_hole_entity_radius * 0.45);
      this.blackHoleImplosionVfx.add(new TimeWarperPower.BlackHoleImplosionVfx(hole.world, hole.center, vfxRadius));
      hole.world.playSound(null, BlockPos.ofFloored(hole.center), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0F, 0.45F);
      EpicVfx.cue(hole.world, owner.getId(), "time_warper", "detonation", hole.center.x, hole.center.y, hole.center.z, Vec3d.ZERO, 2.0F, 36, 0.35F);
      if (multiplier > 1.01) {
         this.blackHoleBlasts
            .add(
               new TimeWarperPower.BlackHoleBlast(
                  hole.world, hole.center, hole.ownerId, Math.max(1.0, config.time_warper_black_hole_implosion_radius) * multiplier, multiplier, 5
               )
            );
      }

      owner.sendMessage(
         Text.literal(String.format(Locale.ROOT, "Black Hole detonated — %d items absorbed — Power x%.2f", (int)hole.absorbed, multiplier))
            .formatted(Formatting.DARK_PURPLE),
         true
      );
   }

   public boolean isBlackHoleCharging(UUID id) {
      return this.blackHoles.containsKey(id);
   }

   public boolean isRealityForm(ServerPlayerEntity player) {
      return player != null
         && player.getEntityWorld().getServer() != null
         && "REALITY".equals(PowerManager.get(player.getEntityWorld().getServer()).getTimeWarperForm(player.getUuid()));
   }

   @Override
   public boolean canUseKey(ServerPlayerEntity player, int keyType) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         boolean reality = this.isRealityForm(player);
         if (keyType == 4) {
            int cd = PowerManager.get(player.getEntityWorld().getServer()).getTimeWarperFormCooldownTicks(player.getUuid());
            if (cd > 0) {
               player.sendMessage(Text.literal("Form switch cooldown: " + (cd + 19) / 20 + "s"), true);
               return false;
            } else {
               return true;
            }
         } else if (!reality) {
            return keyType == 0 || keyType == 1;
         } else if (keyType != 0 && keyType != 3) {
            return keyType == 2;
         } else {
            List<TimeWarperPower.TimeClone> clones = this.timeClones.getOrDefault(player.getUuid(), Collections.emptyList());
            int living = (int)clones.stream().filter(c -> c.entity.isAlive() && !c.entity.isRemoved()).count();
            if (living >= Math.max(1, YPConfig.getInstance().time_warper_clone_max_active)) {
               player.sendMessage(Text.literal("Time Clone cap reached (" + YPConfig.getInstance().time_warper_clone_max_active + ")."), true);
               return false;
            } else if (keyType == 0
               && PowerManager.get(player.getEntityWorld().getServer()).getTimeShards(player.getUuid())
                  < Math.max(0, YPConfig.getInstance().time_warper_clone_shards_cost)) {
               player.sendMessage(Text.literal("Not enough Time Shards. Collect them by physically touching the black shards."), true);
               return false;
            } else if (keyType == 3 && !this.hasGoldenShard(player)) {
               player.sendMessage(Text.literal("An inventory Golden Time Shard is required for an Elite Clone."), true);
               return false;
            } else {
               return true;
            }
         }
      } else {
         return false;
      }
   }

   @Override
   public void onAlternateActivate(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         PowerManager data = PowerManager.get(server);
         UUID id = player.getUuid();
         int cooldown = data.getTimeWarperFormCooldownTicks(id);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Form switch cooldown: " + (cooldown + 19) / 20 + "s"), true);
         } else {
            String next = this.isRealityForm(player) ? "TIME" : "REALITY";
            data.setTimeWarperForm(id, next);
            data.setTimeWarperFormCooldownTicks(id, Math.max(0, YPConfig.getInstance().time_warper_form_switch_cooldown_ticks));
            player.sendMessage(
               Text.literal("Time Warper form switched to " + next + ". Ability cooldowns and summons are unchanged.")
                  .formatted(next.equals("TIME") ? Formatting.AQUA : Formatting.DARK_PURPLE, Formatting.BOLD),
               true
            );
            player.getEntityWorld()
               .playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0F, next.equals("TIME") ? 1.2F : 0.7F);
            ServerWorld var8 = player.getEntityWorld();
            if (var8 instanceof ServerWorld) {
               EpicVfx.activation(var8, player.getX(), player.getY(), player.getZ(), 1.0);
            }
         }
      }
   }

   @Override
   public void onQuaternaryActivate(ServerPlayerEntity player) {
      this.summonTimeClone(player, true);
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         UUID playerId = player.getUuid();
         long currentTicks = server.getTicks();
         int cooldownTicks = Math.max(0, YPConfig.getInstance().time_warp_rewind_cooldown_ticks);
         Long lastUse = this.timeWarpCooldown.get(playerId);
         if (cooldownTicks > 0 && lastUse != null && currentTicks - lastUse < cooldownTicks) {
            long remaining = cooldownTicks - (currentTicks - lastUse);
            player.sendMessage(Text.literal("Time Warp on cooldown: " + (remaining + 19L) / 20L + "s"), true);
         } else {
            boolean rewoundAny = false;

            for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
               Deque<TimeWarperPower.PlayerSnapshot> history = this.playerHistories.get(target.getUuid());
               if (history != null && history.size() > 500) {
                  TimeWarperPower.PlayerSnapshot snapshot = history.getFirst();
                  ServerWorld targetWorld = server.getWorld(snapshot.dimension);
                  if (targetWorld != null) {
                     target.teleport(targetWorld, snapshot.x, snapshot.y, snapshot.z, Set.of(), snapshot.yaw, snapshot.pitch, true);
                     rewoundAny = true;
                     target.setHealth(Math.min(snapshot.health, target.getMaxHealth()));
                     target.setVelocity(Vec3d.ZERO);
                     ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();

                     for (List<TimeWarperPower.FrozenEntity> frozenList : this.frozenEntities.values()) {
                        for (TimeWarperPower.FrozenEntity frozen : frozenList) {
                           if (frozen.entity == target) {
                              frozen.position = target.getEntityPos();
                              frozen.velocity = Vec3d.ZERO;
                              frozen.yaw = snapshot.yaw;
                              frozen.pitch = snapshot.pitch;
                           }
                        }
                     }

                     targetWorld.playSound(null, target.getBlockPos(), SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                     EpicVfx.cue(targetWorld, player.getId(), "time_warper", "phase", target.getX(), target.getY(), target.getZ(), Vec3d.ZERO, 1.0F, 24, 0.0F);
                     targetWorld.spawnParticles(ParticleTypes.PORTAL, true, true, target.getX(), target.getY() + 1.0, target.getZ(), 24, 0.5, 0.8, 0.5, 0.1);
                     EpicVfx.implosion(targetWorld, ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + 0.5, target.getZ(), 2.0, 18, 0.25);
                     EpicVfx.shockwave(targetWorld, target.getX(), target.getY(), target.getZ(), 4.0);
                     if (target != player) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 1));
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 120, 0));
                     }
                  }
               } else if (target == player) {
                  player.sendMessage(Text.literal("Not enough history yet; Time Warp needs 25 seconds of recorded play."), true);
               }
            }

            if (rewoundAny && cooldownTicks > 0) {
               this.timeWarpCooldown.put(playerId, currentTicks);
            }

            player.sendMessage(Text.literal("Time Warp! Rewound all players by 25 seconds."), true);
         }
      }
   }

   @Override
   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, float amount) {
      return this.timeStopActive.containsKey(player.getUuid()) ? amount * 0.5F : amount;
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      TimeWarperDomainManager.onPowerRemoved(player);
      this.timeStopActive.remove(uuid);
      this.timeWarpCooldown.remove(uuid);
      this.blackHoles.remove(uuid);
      this.blackHoleHoldHeartbeats.remove(uuid);
      List<TimeWarperPower.FrozenEntity> frozen = this.frozenEntities.remove(uuid);
      if (frozen != null) {
         for (TimeWarperPower.FrozenEntity fe : frozen) {
            releaseFrozen(fe.entity);
            releaseTimeStopVisual(fe.entity);
            if (fe.entity.isAlive()
               && !fe.entity.isRemoved()
               && !FREEZE_COUNTS.containsKey(fe.entity)
               && !SeriousPunchPower.isEntityFrozen(fe.entity)
               && !IceEmperorPower.isEntityFrozen(fe.entity)) {
               fe.entity.setVelocity(fe.velocity);
               ((EntityVelocityAccessor)fe.entity).invokeYppowersScheduleVelocityUpdate();
            }
         }
      }

      List<TimeWarperPower.TimeClone> ownedClones = this.timeClones.remove(uuid);
      if (ownedClones != null) {
         for (TimeWarperPower.TimeClone clone : ownedClones) {
            clone.entity.discard();
         }
      }

      this.lastOwnerTargets.remove(uuid);
   }

   private void summonTimeClone(ServerPlayerEntity owner, boolean elite) {
      ServerWorld id = owner.getEntityWorld();
      if (id instanceof ServerWorld && owner.getEntityWorld().getServer() != null) {
         UUID idx = owner.getUuid();
         PowerManager data = PowerManager.get(id.getServer());
         if (!elite || this.hasGoldenShard(owner)) {
            if (elite || data.getTimeShards(idx) >= Math.max(0, YPConfig.getInstance().time_warper_clone_shards_cost)) {
               ZombieEntity clone = EntityType.ZOMBIE.create(id, SpawnReason.TRIGGERED);
               if (clone == null) {
                  owner.sendMessage(Text.literal("The clone could not manifest."), true);
               } else {
                  List<TimeWarperPower.TimeClone> list = this.timeClones.computeIfAbsent(idx, ignored -> new ArrayList<>());
                  clone.refreshPositionAndAngles(
                     owner.getX() + (list.size() % 5 - 2) * 1.2, owner.getY(), owner.getZ() + 1.5 + list.size() / 5 * 0.8, owner.getYaw(), 0.0F
                  );
                  clone.setCustomName(Text.literal("YPCLONE|" + idx + "|" + (elite ? "G" : "N")));
                  clone.setCustomNameVisible(false);
                  clone.addCommandTag("yppowers_time_clone");
                  clone.addCommandTag("yppowers_owner_" + idx);
                  if (elite) {
                     clone.addCommandTag("yppowers_time_clone_golden");
                  }

                  clone.setPersistent();
                  clone.setCanPickUpLoot(false);
                  clone.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                  if (elite) {
                     clone.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                     clone.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                     if (clone.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
                        clone.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(40.0);
                     }

                     clone.setHealth(40.0F);
                  } else {
                     clone.setHealth(clone.getMaxHealth());
                  }

                  SummonTuning.strengthen(owner, clone);
                  equipTimeClone(clone, elite);
                  if (elite) {
                     if (!this.consumeGoldenShard(owner)) {
                        clone.discard();
                        return;
                     }
                  } else if (!data.spendTimeShards(idx, Math.max(0, YPConfig.getInstance().time_warper_clone_shards_cost))) {
                     clone.discard();
                     return;
                  }

                  id.spawnEntity(clone);
                  list.add(new TimeWarperPower.TimeClone(clone, elite, Math.max(20, YPConfig.getInstance().time_warper_clone_lifespan_ticks)));
                  id.spawnParticles(ParticleTypes.PORTAL, true, true, clone.getX(), clone.getBodyY(0.5), clone.getZ(), elite ? 40 : 18, 0.35, 0.5, 0.35, 0.2);
                  EpicVfx.activation(id, clone.getX(), clone.getY(), clone.getZ(), elite ? 1.3 : 0.9);
                  EpicVfx.shockwave(id, clone.getX(), clone.getY(), clone.getZ(), elite ? 5.0 : 3.5);
                  id.playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.8F, elite ? 0.6F : 1.3F);
                  owner.sendMessage(
                     Text.literal(elite ? "Elite Time Clone summoned." : "Time Clone summoned. Shards remaining: " + data.getTimeShards(idx)), true
                  );
               }
            }
         }
      }
   }

   private boolean hasGoldenShard(ServerPlayerEntity player) {
      for (int i = 0; i < player.getInventory().size(); i++) {
         if (YPItemData.hasFlag(player.getInventory().getStack(i), "YPGoldenTimeShard")) {
            return true;
         }
      }

      return false;
   }

   private boolean consumeGoldenShard(ServerPlayerEntity player) {
      for (int i = 0; i < player.getInventory().size(); i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (YPItemData.hasFlag(stack, "YPGoldenTimeShard")) {
            stack.decrement(1);
            return true;
         }
      }

      return false;
   }

   @Override
   public void onAttack(ServerPlayerEntity attacker, Entity target) {
      if (target != null && target.isAlive() && PowerCombatUtil.canHarm(attacker, target)) {
         this.lastOwnerTargets.put(attacker.getUuid(), target);
      }
   }

   private void tickTimeClones(MinecraftServer server) {
      PowerManager data = PowerManager.get(server);
      Iterator<Entry<UUID, List<TimeWarperPower.TimeClone>>> owners = this.timeClones.entrySet().iterator();

      while (owners.hasNext()) {
         Entry<UUID, List<TimeWarperPower.TimeClone>> entry = owners.next();
         UUID ownerId = entry.getKey();
         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
         if (owner == null) {
            List<NbtCompound> snapshots = new ArrayList<>();

            for (TimeWarperPower.TimeClone c : entry.getValue()) {
               if (c.entity.isAlive() && !c.entity.isRemoved() && c.ticks > 0) {
                  NbtCompound nbt = new NbtCompound();
                  nbt.putString("Variant", c.elite ? "ELITE" : "ORDINARY");
                  nbt.putInt("Ticks", c.ticks);
                  snapshots.add(nbt);
                  c.entity.discard();
               }
            }

            data.saveStoredTimeClones(ownerId, snapshots);
            owners.remove();
         } else {
            List<TimeWarperPower.TimeClone> list = entry.getValue();
            list.removeIf(cx -> {
               if (!cx.entity.isRemoved() && cx.entity.isAlive() && --cx.ticks > 0) {
                  return false;
               } else {
                  cx.entity.discard();
                  return true;
               }
            });
            if (list.isEmpty()) {
               owners.remove();
            } else {
               Entity last = this.lastOwnerTargets.get(ownerId);

               for (TimeWarperPower.TimeClone cx : list) {
                  if (cx.ticks % 10 == 0) {
                     Entity target = last != null && last.isAlive() && last.getEntityWorld() == owner.getEntityWorld() && PowerCombatUtil.canHarm(owner, last)
                        ? last
                        : null;
                     if (target == null) {
                        target = PowerCombatUtil.nearestTarget(owner, 24.0);
                     }

                     if (target instanceof LivingEntity living) {
                        cx.entity.setTarget(living);
                     } else {
                        cx.entity.setTarget(null);
                     }

                     if (target == null && cx.entity.squaredDistanceTo(owner) > 100.0) {
                        cx.entity.getNavigation().startMovingTo(owner, 1.15);
                     } else if (target != null) {
                        cx.entity.getNavigation().stop();
                     }

                     if (cx.elite && cx.ticks % 5 == 0 && cx.entity.getEntityWorld() instanceof ServerWorld cloneWorld) {
                        cloneWorld.spawnParticles(ParticleTypes.PORTAL, true, true, cx.entity.getX(), cx.entity.getY() + 1.0, cx.entity.getZ(), 0, 0.0, 0.04, 0.0, 1.0);
                     }
                  }
               }
            }
         }
      }
   }

   private static void equipTimeClone(ZombieEntity clone, boolean elite) {
      clone.equipStack(EquipmentSlot.MAINHAND, new ItemStack(elite ? Items.DIAMOND_SWORD : Items.STONE_SWORD));
      ItemStack helmet = new ItemStack(elite ? Items.DIAMOND_HELMET : Items.IRON_HELMET);
      ItemStack chest = new ItemStack(elite ? Items.DIAMOND_CHESTPLATE : Items.IRON_CHESTPLATE);
      ItemStack legs = new ItemStack(elite ? Items.DIAMOND_LEGGINGS : Items.IRON_LEGGINGS);
      ItemStack boots = new ItemStack(elite ? Items.DIAMOND_BOOTS : Items.IRON_BOOTS);
      clone.equipStack(EquipmentSlot.HEAD, helmet);
      clone.equipStack(EquipmentSlot.CHEST, chest);
      clone.equipStack(EquipmentSlot.LEGS, legs);
      clone.equipStack(EquipmentSlot.FEET, boots);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.restoreStoredClones(player);
   }

   private void restoreStoredClones(ServerPlayerEntity owner) {
      if (owner.getEntityWorld().getServer() != null) {
         ServerWorld saved = owner.getEntityWorld();
         if (saved instanceof ServerWorld) {
            ServerWorld world = saved;
            List<NbtCompound> var11 = PowerManager.get(saved.getServer()).takeStoredTimeClones(owner.getUuid());
            List list = this.timeClones.computeIfAbsent(owner.getUuid(), ignored -> new ArrayList<>());
            int index = 0;

            for (NbtCompound nbt : var11) {
               if (list.size() >= Math.max(1, YPConfig.getInstance().time_warper_clone_max_active)) {
                  break;
               }

               boolean elite = nbt.getString("Variant", "").equals("ELITE");
               int ticks = Math.max(20, nbt.getInt("Ticks", 20));
               ZombieEntity clone = EntityType.ZOMBIE.create(world, SpawnReason.TRIGGERED);
               if (clone != null) {
                  clone.refreshPositionAndAngles(owner.getX() + (index % 5 - 2) * 1.2, owner.getY(), owner.getZ() + 1.5 + index / 5 * 0.8, owner.getYaw(), 0.0F);
                  clone.setCustomName(Text.literal("YPCLONE|" + owner.getUuid() + "|" + (elite ? "G" : "N")));
                  clone.addCommandTag("yppowers_time_clone");
                  clone.addCommandTag("yppowers_owner_" + owner.getUuid());
                  if (elite) {
                     clone.addCommandTag("yppowers_time_clone_golden");
                  }

                  clone.setPersistent();
                  clone.setCanPickUpLoot(false);
                  clone.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                  if (elite) {
                     clone.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                     clone.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                     if (clone.getAttributeInstance(EntityAttributes.MAX_HEALTH) != null) {
                        clone.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(40.0);
                     }

                     clone.setHealth(40.0F);
                  } else {
                     clone.setHealth(clone.getMaxHealth());
                  }

                  SummonTuning.strengthen(owner, clone);
                  equipTimeClone(clone, elite);
                  world.spawnEntity(clone);
                  list.add(new TimeWarperPower.TimeClone(clone, elite, ticks));
                  index++;
               }
            }

            return;
         }
      }
   }

   public static void clearBlackHoleForPlayerDeath(ServerPlayerEntity player) {
      if (player != null && player.getEntityWorld().getServer() != null) {
         if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.TIME_WARPER) instanceof TimeWarperPower timeWarper) {
            timeWarper.blackHoles.remove(player.getUuid());
            timeWarper.blackHoleHoldHeartbeats.remove(player.getUuid());
         }
      }
   }

   @Override
   public void onPlayerDisconnect(ServerPlayerEntity player) {
      this.blackHoles.remove(player.getUuid());
      this.blackHoleHoldHeartbeats.remove(player.getUuid());
      UUID id = player.getUuid();
      this.lastOwnerTargets.remove(id);
      List<TimeWarperPower.TimeClone> list = this.timeClones.remove(id);
      if (list != null) {
         List<NbtCompound> saved = new ArrayList<>();

         for (TimeWarperPower.TimeClone c : list) {
            if (c.entity.isAlive() && !c.entity.isRemoved() && c.ticks > 0) {
               NbtCompound nbt = new NbtCompound();
               nbt.putString("Variant", c.elite ? "ELITE" : "ORDINARY");
               nbt.putInt("Ticks", c.ticks);
               saved.add(nbt);
               c.entity.discard();
            }
         }

         if (player.getEntityWorld().getServer() != null) {
            PowerManager.get(player.getEntityWorld().getServer()).saveStoredTimeClones(id, saved);
         }
      }
   }

   public static void dropTimeShards(ServerWorld world, Entity dead) {
      if (world != null && dead != null && !(dead instanceof ServerPlayerEntity)) {
         YPConfig cfg = YPConfig.getInstance();
         if (world.random.nextDouble() < Math.max(0.0, Math.min(1.0, cfg.time_warper_time_shard_drop_chance))) {
            ItemStack stack = new ItemStack(Items.ECHO_SHARD);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Time Shard").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
            YPItemData.setFlag(stack, "YPTimeShard", true);
            ItemEntity entity = new ItemEntity(world, dead.getX(), dead.getY() + 0.2, dead.getZ(), stack);
            entity.setPickupDelay(32767);
            entity.setInvulnerable(true);
            world.spawnEntity(entity);
            TIME_SHARD_LIFETIME.put(entity.getUuid(), Math.max(1, cfg.time_warper_time_shard_lifetime_ticks));
         }

         boolean boss = dead.getType() == EntityType.WITHER
            || dead.getType() == EntityType.ENDER_DRAGON
            || dead.getType() == EntityType.WARDEN
            || dead.getType() == EntityType.ELDER_GUARDIAN;
         if (boss && world.random.nextDouble() < Math.max(0.0, Math.min(1.0, cfg.time_warper_golden_shard_drop_chance))) {
            ItemStack golden = new ItemStack(Items.NETHER_STAR);
            golden.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Golden Time Shard").formatted(Formatting.GOLD, Formatting.BOLD));
            YPItemData.setFlag(golden, "YPGoldenTimeShard", true);
            ItemEntity entity = new ItemEntity(world, dead.getX(), dead.getY() + 0.4, dead.getZ(), golden);
            entity.setPickupDelay(32767);
            entity.setInvulnerable(true);
            world.spawnEntity(entity);
            GOLDEN_SHARD_IDS.add(entity.getUuid());
            world.spawnParticles(ParticleTypes.END_ROD, true, true, dead.getX(), dead.getY() + 0.5, dead.getZ(), 30, 0.4, 0.4, 0.4, 0.04);
         }
      }
   }

   public static void tryCollectTimeShard(ServerPlayerEntity player) {
      if (!TIME_SHARD_LIFETIME.isEmpty() || !GOLDEN_SHARD_IDS.isEmpty()) {
         if (player != null && player.getEntityWorld().getServer() != null) {
            ServerWorld manager = player.getEntityWorld();
            if (manager instanceof ServerWorld) {
               ServerWorld world = manager;
               PowerManager managerx = PowerManager.get(manager.getServer());
               if (managerx.getPowerType(player.getUuid()) != PowerType.TIME_WARPER) {
                  return;
               }

               for (ItemEntity shard : manager.getEntitiesByClass(
                  ItemEntity.class,
                  player.getBoundingBox().expand(0.2),
                  e -> YPItemData.hasFlag(e.getStack(), "YPTimeShard") || YPItemData.hasFlag(e.getStack(), "YPGoldenTimeShard")
               )) {
                  if (shard.getBoundingBox().intersects(player.getBoundingBox())) {
                     if (YPItemData.hasFlag(shard.getStack(), "YPGoldenTimeShard")) {
                        ItemStack golden = shard.getStack().copy();
                        if (player.getInventory().insertStack(golden)) {
                           GOLDEN_SHARD_IDS.remove(shard.getUuid());
                           shard.discard();
                           player.sendMessage(Text.literal("Golden Time Shard collected.").formatted(Formatting.GOLD), true);
                           world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.7F, 1.2F);
                        }
                     } else {
                        managerx.addTimeShards(player.getUuid(), 1);
                        shard.discard();
                        player.sendMessage(
                           Text.literal("Time Shard collected: " + managerx.getTimeShards(player.getUuid())).formatted(Formatting.DARK_PURPLE), true
                        );
                        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.55F, 1.7F);
                     }
                  }
               }

               return;
            }
         }
      }
   }

   private static ItemEntity findShard(MinecraftServer server, UUID id) {
      for (ServerWorld world : server.getWorlds()) {
         if (world.getEntity(id) instanceof ItemEntity item) {
            return item;
         }
      }

      return null;
   }

   private static void tickTimeShards(MinecraftServer server) {
      if (!TIME_SHARD_LIFETIME.isEmpty() || !GOLDEN_SHARD_IDS.isEmpty()) {
         Iterator<Entry<UUID, Integer>> it = TIME_SHARD_LIFETIME.entrySet().iterator();

         while (it.hasNext()) {
            Entry<UUID, Integer> entry = it.next();
            ItemEntity item = findShard(server, entry.getKey());
            if (item != null && !item.isRemoved()) {
               int remaining = entry.getValue() - 1;
               if (remaining <= 0) {
                  item.discard();
                  it.remove();
               } else {
                  entry.setValue(remaining);
                  if (server.getTicks() % 3 == 0 && item.getEntityWorld() instanceof ServerWorld shardWorld) {
                     shardWorld.spawnParticles(BLACK_HOLE_DARK_PARTICLE, true, true, item.getX(), item.getY() + 0.15, item.getZ(), 3, 0.12, 0.12, 0.12, 0.0);
                  }
               }
            } else {
               it.remove();
            }
         }

         GOLDEN_SHARD_IDS.removeIf(goldenId -> {
            ItemEntity golden = findShard(server, goldenId);
            return golden == null || golden.isRemoved();
         });
      }
   }

   public static void clearTimeShardRuntimeState() {
      TIME_SHARD_LIFETIME.clear();
      GOLDEN_SHARD_IDS.clear();
   }

   private static final class BlackHoleBlast {
      final ServerWorld world;
      final Vec3d center;
      final UUID ownerId;
      final double radius;
      final double multiplier;
      final int duration;
      int age;

      BlackHoleBlast(ServerWorld world, Vec3d center, UUID ownerId, double radius, double multiplier, int delay) {
         this.world = world;
         this.center = center;
         this.ownerId = ownerId;
         this.radius = radius;
         this.multiplier = multiplier;
         this.duration = (int)Math.round(20.0 + 6.0 * multiplier);
         this.age = -delay;
      }
   }

   private static final class BlackHoleImplosionVfx {
      final ServerWorld world;
      final Vec3d center;
      final double radius;
      int age;

      BlackHoleImplosionVfx(ServerWorld world, Vec3d center, double radius) {
         this.world = world;
         this.center = center;
         this.radius = radius;
      }
   }

   private static final class BlackHoleState {
      final UUID ownerId;
      final ServerWorld world;
      final Vec3d center;
      final BlockPos terrainOrigin;
      final int terrainRadiusBlocks;
      final double terrainRadiusSquared;
      int elapsedTicks;
      int terrainShell;
      int terrainX;
      int terrainY;
      int terrainZ;
      boolean terrainFinished;
      boolean imploded;
      boolean finalBlastResolved;
      boolean sustainStarted;
      int sustainTicks;
      int sustainUpkeepTicks;
      int outwardBurstTicksRemaining;
      int recollapseTicks;
      final Map<UUID, Entity> recollapseTargets = new LinkedHashMap<>();
      double absorbed;
      int lastDisplayed = -1;

      BlackHoleState(UUID ownerId, ServerWorld world, Vec3d center, double terrainRadius, boolean terrainEnabled) {
         this.ownerId = ownerId;
         this.world = world;
         this.center = center;
         this.terrainOrigin = BlockPos.ofFloored(center);
         this.terrainRadiusBlocks = Math.max(0, (int)Math.ceil(terrainRadius));
         this.terrainRadiusSquared = Math.max(0.0, terrainRadius) * Math.max(0.0, terrainRadius);
         this.terrainShell = 0;
         this.terrainX = 0;
         this.terrainY = 0;
         this.terrainZ = 0;
         this.terrainFinished = !terrainEnabled;
      }

      BlockPos nextTerrainPosition() {
         while (!this.terrainFinished) {
            int x = this.terrainX;
            int y = this.terrainY;
            int z = this.terrainZ;
            this.advanceTerrainCursor();
            if (!((double)x * x + (double)y * y + (double)z * z > this.terrainRadiusSquared + 1.0E-7)) {
               return this.terrainOrigin.add(x, y, z).toImmutable();
            }
         }

         return null;
      }

      private void advanceTerrainCursor() {
         if (Math.abs(this.terrainX) != this.terrainShell && Math.abs(this.terrainY) != this.terrainShell) {
            if (this.terrainZ == -this.terrainShell) {
               this.terrainZ = this.terrainShell;
               return;
            }
         } else if (this.terrainZ < this.terrainShell) {
            this.terrainZ++;
            return;
         }

         this.terrainZ = -this.terrainShell;
         if (this.terrainY < this.terrainShell) {
            this.terrainY++;
         } else {
            this.terrainY = -this.terrainShell;
            if (this.terrainX < this.terrainShell) {
               this.terrainX++;
            } else {
               this.terrainShell++;
               if (this.terrainShell > this.terrainRadiusBlocks) {
                  this.terrainFinished = true;
               } else {
                  this.terrainX = -this.terrainShell;
                  this.terrainY = -this.terrainShell;
                  this.terrainZ = -this.terrainShell;
               }
            }
         }
      }
   }

   private static class FrozenEntity {
      final Entity entity;
      Vec3d position;
      Vec3d velocity;
      float yaw;
      float pitch;

      FrozenEntity(Entity entity) {
         this.entity = entity;
         this.position = entity.getEntityPos();
         Vec3d savedVelocity = TimeWarperPower.getFrozenVelocity(entity);
         if (savedVelocity == null) {
            savedVelocity = SeriousPunchPower.getFrozenVelocity(entity);
         }

         if (savedVelocity == null) {
            savedVelocity = IceEmperorPower.getFrozenVelocity(entity);
         }

         this.velocity = savedVelocity == null ? entity.getVelocity() : savedVelocity;
         this.yaw = entity.getYaw();
         this.pitch = entity.getPitch();
      }
   }

   private static class PlayerSnapshot {
      final double x;
      final double y;
      final double z;
      final float yaw;
      final float pitch;
      final float health;
      final RegistryKey<World> dimension;

      PlayerSnapshot(ServerPlayerEntity player) {
         this.x = player.getX();
         this.y = player.getY();
         this.z = player.getZ();
         this.yaw = player.getYaw();
         this.pitch = player.getPitch();
         this.health = player.getHealth();
         this.dimension = player.getEntityWorld().getRegistryKey();
      }
   }

   private static final class TimeClone {
      final ZombieEntity entity;
      final boolean elite;
      int ticks;

      TimeClone(ZombieEntity entity, boolean elite, int ticks) {
         this.entity = entity;
         this.elite = elite;
         this.ticks = ticks;
      }
   }
}
