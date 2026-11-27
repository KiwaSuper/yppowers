package com.yppowers.power.impl;

import java.util.Optional;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
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

final class TargetingUtil {
   private TargetingUtil() {
   }

   static Entity pickLookTarget(ServerPlayerEntity player, double range) {
      ServerWorld eye = player.getEntityWorld();
      if (eye instanceof ServerWorld && !(range <= 0.0)) {
         Vec3d eyex = player.getEyePos();
         Vec3d look = player.getRotationVec(1.0F);
         if (look.lengthSquared() < 1.0E-6) {
            return null;
         } else {
            look = look.normalize();
            Vec3d end = eyex.add(look.multiply(range));
            BlockHitResult blockHit = eye.raycast(new RaycastContext(eyex, end, ShapeType.OUTLINE, FluidHandling.NONE, player));
            double blockDistance = blockHit.getType() == Type.BLOCK ? blockHit.getPos().distanceTo(eyex) : Double.MAX_VALUE;
            Box search = new Box(eyex, end).expand(1.0);
            Entity best = null;
            double bestDistance = Double.MAX_VALUE;

            for (Entity candidate : eye.getOtherEntities(player, search, e -> isValidTarget(player, e))) {
               Optional<Vec3d> hit = candidate.getBoundingBox().expand(candidate.getTargetingMargin()).raycast(eyex, end);
               if (!hit.isEmpty()) {
                  double distance = hit.get().distanceTo(eyex);
                  if (!(distance > range) && !(distance >= bestDistance)) {
                     best = candidate;
                     bestDistance = distance;
                  }
               }
            }

            return best != null && !(bestDistance > blockDistance) ? best : null;
         }
      } else {
         return null;
      }
   }

   private static boolean isValidTarget(ServerPlayerEntity player, Entity entity) {
      return entity != player && entity.isAlive() && !entity.isRemoved() && !entity.isSpectator();
   }

   static boolean canFitAt(ServerWorld world, Entity entity, Vec3d position) {
      Vec3d delta = position.subtract(entity.getEntityPos());
      Box moved = entity.getBoundingBox().offset(delta);
      return world.isBlockSpaceEmpty(entity, moved);
   }

   static Vec3d findSafeNear(ServerWorld world, Entity entity, Vec3d center) {
      double[] verticalOffsets = new double[]{0.0, 0.5, 1.0, 1.5, -0.5, -1.0, 2.0, -1.5};
      double[][] horizontalOffsets = new double[][]{
         {0.0, 0.0}, {0.35, 0.0}, {-0.35, 0.0}, {0.0, 0.35}, {0.0, -0.35}, {0.35, 0.35}, {-0.35, 0.35}, {0.35, -0.35}, {-0.35, -0.35}
      };

      for (double dy : verticalOffsets) {
         for (double[] offset : horizontalOffsets) {
            Vec3d candidate = new Vec3d(center.x + offset[0], center.y + dy, center.z + offset[1]);
            if (world.getWorldBorder().contains(BlockPos.ofFloored(candidate)) && canFitAt(world, entity, candidate)) {
               return candidate;
            }
         }
      }

      return null;
   }

   static Vec3d findSafeLandingNear(ServerWorld world, Entity entity, Vec3d center) {
      double[][] horizontalOffsets = new double[][]{
         {0.0, 0.0}, {0.35, 0.0}, {-0.35, 0.0}, {0.0, 0.35}, {0.0, -0.35}, {0.35, 0.35}, {-0.35, 0.35}, {0.35, -0.35}, {-0.35, -0.35}
      };

      for (int halfStep = 0; halfStep <= 64; halfStep++) {
         double dy = -halfStep * 0.5;

         for (double[] offset : horizontalOffsets) {
            Vec3d candidate = new Vec3d(center.x + offset[0], center.y + dy, center.z + offset[1]);
            BlockPos feet = BlockPos.ofFloored(candidate);
            BlockPos floorPos = feet.down();
            if (world.isInBuildLimit(floorPos)
               && world.isInBuildLimit(feet)
               && world.isInBuildLimit(feet.up())
               && world.getWorldBorder().contains(feet)
               && world.getWorldBorder().contains(feet.add(1, 0, 1))) {
               BlockState floor = world.getBlockState(floorPos);
               if (!floor.getCollisionShape(world, floorPos).isEmpty()
                  && world.getFluidState(floorPos).isEmpty()
                  && !floor.isOf(Blocks.MAGMA_BLOCK)
                  && !floor.isOf(Blocks.CAMPFIRE)
                  && !floor.isOf(Blocks.SOUL_CAMPFIRE)
                  && !floor.isOf(Blocks.CACTUS)
                  && !floor.isOf(Blocks.FIRE)
                  && !floor.isOf(Blocks.SOUL_FIRE)
                  && !floor.isOf(Blocks.SWEET_BERRY_BUSH)
                  && !floor.isOf(Blocks.WITHER_ROSE)
                  && !floor.isOf(Blocks.POINTED_DRIPSTONE)
                  && world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                  && world.getBlockState(feet.up()).getCollisionShape(world, feet.up()).isEmpty()
                  && world.getFluidState(feet).isEmpty()
                  && world.getFluidState(feet.up()).isEmpty()
                  && canFitAt(world, entity, candidate)) {
                  return candidate;
               }
            }
         }
      }

      return null;
   }
}
