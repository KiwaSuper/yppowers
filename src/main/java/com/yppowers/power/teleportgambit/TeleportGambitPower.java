package com.yppowers.power.teleportgambit;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import java.util.List;
import com.yppowers.system.TargetingUtil;
import com.yppowers.system.EpicVfx;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import com.yppowers.progression.RitualManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class TeleportGambitPower extends Power {
   private final Map<UUID, Integer> activePads = new HashMap<>();
   private final Map<UUID, Integer> gatewayCooldowns = new HashMap<>();
   private final Map<UUID, BlockPos> padPositions = new HashMap<>();
   private final Map<UUID, BlockState> originalBlocks = new HashMap<>();
   private final Map<UUID, RegistryKey<World>> padWorlds = new HashMap<>();
   private final Map<UUID, Long> transferLockoutUntil = new HashMap<>();
   private static final int TRANSFER_LOCKOUT_TICKS = 20;

   @Override
   public PowerType getType() {
      return PowerType.GAMBIT;
   }

   @Override
   public void onSecondaryActivate(ServerPlayerEntity player) {
      TeleportGambitDomainManager.beginCharge(player);
   }

   @Override
   public void onTertiaryActivate(ServerPlayerEntity player) {
      TeleportGambitRandomPortalManager.open(player);
   }

   @Override
   public boolean canUseKey(ServerPlayerEntity player, int keyType) {
      if (keyType != 2) {
         return true;
      } else if (player.getEntityWorld().getRegistryKey().equals(TeleportGambitDomainManager.DOMAIN_KEY)
         && !TeleportGambitRandomPortalManager.hasReturnGroup(player.getUuid())) {
         player.sendMessage(Text.literal("The Gambit Gate cannot be opened from inside your private Domain."), true);
         return false;
      } else if (TeleportGambitRandomPortalManager.hasActivePortal(player.getUuid())) {
         player.sendMessage(Text.literal("Your random portal circle is already charging."), true);
         return false;
      } else if (TeleportGambitRandomPortalManager.hasReturnGroup(player.getUuid())) {
         return true;
      } else {
         int cooldown = TeleportGambitRandomPortalManager.getCooldownTicks(player.getUuid());
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Random Portal cooldown: " + (cooldown + 19) / 20 + "s"), true);
            return false;
         } else {
            return true;
         }
      }
   }

   @Override
   public void onPlayerDeath(ServerPlayerEntity player) {
      this.removePad(player, true);
      if (player.getEntityWorld().getServer() != null) {
         TeleportGambitRandomPortalManager.returnGroup(player.getEntityWorld().getServer(), player.getUuid(), player, true);
      }

      TeleportGambitRandomPortalManager.cancel(player.getUuid());
      TeleportGambitDomainManager.onPlayerDeath(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      TeleportGambitRandomPortalManager.restorePendingReturn(player);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      TeleportGambitRandomPortalManager.restorePendingReturn(player);
   }

   @Override
   public void onPrimaryActivate(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (this.activePads.containsKey(uuid)) {
         player.sendMessage(Text.literal("Your gateway is already active."), true);
      } else {
         int cooldown = this.gatewayCooldowns.getOrDefault(uuid, 0);
         if (cooldown > 0) {
            player.sendMessage(Text.literal("Gateway on cooldown: " + (cooldown + 19) / 20 + "s"), true);
         } else {
            BlockPos pos = player.getBlockPos().down().toImmutable();
            ServerWorld world = player.getEntityWorld();
            BlockState oldState = world.getBlockState(pos);
            if (world.isInBuildLimit(pos)
               && world.getWorldBorder().contains(pos)
               && TimeWarperDomainManager.mayModifyBlock(player, pos)
               && !oldState.hasBlockEntity()
               && !(oldState.getHardness(world, pos) < 0.0F)) {
               this.originalBlocks.put(uuid, oldState);
               this.padWorlds.put(uuid, world.getRegistryKey());
               this.padPositions.put(uuid, pos);
               this.activePads.put(uuid, Math.max(1, YPConfig.getInstance().teleport_gambit_gateway_duration_ticks));
               world.setBlockState(pos, Blocks.END_PORTAL.getDefaultState(), 3);
               int configuredCooldown = Math.max(0, YPConfig.getInstance().teleport_gambit_gateway_cooldown_ticks);
               if (configuredCooldown > 0) {
                  this.gatewayCooldowns.put(uuid, configuredCooldown);
               }

               world.playSound(null, pos, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0F, 1.0F);
               world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, 30, 0.35, 0.05, 0.35, 0.1);
               EpicVfx.activation(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.2);
               EpicVfx.column(world, ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 4.0, 12);
               EpicVfx.cue(world, player.getId(), "teleport_gambit", "gateway", pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Vec3d.ZERO, 1.2F, 24, 0.0F);
            } else {
               player.sendMessage(Text.literal("A gateway cannot replace this block."), true);
            }
         }
      }
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      int cooldown = this.gatewayCooldowns.getOrDefault(uuid, 0);
      if (cooldown > 0) {
         if (--cooldown <= 0) {
            this.gatewayCooldowns.remove(uuid);
         } else {
            this.gatewayCooldowns.put(uuid, cooldown);
         }
      }

      if (this.activePads.containsKey(uuid)) {
         ServerWorld world = player.getEntityWorld();
         BlockPos padPos = this.padPositions.get(uuid);
         int ticksLeft = this.activePads.get(uuid) - 1;
         if (padPos != null && world.getRegistryKey().equals(this.padWorlds.get(uuid))) {
            ServerPlayerEntity entrant = null;
            long now = world.getTime();

            for (ServerPlayerEntity candidatePlayer : player.getEntityWorld().getServer().getPlayerManager().getPlayerList()) {
               if (candidatePlayer.getEntityWorld() == world && isAtPad(candidatePlayer, padPos)) {
                  Long lockoutUntil = this.transferLockoutUntil.get(candidatePlayer.getUuid());
                  if (lockoutUntil != null) {
                     if (now < lockoutUntil) {
                        continue;
                     }

                     this.transferLockoutUntil.remove(candidatePlayer.getUuid());
                  }

                  entrant = candidatePlayer;
                  break;
               }
            }

            if (entrant != null) {
               Vec3d look = entrant.getRotationVec(1.0F).normalize();
               BlockPos dest = null;

               for (int distance = 25; distance <= 50; distance++) {
                  BlockPos candidate = BlockPos.ofFloored(entrant.getEyePos().add(look.multiply(distance)));
                  if (this.isSafe(world, candidate, entrant)) {
                     dest = candidate;
                     break;
                  }
               }

               if (dest == null) {
                  for (int distancex = 51; distancex <= 256 && dest == null; distancex++) {
                     BlockPos candidate = BlockPos.ofFloored(entrant.getEyePos().add(look.multiply(distancex)));
                     if (this.isSafe(world, candidate, entrant)) {
                        dest = candidate;
                     }
                  }

                  for (int distancexx = 24; distancexx >= 1 && dest == null; distancexx--) {
                     BlockPos candidate = BlockPos.ofFloored(entrant.getEyePos().add(look.multiply(distancexx)));
                     if (this.isSafe(world, candidate, entrant)) {
                        dest = candidate;
                     }
                  }
               }

               if (dest != null) {
                  world.spawnParticles(ParticleTypes.PORTAL, true, true, entrant.getX(), entrant.getY(), entrant.getZ(), 24, 0.5, 0.5, 0.5, 0.1);
                  EpicVfx.implosion(world, ParticleTypes.PORTAL, entrant.getX(), entrant.getY() + 0.2, entrant.getZ(), 1.8, 18, 0.25);
                  this.transferLockoutUntil.put(entrant.getUuid(), world.getTime() + 20L);
                  boolean wasInDanger = RitualManager.isInDanger(entrant);
                  boolean teleported = entrant.teleport(
                     world, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, Set.of(), entrant.getYaw(), entrant.getPitch(), false
                  );
                  if (teleported) {
                     RitualManager.recordDangerEscape(entrant, wasInDanger);
                     EpicVfx.cue(
                        world, entrant.getId(), "teleport_gambit", "arrival", dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, Vec3d.ZERO, 1.2F, 18, 0.0F
                     );
                  }

                  entrant.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0));
                  entrant.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 40, 1));
                  world.spawnParticles(ParticleTypes.PORTAL, true, true, dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5, 24, 0.5, 0.8, 0.5, 0.1);
                  EpicVfx.shockwave(world, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 4.0);
                  EpicVfx.column(world, ParticleTypes.REVERSE_PORTAL, dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, 4.0, 12);
                  world.playSound(null, dest, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.0F);
                  this.removePad(player, true);
                  return;
               }
            }

            if (ticksLeft % 3 == 0) {
               world.spawnParticles(ParticleTypes.REVERSE_PORTAL, true, true, padPos.getX() + 0.5, padPos.getY() + 0.1, padPos.getZ() + 0.5, 2, 0.2, 0.05, 0.2, 0.02);
            }

            if (ticksLeft % 10 == 0) {
               EpicVfx.helix(world, ParticleTypes.REVERSE_PORTAL, padPos.getX() + 0.5, padPos.getY() + 0.1, padPos.getZ() + 0.5, 2.0, 0.35, 1.0, 8, 0.035);
            }

            if (ticksLeft <= 0) {
               boolean wasPortal = world.getBlockState(padPos).isOf(Blocks.END_PORTAL);
               this.removePad(player, true);
               if (wasPortal && !world.getBlockState(padPos).isOf(Blocks.END_PORTAL)) {
                  EpicVfx.implosion(world, ParticleTypes.REVERSE_PORTAL, padPos.getX() + 0.5, padPos.getY() + 0.15, padPos.getZ() + 0.5, 1.3, 14, 0.15);
               }
            } else {
               this.activePads.put(uuid, ticksLeft);
            }
         } else {
            this.removePad(player, true);
         }
      }
   }

   private static boolean isAtPad(ServerPlayerEntity player, BlockPos padPos) {
      return player.getBlockPos().equals(padPos) || player.getBlockPos().down().equals(padPos);
   }

   private boolean isSafe(ServerWorld world, BlockPos pos, ServerPlayerEntity player) {
      BlockPos floorPos = pos.down();
      if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)
         && world.isInBuildLimit(floorPos)
         && world.isInBuildLimit(pos)
         && world.isInBuildLimit(pos.up())
         && world.getWorldBorder().contains(pos)
         && world.getWorldBorder().contains(pos.add(1, 0, 1))) {
         BlockState floor = world.getBlockState(floorPos);
         if (floor.getCollisionShape(world, floorPos).isEmpty() || !world.getFluidState(floorPos).isEmpty()) {
            return false;
         } else if (floor.isOf(Blocks.MAGMA_BLOCK)
            || floor.isOf(Blocks.CAMPFIRE)
            || floor.isOf(Blocks.SOUL_CAMPFIRE)
            || floor.isOf(Blocks.CACTUS)
            || floor.isOf(Blocks.FIRE)
            || floor.isOf(Blocks.SOUL_FIRE)
            || floor.isOf(Blocks.SWEET_BERRY_BUSH)
            || floor.isOf(Blocks.WITHER_ROSE)
            || floor.isOf(Blocks.POINTED_DRIPSTONE)) {
            return false;
         } else if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
            && world.getBlockState(pos.up()).getCollisionShape(world, pos.up()).isEmpty()
            && world.getFluidState(pos).isEmpty()
            && world.getFluidState(pos.up()).isEmpty()) {
            Vec3d destination = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            return TargetingUtil.canFitAt(world, player, destination);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void removePad(ServerPlayerEntity player, boolean restore) {
      UUID uuid = player.getUuid();
      BlockPos pos = this.padPositions.remove(uuid);
      BlockState old = this.originalBlocks.remove(uuid);
      RegistryKey<World> dimension = this.padWorlds.remove(uuid);
      this.activePads.remove(uuid);
      if (restore && pos != null && dimension != null && player.getEntityWorld().getServer() != null) {
         ServerWorld world = player.getEntityWorld().getServer().getWorld(dimension);
         if (world != null && world.getBlockState(pos).isOf(Blocks.END_PORTAL) && old != null) {
            world.setBlockState(pos, old, 3);
         }
      }
   }

   public static boolean isCustomGatewayBlock(ServerWorld world, BlockPos pos) {
      if (world == null || pos == null || world.getServer() == null) {
         return false;
      } else if (PowerManager.get(world.getServer()).getPowerInstance(PowerType.GAMBIT) instanceof TeleportGambitPower gambit) {
         RegistryKey<World> dimension = world.getRegistryKey();

         for (Entry<UUID, BlockPos> entry : gambit.padPositions.entrySet()) {
            if (pos.equals(entry.getValue()) && gambit.activePads.containsKey(entry.getKey()) && dimension.equals(gambit.padWorlds.get(entry.getKey()))) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static boolean isCustomGateway(ServerPlayerEntity player, BlockPos pos) {
      return player != null && isCustomGatewayBlock(player.getEntityWorld(), pos);
   }

   public static void shutdown(MinecraftServer server) {
      if (server == null) {
         return;
      }
      if (PowerManager.get(server).getPowerInstance(PowerType.GAMBIT) instanceof TeleportGambitPower gambit) {
         for (UUID uuid : new ArrayList<>(gambit.padPositions.keySet())) {
            BlockPos pos = gambit.padPositions.remove(uuid);
            BlockState old = gambit.originalBlocks.remove(uuid);
            RegistryKey<World> dimension = gambit.padWorlds.remove(uuid);
            gambit.activePads.remove(uuid);
            if (pos != null && dimension != null && old != null) {
               ServerWorld world = server.getWorld(dimension);
               if (world != null && world.getBlockState(pos).isOf(Blocks.END_PORTAL)) {
                  world.setBlockState(pos, old, 3);
               }
            }
         }
         gambit.activePads.clear();
         gambit.padPositions.clear();
         gambit.originalBlocks.clear();
         gambit.padWorlds.clear();
         gambit.transferLockoutUntil.clear();
      }
   }

   public static void playerDisconnected(ServerPlayerEntity player) {
      TeleportGambitRandomPortalManager.cancel(player.getUuid());
      TeleportGambitDomainManager.onPlayerDisconnect(player);
      if (player.getEntityWorld().getServer() != null) {
         if (PowerManager.get(player.getEntityWorld().getServer()).getPowerInstance(PowerType.GAMBIT) instanceof TeleportGambitPower gambit) {
            if (gambit.activePads.containsKey(player.getUuid())) {
               gambit.removePad(player, true);
            }

            gambit.transferLockoutUntil.remove(player.getUuid());
         }
      }
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      TeleportGambitRandomPortalManager.cancel(player.getUuid());
      TeleportGambitDomainManager.onPowerRemoved(player);
      this.removePad(player, true);
      this.gatewayCooldowns.remove(player.getUuid());
   }

   @Override
   public void clearCooldowns(UUID playerId) {
      this.gatewayCooldowns.remove(playerId);
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      if (player.getEntityWorld().getServer() == null) return List.of();
      YPConfig cfg = YPConfig.getInstance();
      UUID id = player.getUuid();
      int domainCd = PowerManager.get(player.getEntityWorld().getServer()).getTeleportGambitDomainCooldownTicks(id);
      return List.of(
         HudPill.ability(AbilityKey.PRIMARY, "Gateway", this.gatewayCooldowns.getOrDefault(id, 0), cfg.teleport_gambit_gateway_cooldown_ticks, energy >= cfg.energy_cost_primary),
         HudPill.ability(AbilityKey.SECONDARY, "Domain", domainCd, cfg.teleport_gambit_domain_cooldown_ticks, energy >= cfg.energy_cost_secondary)
      );
   }
}
