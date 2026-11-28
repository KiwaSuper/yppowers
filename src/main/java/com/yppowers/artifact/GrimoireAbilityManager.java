package com.yppowers.artifact;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.network.YPNetworking;
import com.yppowers.power.PowerManager;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import com.yppowers.system.PowerCombatUtil;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;

public final class GrimoireAbilityManager {
   private static final Map<UUID, GrimoireAbilityManager.BeamState> FIRING = new HashMap<>();

   private GrimoireAbilityManager() {
   }

   public static void setBeamFiring(ServerPlayerEntity player, boolean firing) {
      if (!firing) {
         GrimoireAbilityManager.BeamState removed = FIRING.remove(player.getUuid());
         if (removed != null) {
            clearBlockProgress(player, removed);
            YPNetworking.syncGrimoireBookVisual(player, false, 0);
         }
      } else {
         ServerWorld var3 = player.getEntityWorld();
         if (var3 instanceof ServerWorld && player.isAlive() && !player.isRemoved() && !FIRING.containsKey(player.getUuid())) {
            FIRING.put(player.getUuid(), new GrimoireAbilityManager.BeamState(var3.getTime()));
            YPNetworking.syncGrimoireBookVisual(player, true, 30);
         }
      }
   }

   public static void onDisconnect(ServerPlayerEntity player) {
      if (player != null) {
         setBeamFiring(player, false);
      }
   }

   public static void clearRuntimeState(MinecraftServer server) {
      if (server != null) {
         for (Entry<UUID, GrimoireAbilityManager.BeamState> entry : FIRING.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
               clearBlockProgress(player, entry.getValue());
               YPNetworking.syncGrimoireBookVisual(player, false, 0);
            }
         }
      }
      FIRING.clear();
   }

   public static void serverTick(MinecraftServer server) {
      if (!FIRING.isEmpty()) {
         Iterator<Entry<UUID, GrimoireAbilityManager.BeamState>> iterator = FIRING.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<UUID, GrimoireAbilityManager.BeamState> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null && player.isAlive() && !player.isRemoved() && !GraceManager.isActive(server)) {
               ServerWorld state = player.getEntityWorld();
               if (state instanceof ServerWorld
                  && GrimoireOfLightManager.hasGrimoire(player)
                  && PowerManager.get(server).isGrimoireSkillsetEnabled(player.getUuid())) {
                  tickBeam(player, state, entry.getValue());
                  GrimoireAbilityManager.BeamState statex = entry.getValue();
                  if (state.getTime() - statex.lastVisualSyncTime >= 10L) {
                     YPNetworking.syncGrimoireBookVisual(player, true, 30);
                     statex.lastVisualSyncTime = state.getTime();
                  }
                  continue;
               }
            }

            GrimoireAbilityManager.BeamState removed = entry.getValue();
            iterator.remove();
            if (player != null) {
               clearBlockProgress(player, removed);
               YPNetworking.syncGrimoireBookVisual(player, false, 0);
            }
         }
      }
   }

   private static void tickBeam(ServerPlayerEntity player, ServerWorld world, GrimoireAbilityManager.BeamState state) {
      YPConfig config = YPConfig.getInstance();
      Vec3d start = player.getEyePos();
      Vec3d direction = player.getRotationVec(1.0F).normalize();
      if (world.getTime() % 2L == 0L) {
         Vec3d book = start.add(direction.multiply(0.82));
         world.spawnParticles(ParticleTypes.ENCHANT, true, true, book.x, book.y, book.z, 3, 0.12, 0.12, 0.12, 0.2);
         world.spawnParticles(ParticleTypes.WAX_ON, true, true, book.x, book.y, book.z, 1, 0.08, 0.08, 0.08, 0.0);
      }

      Vec3d end = start.add(direction.multiply(config.grimoire_beam_range_blocks));
      BlockHitResult blockHit = world.raycast(new RaycastContext(start, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
      Vec3d blockEnd = blockHit.getType() == Type.MISS ? end : blockHit.getPos();
      double blockDistance = start.distanceTo(blockEnd);
      LivingEntity target = findLivingTarget(player, world, start, end, blockDistance);
      if (target != null && world.getTime() - state.lastDamageTime >= config.grimoire_beam_damage_interval_ticks) {
         if (config.grimoire_beam_damage > 0.0 && PowerCombatUtil.canHarm(player, target)) {
            target.damage(world, world.getDamageSources().indirectMagic(player, player), (float)config.grimoire_beam_damage);
         }

         state.lastDamageTime = world.getTime();
      }

      Vec3d visualEnd = target == null ? blockEnd : target.getBoundingBox().getCenter();
      emitBeamParticles(world, start, visualEnd);
      if (blockHit.getType() != Type.BLOCK || target != null && !(blockDistance <= start.distanceTo(target.getBoundingBox().getCenter()))) {
         clearBlockProgress(player, state);
      } else {
         progressBlockCarving(player, world, blockHit.getBlockPos(), state, config.grimoire_beam_block_break_ticks);
      }
   }

   private static LivingEntity findLivingTarget(ServerPlayerEntity player, ServerWorld world, Vec3d start, Vec3d end, double maxDistance) {
      Box search = player.getBoundingBox().stretch(end.subtract(start)).expand(0.8);
      LivingEntity nearest = null;
      double nearestDistance = maxDistance;

      for (Entity entity : world.getOtherEntities(
         player, search, candidate -> candidate instanceof LivingEntity livingx && livingx.isAlive() && livingx.isAttackable() && PowerCombatUtil.canHarm(player, livingx)
      )) {
         LivingEntity living = (LivingEntity)entity;
         Optional<Vec3d> intercept = living.getBoundingBox().expand(0.25).raycast(start, end);
         if (!intercept.isEmpty()) {
            double distance = start.distanceTo(intercept.get());
            if (distance < nearestDistance) {
               nearestDistance = distance;
               nearest = living;
            }
         }
      }

      return nearest;
   }

   private static void progressBlockCarving(
      ServerPlayerEntity player, ServerWorld world, BlockPos pos, GrimoireAbilityManager.BeamState state, int requiredTicks
   ) {
      BlockState block = world.getBlockState(pos);
      if (!block.isAir()
         && !(block.getHardness(world, pos) < 0.0F)
         && world.getBlockEntity(pos) == null
         && player.getAbilities().allowModifyWorld
         && world.getWorldBorder().contains(pos)
         && TimeWarperDomainManager.mayModifyBlock(player, pos)) {
         if (state.carvingWorld != world || !pos.equals(state.carvingPos)) {
            clearBlockProgress(player, state);
            state.carvingWorld = world;
            state.carvingPos = pos.toImmutable();
            state.carvingTicks = 0;
         }

         state.carvingTicks++;
         int safeRequired = Math.max(1, requiredTicks);
         int stage = Math.max(0, Math.min(9, (int)(state.carvingTicks * 10L / safeRequired)));
         world.setBlockBreakingInfo(player.getId(), pos, stage);
         if (state.carvingTicks >= safeRequired) {
            world.breakBlock(pos, true, player);
            world.setBlockBreakingInfo(player.getId(), pos, -1);
            state.carvingPos = null;
            state.carvingWorld = null;
            state.carvingTicks = 0;
         }
      } else {
         clearBlockProgress(player, state);
      }
   }

   private static void clearBlockProgress(ServerPlayerEntity player, GrimoireAbilityManager.BeamState state) {
      if (state.carvingPos != null) {
         ServerWorld targetWorld = state.carvingWorld != null ? state.carvingWorld : player.getEntityWorld();
         if (targetWorld != null) {
            targetWorld.setBlockBreakingInfo(player.getId(), state.carvingPos, -1);
         }
         state.carvingPos = null;
         state.carvingWorld = null;
         state.carvingTicks = 0;
      }
   }

   private static void emitBeamParticles(ServerWorld world, Vec3d start, Vec3d end) {
      Vec3d delta = end.subtract(start);
      double length = delta.length();
      if (!(length < 0.01)) {
         Vec3d step = delta.multiply(1.0 / length);
         int particles = Math.max(1, Math.min(48, (int)(length / 0.7)));
         double spacing = length / particles;

         for (int i = 1; i <= particles; i++) {
            Vec3d point = start.add(step.multiply(i * spacing));
            world.spawnParticles(ParticleTypes.END_ROD, true, true, point.x, point.y, point.z, 1, 0.015, 0.015, 0.015, 0.0);
         }

         world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, true, true, end.x, end.y, end.z, 2, 0.12, 0.12, 0.12, 0.015);
      }
   }

   private static final class BeamState {
      private long lastDamageTime;
      private long lastVisualSyncTime;
      private ServerWorld carvingWorld;
      private BlockPos carvingPos;
      private int carvingTicks;

      private BeamState(long now) {
         this.lastDamageTime = now - 1200L;
         this.lastVisualSyncTime = now;
      }
   }
}
