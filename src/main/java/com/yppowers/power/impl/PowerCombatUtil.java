package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

final class PowerCombatUtil {
   private PowerCombatUtil() {
   }

   static boolean canHarm(ServerPlayerEntity owner, Entity target) {
      if (owner == null || target == null || target == owner || target.isRemoved() || !target.isAlive()) {
         return false;
      } else if (owner.getEntityWorld().getServer() != null && !GraceManager.isActive(owner.getEntityWorld().getServer())) {
         return target instanceof ServerPlayerEntity other && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other)
            ? false
            : !isFriendlyMinion(owner, target);
      } else {
         return false;
      }
   }

   static boolean isFriendlyMinion(ServerPlayerEntity owner, Entity target) {
      if (owner != null && target instanceof MobEntity mob && owner.getEntityWorld().getServer() != null) {
         for (String tag : mob.getCommandTags()) {
            if (tag.startsWith("yppowers_owner_")) {
               try {
                  UUID ownerId = UUID.fromString(tag.substring("yppowers_owner_".length()));
                  ServerPlayerEntity minionOwner = owner.getEntityWorld().getServer().getPlayerManager().getPlayer(ownerId);
                  if (ownerId.equals(owner.getUuid())) {
                     return true;
                  }

                  if (AllianceManager.get(owner.getEntityWorld().getServer()).areAllied(owner.getUuid(), ownerId)) {
                     return true;
                  }
               } catch (IllegalArgumentException var7) {
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static LivingEntity pickDefenseTarget(ServerPlayerEntity owner, double radius) {
      ServerWorld attacker = owner.getEntityWorld();
      if (attacker instanceof ServerWorld && owner.getEntityWorld().getServer() != null && !GraceManager.isActive(owner.getEntityWorld().getServer())) {
         LivingEntity attackerx = owner.getAttacker();
         if (attackerx != null && attackerx != owner && attackerx.isAlive() && canHarm(owner, attackerx)) {
            return attackerx;
         } else {
            LivingEntity victim = owner.getAttacking();
            if (victim != null && victim != owner && victim.isAlive() && canHarm(owner, victim)) {
               return victim;
            } else {
               LivingEntity best = null;
               double bestDistance = radius * radius;

               for (LivingEntity e : attacker.getEntitiesByClass(
                  LivingEntity.class, owner.getBoundingBox().expand(radius), x -> x != owner && x.isAlive() && canHarm(owner, x)
               )) {
                  boolean threat = e instanceof Monster || e instanceof ServerPlayerEntity || e instanceof MobEntity mob && mob.getTarget() == owner;
                  if (threat) {
                     double d = owner.squaredDistanceTo(e);
                     if (d < bestDistance) {
                        best = e;
                        bestDistance = d;
                     }
                  }
               }

               return best;
            }
         }
      } else {
         return null;
      }
   }

   static LivingEntity nearestTarget(ServerPlayerEntity owner, double radius) {
      ServerWorld alliances = owner.getEntityWorld();
      if (alliances instanceof ServerWorld && owner.getEntityWorld().getServer() != null && !GraceManager.isActive(owner.getEntityWorld().getServer())) {
         AllianceManager alliancesx = AllianceManager.get(owner.getEntityWorld().getServer());
         Box box = owner.getBoundingBox().expand(radius);
         LivingEntity best = null;
         double bestDistance = radius * radius;

         for (LivingEntity candidate : alliances.getEntitiesByClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && canHarm(owner, e))) {
            double d = owner.squaredDistanceTo(candidate);
            if (d < bestDistance) {
               best = candidate;
               bestDistance = d;
            }
         }

         return best;
      } else {
         return null;
      }
   }

   static Vec3d safeLook(ServerPlayerEntity player) {
      Vec3d look = player.getRotationVec(1.0F);
      return look.lengthSquared() < 1.0E-6 ? new Vec3d(0.0, 0.0, 1.0) : look.normalize();
   }
}
