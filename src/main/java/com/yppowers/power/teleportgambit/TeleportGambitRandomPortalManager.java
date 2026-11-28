package com.yppowers.power.teleportgambit;
import com.yppowers.system.EpicVfx;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import com.yppowers.progression.RitualManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;

public final class TeleportGambitRandomPortalManager {
   private static final Map<UUID, TeleportGambitRandomPortalManager.Portal> ACTIVE_PORTALS = new HashMap<>();
   private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
   private static final Map<UUID, TeleportGambitRandomPortalManager.ReturnGroup> RETURN_GROUPS = new HashMap<>();
   private static final double GROUP_VERTICAL_TOLERANCE = 6.0;

   private TeleportGambitRandomPortalManager() {
   }

   public static boolean hasActivePortal(UUID ownerId) {
      return ACTIVE_PORTALS.containsKey(ownerId);
   }

   public static int getCooldownTicks(UUID ownerId) {
      return Math.max(0, COOLDOWNS.getOrDefault(ownerId, 0));
   }

   public static boolean hasReturnGroup(UUID ownerId) {
      return getCooldownTicks(ownerId) > 0 && RETURN_GROUPS.containsKey(ownerId);
   }

   public static void open(ServerPlayerEntity caster) {
      if (caster != null && caster.isAlive() && caster.getEntityWorld().getServer() != null) {
         MinecraftServer server = caster.getEntityWorld().getServer();
         if (!GraceManager.blockAbility(caster)) {
            UUID ownerId = caster.getUuid();
            if (ACTIVE_PORTALS.containsKey(ownerId)) {
               caster.sendMessage(Text.literal("Your random portal circle is already charging."), true);
            } else if (hasReturnGroup(ownerId)) {
               returnGroup(server, ownerId, caster, false);
            } else {
               int cooldown = getCooldownTicks(ownerId);
               if (cooldown > 0) {
                  caster.sendMessage(Text.literal("Random Portal cooldown: " + (cooldown + 19) / 20 + "s"), true);
               } else if (PowerManager.get(server).getPowerType(ownerId) == PowerType.GAMBIT) {
                  if (caster.getEntityWorld().getRegistryKey().equals(TeleportGambitDomainManager.DOMAIN_KEY)
                     || caster.getEntityWorld().getRegistryKey().equals(TimeWarperDomainManager.TIME_DOMAIN_KEY)) {
                     caster.sendMessage(Text.literal("The Gambit Gate cannot be opened from inside a pocket Domain."), true);
                  } else {
                     ServerWorld config = caster.getEntityWorld();
                     if (config instanceof ServerWorld) {
                        YPConfig var12 = YPConfig.getInstance();
                        double radius = Math.max(4.0, var12.teleport_gambit_random_portal_radius);
                        int chargeTicks = Math.max(20, var12.teleport_gambit_random_portal_charge_ticks);
                        Vec3d center = new Vec3d(caster.getX(), caster.getY() + 0.06, caster.getZ());
                        TeleportGambitRandomPortalManager.Portal portal = new TeleportGambitRandomPortalManager.Portal(
                           ownerId, config, center, radius, chargeTicks
                        );
                        ACTIVE_PORTALS.put(ownerId, portal);
                        int configuredCooldown = Math.max(0, var12.teleport_gambit_random_portal_cooldown_ticks);
                        if (configuredCooldown > 0) {
                           COOLDOWNS.put(ownerId, configuredCooldown);
                        }

                        emitOpeningBurst(config, center, radius);
                        config.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 2.0F, 0.55F);
                        caster.sendMessage(
                           Text.literal("The Gambit Gate is opening! Stay inside the large circle for the full charge.").formatted(Formatting.DARK_PURPLE),
                           true
                        );
                     }
                  }
               }
            }
         }
      }
   }

   public static void serverTick(MinecraftServer server) {
      tickCooldowns(server);
      if (!ACTIVE_PORTALS.isEmpty()) {
         if (GraceManager.isActive(server)) {
            ACTIVE_PORTALS.clear();
            return;
         }
         for (TeleportGambitRandomPortalManager.Portal portal : new ArrayList<>(ACTIVE_PORTALS.values())) {
            portal.age++;
            portal.ticksRemaining--;
            if ((portal.age & 1) == 0) {
               emitPortalCircle(portal);
            }

            if (portal.ticksRemaining <= 0) {
               ACTIVE_PORTALS.remove(portal.ownerId);
               teleportCapturedPlayers(server, portal);
            }
         }
      }
   }

   private static void tickCooldowns(MinecraftServer server) {
      Iterator<Entry<UUID, Integer>> iterator = COOLDOWNS.entrySet().iterator();

      while (iterator.hasNext()) {
         Entry<UUID, Integer> entry = iterator.next();
         int next = entry.getValue() - 1;
         if (next <= 0) {
            UUID ownerId = entry.getKey();
            iterator.remove();
            TeleportGambitRandomPortalManager.ReturnGroup expired = RETURN_GROUPS.remove(ownerId);
            if (expired != null && server != null) {
               PowerManager manager = PowerManager.get(server);
               for (TeleportGambitRandomPortalManager.PlayerOrigin origin : expired.origins()) {
                  NbtCompound pending = manager.getTeleportGambitPendingReturn(origin.playerId());
                  if (pending != null && "GATE".equals(pending.getString("ReturnType", ""))) {
                     manager.clearTeleportGambitPendingReturn(origin.playerId());
                  }
               }
            }
         } else {
            entry.setValue(next);
         }
      }
   }

   private static void emitOpeningBurst(ServerWorld world, Vec3d center, double radius) {
      world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, center.x, center.y + 0.1, center.z, 120, radius * 0.35, 0.12, radius * 0.35, 0.12);
      world.spawnParticles(ParticleTypes.END_ROD, true, true, center.x, center.y + 0.12, center.z, 64, radius * 0.25, 0.08, radius * 0.25, 0.06);
      EpicVfx.shockwave(world, center.x, center.y, center.z, radius);
   }

   private static void emitPortalCircle(TeleportGambitRandomPortalManager.Portal portal) {
      ServerWorld world = portal.world;
      Vec3d center = portal.center;
      double rotation = portal.age * 0.035;
      int points = 32;
      double innerRadius = portal.radius * 0.72;

      for (int i = 0; i < points; i++) {
         double angle = rotation + (Math.PI * 2) * i / points;
         double outerX = center.x + Math.cos(angle) * portal.radius;
         double outerZ = center.z + Math.sin(angle) * portal.radius;
         double innerX = center.x + Math.cos(-angle - rotation * 0.55) * innerRadius;
         double innerZ = center.z + Math.sin(-angle - rotation * 0.55) * innerRadius;
         world.spawnParticles((i & 1) == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD, true, true, outerX, center.y, outerZ, 1, 0.015, 0.025, 0.015, 0.002);
         if ((i & 1) == 0) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, innerX, center.y + 0.03, innerZ, 1, 0.012, 0.02, 0.012, 0.001);
         }
      }

      world.spawnParticles(ParticleTypes.PORTAL, true, true, center.x, center.y + 0.25, center.z, 8, 1.4, 0.35, 1.4, 0.12);
      if (portal.ticksRemaining % 20 == 0) {
         world.playSound(
            null,
            BlockPos.ofFloored(center),
            SoundEvents.BLOCK_BEACON_AMBIENT,
            SoundCategory.PLAYERS,
            0.55F,
            0.6F + (float)(0.5 * (1.0 - portal.ticksRemaining / 100.0))
         );
      }
   }

   private static void teleportCapturedPlayers(MinecraftServer server, TeleportGambitRandomPortalManager.Portal portal) {
      double radiusSquared = portal.radius * portal.radius;
      List<ServerPlayerEntity> captured = new ArrayList<>();

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         if (player.getEntityWorld() == portal.world && player.isAlive() && !player.isSpectator() && !(Math.abs(player.getY() - portal.center.y) > 6.0)) {
            double dx = player.getX() - portal.center.x;
            double dz = player.getZ() - portal.center.z;
            if (dx * dx + dz * dz <= radiusSquared) {
               captured.add(player);
            }
         }
      }

      if (captured.isEmpty()) {
         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(portal.ownerId);
         if (owner != null) {
            owner.sendMessage(Text.literal("The Gambit Gate collapsed without catching anyone."), true);
         }
      } else {
         TeleportGambitRandomPortalManager.Destination destination = chooseRandomDestination(server);
         if (destination != null) {
            Set<BlockPos> reserved = new HashSet<>();
            List<TeleportGambitRandomPortalManager.PlayerOrigin> transferredOrigins = new ArrayList<>(captured.size());
            PowerManager manager = PowerManager.get(server);

            for (int index = 0; index < captured.size(); index++) {
               ServerPlayerEntity playerx = captured.get(index);
               TeleportGambitRandomPortalManager.PlayerOrigin origin = new TeleportGambitRandomPortalManager.PlayerOrigin(
                  playerx.getUuid(),
                  playerx.getEntityWorld().getRegistryKey(),
                  playerx.getX(),
                  playerx.getY(),
                  playerx.getZ(),
                  playerx.getYaw(),
                  playerx.getPitch()
               );
               BlockPos landing = findGroupLanding(destination.world(), destination.position(), index, reserved);
               if (landing == null) {
                  landing = destination.position();
               }

               portal.world.spawnParticles(ParticleTypes.PORTAL, true, true, playerx.getX(), playerx.getY() + 0.8, playerx.getZ(), 28, 0.45, 0.65, 0.45, 0.12);
               boolean wasInDanger = RitualManager.isInDanger(playerx);
               boolean teleported = playerx.teleport(
                  destination.world(), landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, Set.of(), playerx.getYaw(), playerx.getPitch(), false
               );
               if (!teleported) {
                  playerx.sendMessage(Text.literal("The Gambit Gate transfer failed; you remain at the original location."), true);
               } else {
                  manager.saveTeleportGambitPendingReturn(playerx.getUuid(), origin.toNbt());
                  transferredOrigins.add(origin);
                  RitualManager.recordDangerEscape(playerx, wasInDanger);
                  playerx.setVelocity(Vec3d.ZERO);
                  playerx.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 100, 0));
                  playerx.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 1));
                  destination.world()
                     .spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, landing.getX() + 0.5, landing.getY() + 0.8, landing.getZ() + 0.5, 36, 0.6, 0.8, 0.6, 0.12);
                  destination.world()
                     .spawnParticles(ParticleTypes.END_ROD, true, true, landing.getX() + 0.5, landing.getY() + 1.0, landing.getZ() + 0.5, 18, 0.45, 0.55, 0.45, 0.06);
                  destination.world().playSound(null, landing, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.75F + index % 5 * 0.1F);
                  playerx.sendMessage(
                     Text.literal(
                           "The Gambit Gate has cast you into "
                              + destination.world().getRegistryKey().getValue()
                              + ". Press V again during the cooldown to return."
                        )
                        .formatted(Formatting.LIGHT_PURPLE),
                     true
                  );
               }
            }

            if (getCooldownTicks(portal.ownerId) > 0 && !transferredOrigins.isEmpty()) {
               RETURN_GROUPS.put(portal.ownerId, new TeleportGambitRandomPortalManager.ReturnGroup(List.copyOf(transferredOrigins)));
            }
         } else {
            for (ServerPlayerEntity playerxx : captured) {
               playerxx.sendMessage(Text.literal("The Gambit Gate could not find a safe landing in any available dimension."), true);
            }
         }
      }
   }

   public static boolean returnGroup(MinecraftServer server, UUID ownerId, ServerPlayerEntity requester, boolean causedByDeath) {
      TeleportGambitRandomPortalManager.ReturnGroup group = RETURN_GROUPS.remove(ownerId);
      if (group == null) {
         return false;
      } else {
         int returned = 0;

         for (TeleportGambitRandomPortalManager.PlayerOrigin origin : group.origins()) {
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(origin.playerId());
            if (member == null || !member.isAlive()) {
               PowerManager.get(server).saveTeleportGambitPendingReturn(origin.playerId(), origin.toNbt());
            } else if (teleportToOrigin(server, member, origin)) {
               PowerManager.get(server).clearTeleportGambitPendingReturn(origin.playerId());
               returned++;
            } else {
               PowerManager.get(server).saveTeleportGambitPendingReturn(origin.playerId(), origin.toNbt());
            }
         }

         if (requester != null && requester.isAlive()) {
            String reason = causedByDeath ? "Your death triggered the Gate's return." : "You recalled the Gambit Gate group.";
            requester.sendMessage(
               Text.literal(reason + " Returned " + returned + " player(s) to their original locations.").formatted(Formatting.LIGHT_PURPLE), true
            );
         }

         return true;
      }
   }

   public static void restorePendingReturn(ServerPlayerEntity player) {
      if (player != null && player.isAlive() && player.getEntityWorld().getServer() != null) {
         MinecraftServer server = player.getEntityWorld().getServer();
         NbtCompound pending = PowerManager.get(server).getTeleportGambitPendingReturn(player.getUuid());
         TeleportGambitRandomPortalManager.PlayerOrigin origin = pending == null
            ? null
            : TeleportGambitRandomPortalManager.PlayerOrigin.fromNbt(player.getUuid(), pending);
         if (origin != null) {
            if (teleportToOrigin(server, player, origin)) {
               PowerManager.get(server).clearTeleportGambitPendingReturn(player.getUuid());
            }
         }
      }
   }

   private static boolean teleportToOrigin(MinecraftServer server, ServerPlayerEntity player, TeleportGambitRandomPortalManager.PlayerOrigin origin) {
      ServerWorld target = server.getWorld(origin.dimension());
      boolean missingDimension = target == null;
      if (missingDimension) {
         target = server.getOverworld();
      }

      ServerWorld var7 = player.getEntityWorld();
      ServerWorld current = var7 instanceof ServerWorld ? var7 : null;
      if (current != null) {
         current.spawnParticles(ParticleTypes.PORTAL, true, true, player.getX(), player.getY() + 0.8, player.getZ(), 24, 0.45, 0.65, 0.45, 0.12);
      }

      double x = origin.x();
      double y = origin.y();
      double z = origin.z();
      if (missingDimension) {
         BlockPos spawn = server.getOverworld().getSpawnPoint().getPos();
         x = spawn.getX() + 0.5;
         y = spawn.getY() + 1.0;
         z = spawn.getZ() + 0.5;
      }

      boolean transferred = player.teleport(target, x, y, z, Set.of(), origin.yaw(), origin.pitch(), false);
      if (!transferred && target != server.getOverworld()) {
         BlockPos spawn = server.getOverworld().getSpawnPoint().getPos();
         transferred = player.teleport(
            server.getOverworld(), spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, Set.of(), origin.yaw(), origin.pitch(), false
         );
         target = server.getOverworld();
      }

      if (!transferred) {
         return false;
      } else {
         player.setVelocity(Vec3d.ZERO);
         target.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, x, y + 0.8, z, 32, 0.55, 0.75, 0.55, 0.12);
         target.playSound(null, BlockPos.ofFloored(x, y, z), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.85F);
         player.sendMessage(Text.literal("The Gambit Gate returned you to your original location.").formatted(Formatting.LIGHT_PURPLE), true);
         return true;
      }
   }

   private static TeleportGambitRandomPortalManager.Destination chooseRandomDestination(MinecraftServer server) {
      List<ServerWorld> dimensions = new ArrayList<>();

      for (ServerWorld world : server.getWorlds()) {
         if (!world.getRegistryKey().equals(TeleportGambitDomainManager.DOMAIN_KEY)
            && !world.getRegistryKey().equals(TimeWarperDomainManager.TIME_DOMAIN_KEY)) {
            dimensions.add(world);
         }
      }

      if (dimensions.isEmpty()) {
         return null;
      } else {
         Collections.shuffle(dimensions, (Random)ThreadLocalRandom.current());
         int attempts = Math.max(4, YPConfig.getInstance().teleport_gambit_random_portal_search_attempts);

         for (ServerWorld candidateWorld : dimensions) {
            BlockPos position = findRandomLanding(candidateWorld, attempts);
            if (position != null) {
               return new TeleportGambitRandomPortalManager.Destination(candidateWorld, position);
            }
         }

         return null;
      }
   }

   private static BlockPos findRandomLanding(ServerWorld world, int attempts) {
      if (world.getRegistryKey().equals(World.END)) {
         return findRandomEndLanding(world, attempts);
      } else {
         ThreadLocalRandom random = ThreadLocalRandom.current();
         double west = world.getWorldBorder().getBoundWest();
         double east = world.getWorldBorder().getBoundEast();
         double north = world.getWorldBorder().getBoundNorth();
         double south = world.getWorldBorder().getBoundSouth();
         BlockPos spawn = world.getSpawnPoint().getPos();
         int maxRange = 10000;
         int minX = Math.max(safeLowerBound(west, east), spawn.getX() - maxRange);
         int maxX = Math.min(safeUpperBound(west, east), spawn.getX() + maxRange);
         int minZ = Math.max(safeLowerBound(north, south), spawn.getZ() - maxRange);
         int maxZ = Math.min(safeUpperBound(north, south), spawn.getZ() + maxRange);
         if (maxX >= minX && maxZ >= minZ) {
            for (int attempt = 0; attempt < attempts; attempt++) {
               int x = random.nextInt(minX, maxX + 1);
               int z = random.nextInt(minZ, maxZ + 1);
               if (world.getRegistryKey().equals(World.NETHER)) {
                  int minY = world.getBottomY() + 8;
                  int maxY = Math.min(world.getTopY(Type.MOTION_BLOCKING, x, z) - 8, Math.max(minY + 1, world.getSeaLevel() + 48));
                  if (maxY > minY) {
                     int startY = random.nextInt(minY, maxY + 1);

                     for (int y = startY; y >= minY; y--) {
                        BlockPos candidate = new BlockPos(x, y, z);
                        if (isSafeLanding(world, candidate)) {
                           return candidate;
                        }
                     }
                  }
               } else {
                  int topY = world.getTopY(Type.MOTION_BLOCKING, x, z);
                  int minY = world.getBottomY() + 1;

                  for (int offset = 0; offset <= 48 && topY - offset >= minY; offset++) {
                     BlockPos candidate = new BlockPos(x, topY - offset, z);
                     if (isSafeLanding(world, candidate)) {
                        return candidate;
                     }
                  }
               }
            }

            return null;
         } else {
            return null;
         }
      }
   }

   private static BlockPos findRandomEndLanding(ServerWorld world, int attempts) {
      ThreadLocalRandom random = ThreadLocalRandom.current();

      for (int attempt = 0; attempt < attempts; attempt++) {
         int x;
         int z;
         if (attempt % 4 != 3) {
            x = random.nextInt(-88, 89);
            z = random.nextInt(-88, 89);
         } else {
            double angle = random.nextDouble(0.0, Math.PI * 2);
            double distance = random.nextDouble(1200.0, 8000.0);
            x = (int)Math.round(Math.cos(angle) * distance);
            z = (int)Math.round(Math.sin(angle) * distance);
         }

         BlockPos column = new BlockPos(x, world.getBottomY(), z);
         if (world.getWorldBorder().contains(column)) {
            int topY = world.getTopY(Type.MOTION_BLOCKING, x, z);
            int minY = world.getBottomY() + 1;

            for (int offset = 0; offset <= 48 && topY - offset >= minY; offset++) {
               BlockPos candidate = new BlockPos(x, topY - offset, z);
               if (isSafeLanding(world, candidate)) {
                  return candidate;
               }
            }
         }
      }

      return null;
   }

   private static int safeLowerBound(double lower, double upper) {
      double margin = Math.min(16.0, Math.max(0.0, (upper - lower) * 0.1));
      return (int)Math.ceil(lower + margin);
   }

   private static int safeUpperBound(double lower, double upper) {
      double margin = Math.min(16.0, Math.max(0.0, (upper - lower) * 0.1));
      return (int)Math.floor(upper - margin);
   }

   private static BlockPos findGroupLanding(ServerWorld world, BlockPos base, int slot, Set<BlockPos> reserved) {
      for (int attempt = 0; attempt < 32; attempt++) {
         double radius = slot == 0 && attempt == 0 ? 0.0 : 1.5 + Math.floor((slot + attempt) / 8.0) * 1.5;
         double angle = slot * 2.399963229728653 + attempt * 1.618033988749895;
         int x = base.getX() + (int)Math.round(Math.cos(angle) * radius);
         int z = base.getZ() + (int)Math.round(Math.sin(angle) * radius);
         if (world.getWorldBorder().contains(new BlockPos(x, base.getY(), z))) {
            if (!world.getRegistryKey().equals(World.NETHER)) {
               int topY = world.getTopY(Type.MOTION_BLOCKING, x, z);

               for (int offset = 0; offset <= 24; offset++) {
                  BlockPos candidate = new BlockPos(x, topY - offset, z);
                  if (!reserved.contains(candidate) && isSafeLanding(world, candidate)) {
                     reserved.add(candidate);
                     return candidate;
                  }
               }
            } else {
               for (int offsetx = 0; offsetx <= 24; offsetx++) {
                  int[] ys = offsetx == 0 ? new int[]{base.getY()} : new int[]{base.getY() - offsetx, base.getY() + offsetx};

                  for (int y : ys) {
                     BlockPos candidate = new BlockPos(x, y, z);
                     if (!reserved.contains(candidate) && isSafeLanding(world, candidate)) {
                        reserved.add(candidate);
                        return candidate;
                     }
                  }
               }
            }
         }
      }

      return base;
   }

   private static boolean isSafeLanding(ServerWorld world, BlockPos feet) {
      BlockPos floorPos = feet.down();
      BlockPos headPos = feet.up();
      if (world.isInBuildLimit(floorPos) && world.isInBuildLimit(feet) && world.isInBuildLimit(headPos)) {
         BlockState floor = world.getBlockState(floorPos);
         if (floor.getCollisionShape(world, floorPos).isEmpty() || !world.getFluidState(floorPos).isEmpty()) {
            return false;
         } else if (!floor.isOf(Blocks.MAGMA_BLOCK)
            && !floor.isOf(Blocks.CAMPFIRE)
            && !floor.isOf(Blocks.SOUL_CAMPFIRE)
            && !floor.isOf(Blocks.CACTUS)
            && !floor.isOf(Blocks.FIRE)
            && !floor.isOf(Blocks.SOUL_FIRE)
            && !floor.isOf(Blocks.SWEET_BERRY_BUSH)
            && !floor.isOf(Blocks.WITHER_ROSE)
            && !floor.isOf(Blocks.POINTED_DRIPSTONE)) {
            BlockState feetState = world.getBlockState(feet);
            BlockState headState = world.getBlockState(headPos);
            return feetState.getCollisionShape(world, feet).isEmpty()
               && headState.getCollisionShape(world, headPos).isEmpty()
               && world.getFluidState(feet).isEmpty()
               && world.getFluidState(headPos).isEmpty();
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void cancel(UUID ownerId) {
      ACTIVE_PORTALS.remove(ownerId);
   }

   public static void shutdown(MinecraftServer server) {
      for (UUID ownerId : new ArrayList<>(RETURN_GROUPS.keySet())) {
         returnGroup(server, ownerId, null, false);
      }

      ACTIVE_PORTALS.clear();
      COOLDOWNS.clear();
      RETURN_GROUPS.clear();
   }

   private record Destination(ServerWorld world, BlockPos position) {
   }

   private record PlayerOrigin(UUID playerId, RegistryKey<World> dimension, double x, double y, double z, float yaw, float pitch) {
      NbtCompound toNbt() {
         NbtCompound nbt = new NbtCompound();
         nbt.putString("ReturnType", "GATE");
         nbt.putString("Dimension", this.dimension.getValue().toString());
         nbt.putDouble("X", this.x);
         nbt.putDouble("Y", this.y);
         nbt.putDouble("Z", this.z);
         nbt.putFloat("Yaw", this.yaw);
         nbt.putFloat("Pitch", this.pitch);
         return nbt;
      }

      static TeleportGambitRandomPortalManager.PlayerOrigin fromNbt(UUID playerId, NbtCompound nbt) {
         if (!nbt.getString("ReturnType", "").equals("GATE")) {
            return null;
         } else {
            Identifier id = Identifier.tryParse(nbt.getString("Dimension", ""));
            return id == null
               ? null
               : new TeleportGambitRandomPortalManager.PlayerOrigin(
                  playerId,
                  RegistryKey.of(RegistryKeys.WORLD, id),
                  nbt.getDouble("X", 0.0),
                  nbt.getDouble("Y", 80.0),
                  nbt.getDouble("Z", 0.0),
                  nbt.getFloat("Yaw", 0.0F),
                  nbt.getFloat("Pitch", 0.0F)
               );
         }
      }
   }

   private static final class Portal {
      final UUID ownerId;
      final ServerWorld world;
      final Vec3d center;
      final double radius;
      int ticksRemaining;
      int age;

      Portal(UUID ownerId, ServerWorld world, Vec3d center, double radius, int chargeTicks) {
         this.ownerId = ownerId;
         this.world = world;
         this.center = center;
         this.radius = radius;
         this.ticksRemaining = chargeTicks;
      }
   }

   private record ReturnGroup(List<TeleportGambitRandomPortalManager.PlayerOrigin> origins) {
   }
}
