package com.yppowers.power.teleportgambit;
import com.yppowers.system.EpicVfx;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.PowerManager;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DragonBreathParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.TintedParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TeleportGambitDomainManager {
   public static final RegistryKey<World> DOMAIN_KEY = RegistryKey.of(RegistryKeys.WORLD, Identifier.of("yppowers", "tg_domain"));
   private static final Logger LOGGER = LoggerFactory.getLogger("YP Powers / Teleport Gambit Domain");
   private static final int FLOOR_Y = 64;
   private static final int PLATFORM_RADIUS = 128;
   private static final int PLATFORM_SIZE = 257;
   private static final int PLATFORM_BLOCKS_PER_TICK = 1024;
   private static final double DOMAIN_BOUNDARY_MARGIN = 3.0;
   private static final Map<UUID, TeleportGambitDomainManager.Charge> CHARGES = new HashMap<>();
   private static final Map<UUID, TeleportGambitDomainManager.DomainSession> SESSIONS = new HashMap<>();
   private static final Map<UUID, UUID> SESSION_OWNER_BY_MEMBER = new HashMap<>();
   private static final List<TeleportGambitDomainManager.BoostSpec> BOOSTS = List.of(
      boost(EntityAttributes.MAX_HEALTH, "max_health"),
      boost(EntityAttributes.MOVEMENT_SPEED, "movement_speed"),
      boost(EntityAttributes.ATTACK_DAMAGE, "attack_damage"),
      boost(EntityAttributes.ATTACK_SPEED, "attack_speed"),
      boost(EntityAttributes.ATTACK_KNOCKBACK, "attack_knockback"),
      boost(EntityAttributes.ARMOR, "armor"),
      boost(EntityAttributes.ARMOR_TOUGHNESS, "armor_toughness"),
      boost(EntityAttributes.KNOCKBACK_RESISTANCE, "knockback_resistance"),
      boost(EntityAttributes.EXPLOSION_KNOCKBACK_RESISTANCE, "explosion_knockback_resistance"),
      boost(EntityAttributes.MAX_ABSORPTION, "max_absorption"),
      boost(EntityAttributes.BLOCK_INTERACTION_RANGE, "block_interaction_range"),
      boost(EntityAttributes.ENTITY_INTERACTION_RANGE, "entity_interaction_range"),
      boost(EntityAttributes.BLOCK_BREAK_SPEED, "block_break_speed"),
      boost(EntityAttributes.MINING_EFFICIENCY, "mining_efficiency"),
      boost(EntityAttributes.SUBMERGED_MINING_SPEED, "submerged_mining_speed"),
      boost(EntityAttributes.MOVEMENT_EFFICIENCY, "movement_efficiency"),
      boost(EntityAttributes.SNEAKING_SPEED, "sneaking_speed"),
      boost(EntityAttributes.STEP_HEIGHT, "step_height"),
      boost(EntityAttributes.OXYGEN_BONUS, "oxygen_bonus"),
      boost(EntityAttributes.SWEEPING_DAMAGE_RATIO, "sweeping_damage_ratio"),
      boost(EntityAttributes.WATER_MOVEMENT_EFFICIENCY, "water_movement_efficiency"),
      boost(EntityAttributes.SAFE_FALL_DISTANCE, "safe_fall_distance")
   );
   private static final int BEACON_BEAM_TICKS = 8;
   private static final int PULL_ANIMATION_TICKS = 4;
   private static final TintedParticleEffect DOMAIN_DARK_PARTICLE = TintedParticleEffect.create(ParticleTypes.ENTITY_EFFECT, 460555);

   private TeleportGambitDomainManager() {
   }

   private static TeleportGambitDomainManager.BoostSpec boost(RegistryEntry<EntityAttribute> attribute, String suffix) {
      return new TeleportGambitDomainManager.BoostSpec(attribute, Identifier.of("yppowers", "tg_domain_5x_" + suffix));
   }

   public static void beginCharge(ServerPlayerEntity caster) {
      MinecraftServer server = caster.getEntityWorld().getServer();
      if (server != null) {
         if (!GraceManager.blockAbility(caster)) {
            UUID ownerId = caster.getUuid();
            if (caster.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)
               || caster.getEntityWorld().getRegistryKey().equals(TimeWarperDomainManager.TIME_DOMAIN_KEY)) {
               caster.sendMessage(Text.literal("You cannot open another Domain from inside a Domain."), true);
            } else if (!CHARGES.containsKey(ownerId) && !SESSIONS.containsKey(ownerId)) {
               UUID currentOwner = SESSION_OWNER_BY_MEMBER.get(ownerId);
               if (currentOwner != null && SESSIONS.containsKey(currentOwner)) {
                  caster.sendMessage(Text.literal("You are trapped inside another Teleport Gambit's Domain."), true);
               } else {
                  PowerManager manager = PowerManager.get(server);
                  int cooldown = manager.getTeleportGambitDomainCooldownTicks(ownerId);
                  if (cooldown > 0) {
                     caster.sendMessage(Text.literal("Domain cooldown: " + (cooldown + 1199) / 1200 + " minute(s) remaining."), true);
                  } else {
                     ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);
                     if (domainWorld == null) {
                        caster.sendMessage(
                           Text.literal("Teleport Gambit's Domain dimension is not loaded. Restart the world/server with this mod version and try again."),
                           false
                        );
                     } else {
                        ServerWorld sourceWorld = caster.getEntityWorld();
                        BlockPos framePos = caster.getBlockPos().up(3).toImmutable();
                        BlockState oldState = sourceWorld.getBlockState(framePos);
                        if (!oldState.hasBlockEntity()
                           && (oldState.isAir() || oldState.isReplaceable())
                           && !(oldState.getHardness(sourceWorld, framePos) < 0.0F)) {
                           int duration = Math.max(40, YPConfig.getInstance().teleport_gambit_domain_charge_ticks);
                           double radius = Math.max(1.0, YPConfig.getInstance().teleport_gambit_domain_pull_radius);
                           BlockPos center = domainCenter(ownerId);
                           TeleportGambitDomainManager.PlatformBuilder builder = domainWorld.getBlockState(center.down()).isOf(Blocks.BEDROCK)
                              ? null
                              : new TeleportGambitDomainManager.PlatformBuilder(center);
                           TeleportGambitDomainManager.Charge charge = new TeleportGambitDomainManager.Charge(
                              ownerId, sourceWorld.getRegistryKey(), framePos, oldState, duration, radius, center, builder
                           );
                           CHARGES.put(ownerId, charge);
                           sourceWorld.setBlockState(framePos, Blocks.END_PORTAL_FRAME.getDefaultState(), 3);
                           sourceWorld.spawnParticles(
                              ParticleTypes.PORTAL, true, true, framePos.getX() + 0.5, framePos.getY() + 0.8, framePos.getZ() + 0.5, 45, 0.55, 0.7, 0.55, 0.12
                           );
                           sourceWorld.spawnParticles(
                              ParticleTypes.REVERSE_PORTAL, true, true, framePos.getX() + 0.5, framePos.getY() + 0.5, framePos.getZ() + 0.5, 30, 0.35, 0.4, 0.35, 0.06
                           );
                           sourceWorld.playSound(null, framePos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.5F, 0.65F);
                           manager.setTeleportGambitDomainCooldownTicks(ownerId, Math.max(0, YPConfig.getInstance().teleport_gambit_domain_cooldown_ticks));
                           charge.affectedPlayers.add(ownerId);
                           caster.sendMessage(Text.literal("The Domain is forming. Stay nearby as the gate charges!"), true);
                        } else {
                           caster.sendMessage(Text.literal("There is no safe space for the Domain's End Portal Frame above you."), true);
                        }
                     }
                  }
               }
            } else {
               caster.sendMessage(Text.literal("Your Domain is already charging or active."), true);
            }
         }
      }
   }

   public static void serverTick(MinecraftServer server) {
      tickCharges(server);
      tickSessions(server);
   }

   private static void tickCharges(MinecraftServer server) {
      if (!CHARGES.isEmpty()) {
         for (TeleportGambitDomainManager.Charge charge : new ArrayList<>(CHARGES.values())) {
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(charge.ownerId);
            ServerWorld sourceWorld = server.getWorld(charge.sourceDimension);
            if (caster != null && caster.isAlive() && !GraceManager.isActive(server) && sourceWorld != null && caster.getEntityWorld().getRegistryKey().equals(charge.sourceDimension)) {
               ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);
               if (domainWorld == null) {
                  caster.sendMessage(Text.literal("The Domain dimension became unavailable; the charge was cancelled."), false);
                  cancelCharge(charge.ownerId, server, true);
               } else {
                  if (charge.phase != TeleportGambitDomainManager.ChargePhase.BEACON_BEAM
                     && !charge.gateRemoved
                     && !sourceWorld.getBlockState(charge.framePos).isOf(Blocks.END_PORTAL_FRAME)) {
                     sourceWorld.setBlockState(charge.framePos, Blocks.END_PORTAL_FRAME.getDefaultState(), 3);
                  }

                  if (charge.phase == TeleportGambitDomainManager.ChargePhase.CHARGING) {
                     charge.elapsedTicks++;
                     double progress = Math.min(1.0, (double)charge.elapsedTicks / charge.durationTicks);
                     emitChargeVfx(sourceWorld, charge.framePos, progress, charge.elapsedTicks);
                     if (charge.platformBuilder != null && !charge.platformBuilder.isComplete()) {
                        charge.platformBuilder.advance(domainWorld, 1024);
                     }

                     if (charge.elapsedTicks >= charge.durationTicks) {
                        if (charge.platformBuilder != null && !charge.platformBuilder.isComplete()) {
                           charge.elapsedTicks = charge.durationTicks - 1;
                        } else {
                           beginBeaconBeam(sourceWorld, charge);
                        }
                     }
                  } else if (charge.phase == TeleportGambitDomainManager.ChargePhase.BEACON_BEAM) {
                     charge.phaseTicks++;
                     if ((charge.phaseTicks & 1) == 0) {
                        emitBeaconBeam(sourceWorld, charge.framePos, charge.phaseTicks);
                     }

                     if (charge.phaseTicks >= 8) {
                        sourceWorld.setBlockState(charge.framePos, Blocks.END_PORTAL_FRAME.getDefaultState(), 3);
                        beginPullAnimation(server, sourceWorld, charge);
                     }
                  } else if (charge.phase == TeleportGambitDomainManager.ChargePhase.PULLING) {
                     charge.phaseTicks++;
                     animatePull(server, sourceWorld, charge);
                     if (charge.phaseTicks >= 4) {
                        completeCharge(server, charge, sourceWorld, domainWorld);
                     }
                  }
               }
            } else {
               cancelCharge(charge.ownerId, server, true);
            }
         }
      }
   }

   private static void beginBeaconBeam(ServerWorld sourceWorld, TeleportGambitDomainManager.Charge charge) {
      charge.phase = TeleportGambitDomainManager.ChargePhase.BEACON_BEAM;
      charge.phaseTicks = 0;
      sourceWorld.playSound(null, charge.framePos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.6F, 0.72F);
      sourceWorld.playSound(null, charge.framePos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.4F, 0.6F);
      double x = charge.framePos.getX() + 0.5;
      double y = charge.framePos.getY() + 0.5;
      double z = charge.framePos.getZ() + 0.5;
      sourceWorld.spawnParticles(ParticleTypes.END_ROD, true, true, x, y, z, 64, 1.15, 0.75, 1.15, 0.16);
      sourceWorld.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x, y, z, 52, 1.0, 0.6, 1.0, 0.12);
      sourceWorld.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, x, y, z, 48, 1.1, 0.7, 1.1, 0.08);
      sourceWorld.spawnParticles(DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), true, true, x, y, z, 28, 0.8, 0.5, 0.8, 0.04);
   }

   private static void emitBeaconBeam(ServerWorld world, BlockPos pos, int phaseTick) {
      double x = pos.getX() + 0.5;
      double z = pos.getZ() + 0.5;

      for (double y = pos.getY() + 0.5; y < pos.getY() + 18.0; y += 0.5) {
         world.spawnParticles(ParticleTypes.END_ROD, true, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
      }

      double rotation = phaseTick * 0.42;

      for (int i = 0; i < 16; i++) {
         double angle = rotation + (Math.PI * 2) * i / 16.0;
         double ringX = x + Math.cos(angle) * 1.35;
         double ringZ = z + Math.sin(angle) * 1.35;
         double ringY = pos.getY() + 0.45 + Math.sin(angle * 2.0) * 0.18;
         world.spawnParticles(ParticleTypes.END_ROD, true, true, ringX, ringY, ringZ, 1, 0.0, 0.0, 0.0, 0.0);
         if ((i & 1) == 0) {
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, ringX, ringY, ringZ, 1, 0.0, 0.0, 0.0, 0.0);
         }
      }

      world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x, pos.getY() + 0.45, z, 24, 0.2, 0.24, 0.2, 0.03);
      world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, x, pos.getY() + 0.5, z, 22, 0.6, 0.5, 0.6, 0.06);
   }

   private static void emitPullGateCollapse(ServerWorld world, BlockPos pos, double progress) {
      double x = pos.getX() + 0.5;
      double y = pos.getY() + 0.5;
      double z = pos.getZ() + 0.5;
      double radius = 0.18 + (1.0 - progress) * 2.0;
      int darkCount = 58 + (int)((1.0 - progress) * 26.0);
      world.spawnParticles(DOMAIN_DARK_PARTICLE, true, true, x, y, z, darkCount, radius * 0.34, radius * 0.34, radius * 0.34, 0.008);
      world.spawnParticles(ParticleTypes.LARGE_SMOKE, true, true, x, y, z, 42, radius * 0.42, radius * 0.42, radius * 0.42, 0.006);
      world.spawnParticles(ParticleTypes.SMOKE, true, true, x, y, z, 24, radius * 0.38, radius * 0.38, radius * 0.38, 0.004);
      double rotation = progress * Math.PI * 1.8;

      for (int i = 0; i < 10; i++) {
         double fraction = (i + 1.0) / 10.0;
         double angle = rotation + fraction * Math.PI * 4.0;
         double orbit = radius * (0.25 + 0.65 * fraction);
         double py = y + Math.sin(angle * 1.6) * radius * 0.32;
         world.spawnParticles(DOMAIN_DARK_PARTICLE, true, true, x + Math.cos(angle) * orbit, py, z + Math.sin(angle) * orbit, 1, 0.015, 0.015, 0.015, 0.002);
      }
   }

   private static void beginPullAnimation(MinecraftServer server, ServerWorld sourceWorld, TeleportGambitDomainManager.Charge charge) {
      charge.phase = TeleportGambitDomainManager.ChargePhase.PULLING;
      charge.phaseTicks = 0;
      charge.affectedPlayers.clear();
      charge.pullStartPositions.clear();
      Vec3d gate = charge.framePos.toCenterPos();
      double radiusSq = charge.radius * charge.radius;

      for (ServerPlayerEntity target : server.getPlayerManager().getPlayerList()) {
         if (target.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())
            && target.isAlive()
            && !target.isSpectator()
            && (!(target.getEntityPos().squaredDistanceTo(gate) > radiusSq) || target.getUuid().equals(charge.ownerId))) {
            charge.affectedPlayers.add(target.getUuid());
            charge.pullStartPositions.put(target.getUuid(), target.getEntityPos());
            target.setVelocity(Vec3d.ZERO);
            ((EntityVelocityAccessor)target).invokeYppowersScheduleVelocityUpdate();
         }
      }

      sourceWorld.playSound(null, charge.framePos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.7F, 0.55F);
   }

   private static void animatePull(MinecraftServer server, ServerWorld sourceWorld, TeleportGambitDomainManager.Charge charge) {
      if (!charge.gateRemoved) {
         BlockState current = sourceWorld.getBlockState(charge.framePos);
         if (current.isOf(Blocks.END_PORTAL_FRAME) || current.isOf(Blocks.BEACON)) {
            sourceWorld.setBlockState(charge.framePos, Blocks.AIR.getDefaultState(), 3);
         }

         charge.gateRemoved = true;
      }

      double linear = Math.min(1.0, charge.phaseTicks / 4.0);
      double eased = linear * linear * (3.0 - 2.0 * linear);
      emitPullGateCollapse(sourceWorld, charge.framePos, eased);
      double goalX = charge.framePos.getX() + 0.5;
      double goalY = charge.framePos.getY() - 2.0;
      double goalZ = charge.framePos.getZ() + 0.5;

      for (UUID id : charge.affectedPlayers) {
         ServerPlayerEntity target = server.getPlayerManager().getPlayer(id);
         Vec3d startPos = charge.pullStartPositions.get(id);
         if (target != null && startPos != null && target.isAlive() && target.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) {
            double x = startPos.x + (goalX - startPos.x) * eased;
            double y = startPos.y + (goalY - startPos.y) * eased;
            double z = startPos.z + (goalZ - startPos.z) * eased;
            target.setVelocity(Vec3d.ZERO);
            target.requestTeleport(x, y, z);
            if (charge.phaseTicks % 3 == 0) {
               sourceWorld.spawnParticles(ParticleTypes.PORTAL, true, true, x, y + 0.8, z, 5, 0.2, 0.35, 0.2, 0.03);
            }
         }
      }
   }

   private static void emitChargeVfx(ServerWorld world, BlockPos pos, double progress, int ticks) {
      double spread = 0.45 + 2.1 * progress;
      int portalCount = 3 + (int)(progress * 15.0);
      int sparkCount = 1 + (int)(progress * 7.0);
      double x = pos.getX() + 0.5;
      double y = pos.getY() + 0.5;
      double z = pos.getZ() + 0.5;
      world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, x, y, z, portalCount, spread * 0.42, spread * 0.55, spread * 0.42, 0.08 + progress * 0.12);
      world.spawnParticles(ParticleTypes.PORTAL, true, true, x, y, z, portalCount, spread, spread * 0.85, spread, 0.06 + progress * 0.18);
      world.spawnParticles(ParticleTypes.END_ROD, true, true, x, y, z, sparkCount, spread * 0.6, spread, spread * 0.6, 0.02 + progress * 0.12);
      if (ticks % 3 == 0) {
         world.spawnParticles(ParticleTypes.ENCHANT, true, true, x, y, z, 4 + (int)(progress * 12.0), spread, spread, spread, 0.4 + progress * 0.8);
      }

      if (ticks % 4 == 0) {
         world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, x, y, z, sparkCount, spread * 0.8, spread, spread * 0.8, 0.08 + progress * 0.15);
      }

      if (ticks % 5 == 0) {
         world.spawnParticles(
            DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), true, true,
            x,
            y,
            z,
            2 + (int)(progress * 9.0),
            spread * 0.5,
            spread * 0.7,
            spread * 0.5,
            0.02 + progress * 0.08
         );
      }

      if (ticks % 20 == 0) {
         world.playSound(null, pos, SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.PLAYERS, (float)(0.25 + progress * 0.7), (float)(0.45 + progress * 0.65));
      }
   }

   private static void completeCharge(MinecraftServer server, TeleportGambitDomainManager.Charge charge, ServerWorld sourceWorld, ServerWorld domainWorld) {
      CHARGES.remove(charge.ownerId);
      restoreFrame(server, charge);
      ServerPlayerEntity caster = server.getPlayerManager().getPlayer(charge.ownerId);
      if (caster != null && caster.isAlive() && caster.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) {
         List<ServerPlayerEntity> arrivals = new ArrayList<>();
         arrivals.add(caster);

         for (UUID id : charge.affectedPlayers) {
            if (!id.equals(charge.ownerId)) {
               ServerPlayerEntity target = server.getPlayerManager().getPlayer(id);
               if (target != null && target.isAlive() && target.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) {
                  arrivals.add(target);
               }
            }
         }

         double preferredRadius = Math.min(24.0, 5.0 + Math.sqrt(Math.max(0, arrivals.size() - 1)) * 2.0);
         Map<UUID, TeleportGambitDomainManager.PlayerOrigin> origins = new HashMap<>();

         for (ServerPlayerEntity player : arrivals) {
            origins.put(player.getUuid(), TeleportGambitDomainManager.PlayerOrigin.capture(player));
         }

         BlockPos ownerSafe = findSafeSpawn(domainWorld, charge.domainCenter, charge.domainCenter.getX() + 0.5, charge.domainCenter.getZ() + 0.5);
         if (!caster.teleport(domainWorld, ownerSafe.getX() + 0.5, ownerSafe.getY(), ownerSafe.getZ() + 0.5, Set.of(), caster.getYaw(), caster.getPitch(), true)
            )
          {
            stopPullingAffectedPlayers(server, charge, sourceWorld);
            PowerManager.get(server).setTeleportGambitDomainCooldownTicks(charge.ownerId, 0);
            caster.sendMessage(
               Text.literal("The Domain transfer failed. You were not trapped and the cooldown was refunded; check the server log for the transfer error."),
               true
            );
         } else {
            TeleportGambitDomainManager.DomainSession session = new TeleportGambitDomainManager.DomainSession(charge.ownerId, charge.domainCenter);
            session.origins.put(charge.ownerId, origins.get(charge.ownerId));
            SESSIONS.put(charge.ownerId, session);
            SESSION_OWNER_BY_MEMBER.put(charge.ownerId, charge.ownerId);
            ServerPlayerEntity liveCaster = playerById(server, charge.ownerId);
            if (liveCaster != null) {
               ensureDomainBoost(liveCaster);
               liveCaster.setHealth(liveCaster.getMaxHealth());
            }

            for (int i = 1; i < arrivals.size(); i++) {
               ServerPlayerEntity original = arrivals.get(i);
               UUID memberId = original.getUuid();
               ServerPlayerEntity member = playerById(server, memberId);
               if (member != null && member.isAlive() && member.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) {
                  double angle = i * 2.399963229728653;
                  double preferredX = charge.domainCenter.getX() + 0.5 + Math.cos(angle) * preferredRadius;
                  double preferredZ = charge.domainCenter.getZ() + 0.5 + Math.sin(angle) * preferredRadius;
                  BlockPos safe = findSafeSpawn(domainWorld, charge.domainCenter, preferredX, preferredZ);
                  if (!member.teleport(domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, Set.of(), member.getYaw(), member.getPitch(), true)) {
                     ServerPlayerEntity liveMember = playerById(server, memberId);
                     if (liveMember != null) {
                        liveMember.setVelocity(Vec3d.ZERO);
                        ((EntityVelocityAccessor)liveMember).invokeYppowersScheduleVelocityUpdate();
                        liveMember.sendMessage(Text.literal("The Domain transfer failed, so you were left outside instead of being trapped."), true);
                     }

                     LOGGER.error(
                        "Could not transfer player {} into Teleport Gambit's Domain ({} -> {}).",
                        new Object[]{memberId, sourceWorld.getRegistryKey().getValue(), DOMAIN_KEY.getValue()}
                     );
                  } else {
                     session.origins.put(memberId, origins.get(memberId));
                     SESSION_OWNER_BY_MEMBER.put(memberId, charge.ownerId);
                     ServerPlayerEntity liveMember = playerById(server, memberId);
                     if (liveMember != null) {
                        liveMember.setVelocity(Vec3d.ZERO);
                        ((EntityVelocityAccessor)liveMember).invokeYppowersScheduleVelocityUpdate();
                        domainWorld.spawnParticles(ParticleTypes.PORTAL, true, true, liveMember.getX(), liveMember.getY() + 1.0, liveMember.getZ(), 32, 0.7, 1.0, 0.7, 0.16);
                        domainWorld.spawnParticles(
                           ParticleTypes.END_ROD, true, true, liveMember.getX(), liveMember.getY() + 1.0, liveMember.getZ(), 14, 0.8, 1.0, 0.8, 0.05
                        );
                        domainWorld.playSound(null, safe, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.2F, 0.65F);
                        liveMember.sendMessage(
                           Text.literal("Teleport Gambit has dragged you into a pocket Domain. You are trapped until its owner dies or leaves."), false
                        );
                     }
                  }
               }
            }

            liveCaster = playerById(server, charge.ownerId);
            if (liveCaster != null && liveCaster.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
               liveCaster.sendMessage(Text.literal("Your Domain is open. Your core stats are multiplied by 5; jump strength is unchanged."), false);
            }

            domainWorld.spawnParticles(
               ParticleTypes.EXPLOSION_EMITTER, true, true, charge.domainCenter.getX() + 0.5, 65.0, charge.domainCenter.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0
            );
            EpicVfx.shockwave(domainWorld, charge.domainCenter.getX() + 0.5, 65.0, charge.domainCenter.getZ() + 0.5, 18.0);
            EpicVfx.column(domainWorld, ParticleTypes.END_ROD, charge.domainCenter.getX() + 0.5, 64.0, charge.domainCenter.getZ() + 0.5, 20.0, 24);
            EpicVfx.flash(domainWorld, charge.domainCenter.getX() + 0.5, 65.5, charge.domainCenter.getZ() + 0.5);
         }
      } else {
         stopPullingAffectedPlayers(server, charge, sourceWorld);
         if (caster != null) {
            caster.sendMessage(Text.literal("The Domain could not open because its owner was no longer at the gate."), true);
         }
      }
   }

   private static ServerPlayerEntity playerById(MinecraftServer server, UUID id) {
      return server.getPlayerManager().getPlayer(id);
   }

   private static boolean teleportPlayer(
      MinecraftServer server, ServerPlayerEntity player, ServerWorld destination, double x, double y, double z, float yaw, float pitch
   ) {
      return server != null && player != null && destination != null && player.teleport(destination, x, y, z, Set.of(), yaw, pitch, true);
   }

   private static void stopPullingAffectedPlayers(MinecraftServer server, TeleportGambitDomainManager.Charge charge, ServerWorld sourceWorld) {
      for (UUID id : charge.affectedPlayers) {
         ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
         if (player != null && player.getEntityWorld().getRegistryKey().equals(sourceWorld.getRegistryKey())) {
            player.setVelocity(Vec3d.ZERO);
            ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
         }
      }
   }

   private static BlockPos findSafeSpawn(ServerWorld world, BlockPos center, double preferredX, double preferredZ) {
      int baseX = (int)Math.floor(preferredX);
      int baseZ = (int)Math.floor(preferredZ);
      BlockPos exact = safeAt(world, baseX, 65, baseZ);
      if (exact != null) {
         return exact;
      } else {
         for (int radius = 1; radius <= 48; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
               BlockPos north = safeAt(world, center.getX() + dx, 65, center.getZ() - radius);
               if (north != null) {
                  return north;
               }

               BlockPos south = safeAt(world, center.getX() + dx, 65, center.getZ() + radius);
               if (south != null) {
                  return south;
               }
            }

            for (int dz = -radius + 1; dz < radius; dz++) {
               BlockPos west = safeAt(world, center.getX() - radius, 65, center.getZ() + dz);
               if (west != null) {
                  return west;
               }

               BlockPos east = safeAt(world, center.getX() + radius, 65, center.getZ() + dz);
               if (east != null) {
                  return east;
               }
            }
         }

         return center.up();
      }
   }

   private static BlockPos safeAt(ServerWorld world, int x, int y, int z) {
      BlockPos feet = new BlockPos(x, y, z);
      if (!world.getBlockState(feet.down()).isSolidBlock(world, feet.down())) {
         return null;
      } else if (!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir()) {
         return null;
      } else {
         return world.getFluidState(feet).isEmpty() && world.getFluidState(feet.up()).isEmpty() ? feet : null;
      }
   }

   private static void tickSessions(MinecraftServer server) {
      if (!SESSIONS.isEmpty()) {
         ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);

         for (TeleportGambitDomainManager.DomainSession session : new ArrayList<>(SESSIONS.values())) {
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(session.ownerId);
            if (owner == null) {
               endSession(server, session.ownerId, TeleportGambitDomainManager.EndReason.OWNER_DISCONNECTED);
            } else if (!owner.isAlive()) {
               endSession(server, session.ownerId, TeleportGambitDomainManager.EndReason.OWNER_DIED);
            } else if (domainWorld != null && owner.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
               if (!insideDomainRegion(owner, session.center)) {
                  endSession(server, session.ownerId, TeleportGambitDomainManager.EndReason.OWNER_LEFT_REGION);
               } else {
                  ensureDomainBoost(owner);
                  if (server.getTicks() % 200 == 0) {
                     domainWorld.setTimeOfDay(6000L);
                  }

                  for (UUID memberId : new ArrayList<>(session.origins.keySet())) {
                     if (!memberId.equals(session.ownerId)) {
                        ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
                        if (member != null
                           && member.isAlive()
                           && (!member.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY) || !insideDomainRegion(member, session.center))) {
                           BlockPos safe = findSafeSpawn(domainWorld, session.center, session.center.getX() + 0.5, session.center.getZ() + 0.5);
                           if (!teleportPlayer(
                              server, member, domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, member.getYaw(), member.getPitch()
                           )) {
                              LOGGER.error("Failed to pull player {} back into active Domain owned by {}.", memberId, session.ownerId);
                           } else {
                              ServerPlayerEntity liveMember = playerById(server, memberId);
                              if (liveMember != null) {
                                 liveMember.sendMessage(Text.literal("The Domain is sealed. You cannot leave while its owner remains inside."), true);
                              }
                           }
                        }
                     }
                  }
               }
            } else {
               endSession(server, session.ownerId, TeleportGambitDomainManager.EndReason.OWNER_LEFT_DIMENSION);
            }
         }
      }
   }

   private static boolean insideDomainRegion(ServerPlayerEntity player, BlockPos center) {
      if (!player.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
         return false;
      } else {
         double dx = Math.abs(player.getX() - (center.getX() + 0.5));
         double dz = Math.abs(player.getZ() - (center.getZ() + 0.5));
         return dx <= 125.0 && dz <= 125.0 && player.getY() >= 65.0 && player.getY() < 250.0;
      }
   }

   public static boolean isProtectedChargeFrame(ServerWorld world, BlockPos pos) {
      for (TeleportGambitDomainManager.Charge charge : CHARGES.values()) {
         if (charge.framePos.equals(pos) && charge.sourceDimension.equals(world.getRegistryKey())) {
            return true;
         }
      }

      return false;
   }

   public static void onPlayerDeath(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         UUID id = player.getUuid();
         cancelCharge(id, server, true);
         UUID ownerId = SESSION_OWNER_BY_MEMBER.get(id);
         if (ownerId != null) {
            if (ownerId.equals(id)) {
               endSession(server, ownerId, TeleportGambitDomainManager.EndReason.OWNER_DIED);
            } else {
               SESSION_OWNER_BY_MEMBER.remove(id);
               TeleportGambitDomainManager.DomainSession session = SESSIONS.get(ownerId);
               if (session != null) {
                  session.origins.remove(id);
               }
               PowerManager.get(server).clearTeleportGambitPendingReturn(id);
            }
         }
      }
   }

   public static void onPlayerDisconnect(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         cancelCharge(player.getUuid(), server, true);
         UUID ownerId = SESSION_OWNER_BY_MEMBER.get(player.getUuid());
         if (ownerId != null && ownerId.equals(player.getUuid())) {
            endSession(server, ownerId, TeleportGambitDomainManager.EndReason.OWNER_DISCONNECTED);
         }

         removeDomainBoost(player);
      }
   }

   public static void onPowerRemoved(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         cancelCharge(player.getUuid(), server, true);
         UUID ownerId = SESSION_OWNER_BY_MEMBER.get(player.getUuid());
         if (ownerId != null && ownerId.equals(player.getUuid())) {
            endSession(server, ownerId, TeleportGambitDomainManager.EndReason.OWNER_POWER_REMOVED);
         }
      }
   }

   public static void onPlayerJoin(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server != null) {
         PowerManager manager = PowerManager.get(server);
         UUID id = player.getUuid();
         NbtCompound pending = manager.getTeleportGambitPendingReturn(id);
         if (pending != null && pending.getString("ReturnType", "DOMAIN").equals("DOMAIN") && restorePendingReturn(player, server, pending)) {
            manager.clearTeleportGambitPendingReturn(id);
         } else {
            UUID ownerId = SESSION_OWNER_BY_MEMBER.get(id);
            TeleportGambitDomainManager.DomainSession active = ownerId == null ? null : SESSIONS.get(ownerId);
            if (active != null && ownerId != null) {
               ServerWorld domainWorld = server.getWorld(DOMAIN_KEY);
               if (domainWorld != null) {
                  BlockPos safe = findSafeSpawn(domainWorld, active.center, active.center.getX() + 0.5, active.center.getZ() + 0.5);
                  if (teleportPlayer(server, player, domainWorld, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYaw(), player.getPitch())) {
                     ServerPlayerEntity livePlayer = playerById(server, id);
                     if (id.equals(active.ownerId) && livePlayer != null) {
                        ensureDomainBoost(livePlayer);
                     }

                     return;
                  }

                  LOGGER.error("Could not restore returning Domain member {} to the active Domain.", id);
               }
            }

            if (player.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
               BlockPos overworldSpawn = server.getOverworld().getSpawnPoint().getPos();
               teleportPlayer(
                  server,
                  player,
                  server.getOverworld(),
                  overworldSpawn.getX() + 0.5,
                  overworldSpawn.getY() + 1.0,
                  overworldSpawn.getZ() + 0.5,
                  player.getYaw(),
                  player.getPitch()
               );
            }

            removeDomainBoost(player);
         }
      }
   }

   public static void onPlayerRespawn(ServerPlayerEntity player) {
      onPlayerJoin(player);
   }

   public static void shutdown(MinecraftServer server) {
      for (UUID ownerId : new ArrayList<>(CHARGES.keySet())) {
         cancelCharge(ownerId, server, true);
      }

      for (UUID ownerId : new ArrayList<>(SESSIONS.keySet())) {
         endSession(server, ownerId, TeleportGambitDomainManager.EndReason.SERVER_SHUTDOWN);
      }
   }

   public static void clearRuntimeState() {
      CHARGES.clear();
      SESSIONS.clear();
      SESSION_OWNER_BY_MEMBER.clear();
   }

   private static void endSession(MinecraftServer server, UUID ownerId, TeleportGambitDomainManager.EndReason reason) {
      TeleportGambitDomainManager.DomainSession session = SESSIONS.remove(ownerId);
      if (session != null) {
         PowerManager manager = PowerManager.get(server);

         for (Entry<UUID, TeleportGambitDomainManager.PlayerOrigin> entry : session.origins.entrySet()) {
            UUID memberId = entry.getKey();
            TeleportGambitDomainManager.PlayerOrigin origin = entry.getValue();
            SESSION_OWNER_BY_MEMBER.remove(memberId);
            ServerPlayerEntity member = server.getPlayerManager().getPlayer(memberId);
            if (memberId.equals(ownerId)) {
               if (reason == TeleportGambitDomainManager.EndReason.OWNER_LEFT_REGION) {
                  manager.clearTeleportGambitPendingReturn(memberId);
                  continue;
               }

               if (reason == TeleportGambitDomainManager.EndReason.OWNER_DIED) {
                  manager.clearTeleportGambitPendingReturn(memberId);
                  if (member != null) {
                     removeDomainBoost(member);
                  }
                  continue;
               }

               if (reason == TeleportGambitDomainManager.EndReason.OWNER_DISCONNECTED) {
                  manager.saveTeleportGambitPendingReturn(memberId, origin.toNbt());
                  if (member != null) {
                     removeDomainBoost(member);
                  }
                  continue;
               }

               if (reason == TeleportGambitDomainManager.EndReason.OWNER_LEFT_DIMENSION
                  && member != null
                  && !member.getEntityWorld().getRegistryKey().equals(DOMAIN_KEY)) {
                  removeDomainBoost(member);
                  manager.clearTeleportGambitPendingReturn(memberId);
                  continue;
               }
            }

            if (member == null || !member.isAlive()) {
               manager.saveTeleportGambitPendingReturn(memberId, origin.toNbt());
               if (member != null) {
                  removeDomainBoost(member);
               }
            } else if (origin.teleport(member, server)) {
               manager.clearTeleportGambitPendingReturn(memberId);
               member.sendMessage(Text.literal("Teleport Gambit's Domain has closed. You have been returned to your previous location."), false);
            } else {
               manager.saveTeleportGambitPendingReturn(memberId, origin.toNbt());
               LOGGER.error("Could not return online Domain member {} to their saved origin; recovery remains pending.", memberId);
            }
         }

         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
         if (owner != null) {
            removeDomainBoost(owner);
            owner.setHealth(Math.min(owner.getHealth(), owner.getMaxHealth()));
            if (reason == TeleportGambitDomainManager.EndReason.OWNER_LEFT_REGION) {
               TeleportGambitDomainManager.PlayerOrigin originx = session.origins.get(ownerId);
               if (originx != null && !originx.teleport(owner, server)) {
                  manager.saveTeleportGambitPendingReturn(ownerId, originx.toNbt());
                  LOGGER.error("Could not return Domain owner {} to their saved origin; recovery remains pending.", ownerId);
               }
            }
         }

         session.origins.clear();
      }
   }

   private static void cancelCharge(UUID ownerId, MinecraftServer server, boolean notify) {
      TeleportGambitDomainManager.Charge charge = CHARGES.remove(ownerId);
      if (charge != null) {
         restoreFrame(server, charge);
         ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
         if (notify && owner != null && owner.isAlive()) {
            owner.sendMessage(Text.literal("The Domain charge was interrupted."), true);
         }
      }
   }

   private static void restoreFrame(MinecraftServer server, TeleportGambitDomainManager.Charge charge) {
      ServerWorld source = server.getWorld(charge.sourceDimension);
      if (source != null) {
         BlockState current = source.getBlockState(charge.framePos);
         boolean gateBlockPresent = current.isOf(Blocks.END_PORTAL_FRAME) || current.isOf(Blocks.BEACON);
         boolean clearedGateSpot = charge.gateRemoved && current.isAir();
         if (gateBlockPresent || clearedGateSpot) {
            source.setBlockState(charge.framePos, charge.originalState, 3);
         }
      }
   }

   private static void ensureDomainBoost(ServerPlayerEntity player) {
      for (TeleportGambitDomainManager.BoostSpec spec : BOOSTS) {
         EntityAttributeInstance attribute = player.getAttributeInstance(spec.attribute());
         if (attribute != null && attribute.getModifier(spec.id()) == null) {
            attribute.addTemporaryModifier(new EntityAttributeModifier(spec.id(), 4.0, Operation.ADD_MULTIPLIED_TOTAL));
         }
      }
   }

   private static void removeDomainBoost(ServerPlayerEntity player) {
      for (TeleportGambitDomainManager.BoostSpec spec : BOOSTS) {
         EntityAttributeInstance attribute = player.getAttributeInstance(spec.attribute());
         if (attribute != null) {
            attribute.removeModifier(spec.id());
         }
      }

      player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
   }

   private static BlockPos domainCenter(UUID ownerId) {
      long hash = ownerId.getMostSignificantBits() ^ Long.rotateLeft(ownerId.getLeastSignificantBits(), 23);
      hash ^= hash >>> 33;
      hash *= -49064778989728563L;
      hash ^= hash >>> 33;
      hash *= -4265267296055464877L;
      hash ^= hash >>> 33;
      int xSlot = (int)(hash & 16383L);
      int zSlot = (int)(hash >>> 14 & 16383L);
      int x = 512 + xSlot * 1024;
      int z = 512 + zSlot * 1024;
      return new BlockPos(x, 65, z);
   }

   private static boolean restorePendingReturn(ServerPlayerEntity player, MinecraftServer server, NbtCompound nbt) {
      if (!nbt.getString("ReturnType", "DOMAIN").equals("DOMAIN")) {
         return false;
      } else {
         TeleportGambitDomainManager.PlayerOrigin origin = TeleportGambitDomainManager.PlayerOrigin.fromNbt(nbt);
         if (origin == null) {
            return false;
         } else if (!origin.teleport(player, server)) {
            return false;
         } else {
            player.sendMessage(Text.literal("You have been returned from Teleport Gambit's Domain."), false);
            return true;
         }
      }
   }

   private record BoostSpec(RegistryEntry<EntityAttribute> attribute, Identifier id) {
   }

   private static final class Charge {
      private final UUID ownerId;
      private final RegistryKey<World> sourceDimension;
      private final BlockPos framePos;
      private final BlockState originalState;
      private final int durationTicks;
      private final double radius;
      private final BlockPos domainCenter;
      private final Set<UUID> affectedPlayers = new HashSet<>();
      private final Map<UUID, Vec3d> pullStartPositions = new HashMap<>();
      private final TeleportGambitDomainManager.PlatformBuilder platformBuilder;
      private TeleportGambitDomainManager.ChargePhase phase = TeleportGambitDomainManager.ChargePhase.CHARGING;
      private int phaseTicks;
      private int elapsedTicks;
      private boolean gateRemoved;

      private Charge(
         UUID ownerId,
         RegistryKey<World> sourceDimension,
         BlockPos framePos,
         BlockState originalState,
         int durationTicks,
         double radius,
         BlockPos domainCenter,
         TeleportGambitDomainManager.PlatformBuilder platformBuilder
      ) {
         this.ownerId = ownerId;
         this.sourceDimension = sourceDimension;
         this.framePos = framePos;
         this.originalState = originalState;
         this.durationTicks = durationTicks;
         this.radius = radius;
         this.domainCenter = domainCenter;
         this.platformBuilder = platformBuilder;
      }
   }

   private static enum ChargePhase {
      CHARGING,
      BEACON_BEAM,
      PULLING;
   }

   private static final class DomainSession {
      private final UUID ownerId;
      private final BlockPos center;
      private final Map<UUID, TeleportGambitDomainManager.PlayerOrigin> origins = new HashMap<>();

      private DomainSession(UUID ownerId, BlockPos center) {
         this.ownerId = ownerId;
         this.center = center;
      }
   }

   private static enum EndReason {
      OWNER_DIED,
      OWNER_DISCONNECTED,
      OWNER_LEFT_DIMENSION,
      OWNER_LEFT_REGION,
      OWNER_POWER_REMOVED,
      SERVER_SHUTDOWN;
   }

   private static final class PlatformBuilder {
      private final BlockPos center;
      private final int total = 66049;
      private int cursor;
      private boolean complete;

      private PlatformBuilder(BlockPos center) {
         this.center = center;
      }

      private boolean isComplete() {
         return this.complete;
      }

      private void advance(ServerWorld world, int allowance) {
         int processed = 0;

         while (this.cursor < 66049 && processed < allowance) {
            int index = this.cursor++;
            int dx = index % 257 - 128;
            int dz = index / 257 - 128;
            if (dx != 0 || dz != 0) {
               BlockPos pos = new BlockPos(this.center.getX() + dx, 64, this.center.getZ() + dz);
               if (!world.getBlockState(pos).isOf(Blocks.BEDROCK)) {
                  world.setBlockState(pos, Blocks.BEDROCK.getDefaultState(), 2);
               }

               processed++;
            }
         }

         if (this.cursor >= 66049) {
            BlockPos marker = new BlockPos(this.center.getX(), 64, this.center.getZ());
            if (!world.getBlockState(marker).isOf(Blocks.BEDROCK)) {
               world.setBlockState(marker, Blocks.BEDROCK.getDefaultState(), 3);
            }

            this.complete = true;
         }
      }
   }

   private record PlayerOrigin(String dimension, double x, double y, double z, float yaw, float pitch) {
      private static TeleportGambitDomainManager.PlayerOrigin capture(ServerPlayerEntity player) {
         return new TeleportGambitDomainManager.PlayerOrigin(
            player.getEntityWorld().getRegistryKey().getValue().toString(), player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch()
         );
      }

      private NbtCompound toNbt() {
         NbtCompound nbt = new NbtCompound();
         nbt.putString("ReturnType", "DOMAIN");
         nbt.putString("Dimension", this.dimension);
         nbt.putDouble("X", this.x);
         nbt.putDouble("Y", this.y);
         nbt.putDouble("Z", this.z);
         nbt.putFloat("Yaw", this.yaw);
         nbt.putFloat("Pitch", this.pitch);
         return nbt;
      }

      private static TeleportGambitDomainManager.PlayerOrigin fromNbt(NbtCompound nbt) {
         String dimension = nbt.getString("Dimension", "");
         return Identifier.tryParse(dimension) == null
            ? null
            : new TeleportGambitDomainManager.PlayerOrigin(
               dimension, nbt.getDouble("X", 0.0), nbt.getDouble("Y", 80.0), nbt.getDouble("Z", 0.0), nbt.getFloat("Yaw", 0.0F), nbt.getFloat("Pitch", 0.0F)
            );
      }

      private boolean teleport(ServerPlayerEntity player, MinecraftServer server) {
         Identifier identifier = Identifier.tryParse(this.dimension);
         ServerWorld destination = null;
         if (identifier != null) {
            RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, identifier);
            destination = server.getWorld(key);
         }

         if (destination == null) {
            destination = server.getOverworld();
         }

         boolean transferred = TeleportGambitDomainManager.teleportPlayer(server, player, destination, this.x, this.y, this.z, this.yaw, this.pitch);
         if (!transferred && destination != server.getOverworld()) {
            BlockPos spawn = server.getOverworld().getSpawnPoint().getPos();
            transferred = TeleportGambitDomainManager.teleportPlayer(
               server, player, server.getOverworld(), spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, this.yaw, this.pitch
            );
         }

         if (!transferred) {
            TeleportGambitDomainManager.LOGGER
               .error(
                  "Could not restore player {} to previous location {} {} {} in {}.",
                  new Object[]{player.getUuid(), this.x, this.y, this.z, destination.getRegistryKey().getValue()}
               );
         }

         return transferred;
      }
   }
}
