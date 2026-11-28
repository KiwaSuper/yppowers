package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.entity.BlackenedMobEntityAccess;
import com.yppowers.artifact.GauntletOfDarknessManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.progression.RitualManager;
import java.util.UUID;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.data.DataTracker.Builder;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({MobEntity.class})
public abstract class MobEntityMixin implements BlackenedMobEntityAccess {
   @Unique
   private static TrackedData<Boolean> yppowers$blackenedByDarkness;

   @Inject(
      method = {"<clinit>"},
      at = {@At("TAIL")}
   )
   private static void yppowers$registerDarknessTracker(CallbackInfo ci) {
      yppowers$blackenedByDarkness = DataTracker.registerData(MobEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
   }

   @Inject(
      method = {"initDataTracker"},
      at = {@At("TAIL")}
   )
   protected void yppowers$initDarknessTracker(Builder builder, CallbackInfo ci) {
      builder.add(yppowers$blackenedByDarkness, false);
   }

   @Override
   public boolean yppowers$isBlackenedByDarkness() {
      return ((MobEntity)(Object)this).getDataTracker().get(yppowers$blackenedByDarkness);
   }

   @Override
   public void yppowers$setBlackenedByDarkness(boolean blackened) {
      ((MobEntity)(Object)this).getDataTracker().set(yppowers$blackenedByDarkness, blackened);
   }

   @Inject(
      method = {"initialize"},
      at = {@At("RETURN")}
   )
   private void yppowers$markNaturallySpawnedDarknessMob(
      ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, EntityData entityData, CallbackInfoReturnable<EntityData> cir
   ) {
      if (spawnReason == SpawnReason.NATURAL || spawnReason == SpawnReason.CHUNK_GENERATION) {
         MobEntity mob = (MobEntity)(Object)this;
         if (GauntletOfDarknessManager.isInsideDarknessInfluence(mob)) {
            mob.addCommandTag("yppowers_gauntlet_darkness_blackened");
            this.yppowers$setBlackenedByDarkness(true);
         }
      }
   }

   @Inject(
      method = {"setTarget"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$setTarget(LivingEntity target, CallbackInfo ci) {
      if (target instanceof ServerPlayerEntity player && this.yppowers$shouldIgnore(player)) {
         ci.cancel();
      } else if (target != null && yppowers$sameSide((MobEntity)(Object)this, target)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void yppowers$clearIgnoredTargetBeforeAi(CallbackInfo ci) {
      MobEntity mob = (MobEntity)(Object)this;
      if (!this.yppowers$isBlackenedByDarkness() && mob.getCommandTags().contains("yppowers_gauntlet_darkness_blackened")) {
         this.yppowers$setBlackenedByDarkness(true);
      }

      if (mob.getTarget() instanceof ServerPlayerEntity player && this.yppowers$shouldIgnore(player)) {
         mob.setTarget(null);
      } else if (mob.getTarget() != null && yppowers$sameSide(mob, mob.getTarget())) {
         mob.setTarget(null);
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void yppowers$trackIllusionistThrowIntoLava(CallbackInfo ci) {
      RitualManager.onThrownMobTick((MobEntity)(Object)this);
   }

   @Unique
   private static String yppowers$ownerTag(MobEntity mob) {
      for (String tag : mob.getCommandTags()) {
         if (tag.startsWith("yppowers_owner_")) {
            return tag;
         }
      }

      return null;
   }

   @Unique
   private static boolean yppowers$sameSide(MobEntity mob, LivingEntity target) {
      if (!(target instanceof MobEntity other)) {
         return false;
      } else if (mob.getCommandTags().contains("yppowers_ice_guard_rogue") || other.getCommandTags().contains("yppowers_ice_guard_rogue")) {
         return false;
      } else {
         String a = yppowers$ownerTag(mob);
         String b = yppowers$ownerTag(other);
         if (a == null || b == null) {
            return false;
         } else if (a.equals(b)) {
            return true;
         } else {
            MinecraftServer server = mob.getEntityWorld().getServer();
            if (server == null) {
               return false;
            } else {
               try {
                  UUID ownerA = UUID.fromString(a.substring("yppowers_owner_".length()));
                  UUID ownerB = UUID.fromString(b.substring("yppowers_owner_".length()));
                  return AllianceManager.get(server).areAllied(ownerA, ownerB);
               } catch (IllegalArgumentException var8) {
                  return false;
               }
            }
         }
      }
   }

   @Unique
   private boolean yppowers$shouldIgnore(ServerPlayerEntity player) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server == null) {
         return false;
      } else {
         MobEntity mob = (MobEntity)(Object)this;
         boolean isRogue = mob.getCommandTags().contains("yppowers_ice_guard_rogue");
         boolean hasOwnerTag = false;

         for (String tag : mob.getCommandTags()) {
            if (tag.startsWith("yppowers_owner_")) {
               hasOwnerTag = true;
               if (!isRogue) {
                  try {
                     UUID ownerId = UUID.fromString(tag.substring("yppowers_owner_".length()));
                     if (ownerId.equals(player.getUuid()) || AllianceManager.get(server).areAllied(ownerId, player.getUuid())) {
                        return true;
                     }
                  } catch (IllegalArgumentException var7) {
                  }
               }
            }
         }

         if (GraceManager.isActive(server)) {
            return hasOwnerTag || isRogue;
         } else {
            PowerManager manager = PowerManager.get(server);
            PowerType type = manager.getPowerType(player.getUuid());
            if (type == null) {
               return false;
            } else {
               Power power = manager.getPowerInstance(type);
               return power != null && power.shouldMobIgnore(player, (MobEntity)(Object)this);
            }
         }
      }
   }
}
