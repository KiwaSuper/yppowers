package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.VanillaMeleeTracker;
import com.yppowers.power.illusionist.IllusionistPower;
import com.yppowers.power.timewarper.TimeWarperPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({PlayerEntity.class})
public abstract class PlayerEntityMixin {
   @Unique
   private Power yppowers$getPower() {
      if ((Object)this instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         PowerType type = manager.getPowerType(player.getUuid());
         return type == null ? null : manager.getPowerInstance(type);
      } else {
         return null;
      }
   }

   @Inject(
      method = {"takeShieldHit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$preventShieldDisable(ServerWorld world, LivingEntity attacker, CallbackInfo ci) {
      if ((Object)this instanceof ServerPlayerEntity player) {
         Power power = this.yppowers$getPower();
         if (power != null && power.shouldPreventShieldDisable(player)) {
            ci.cancel();
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = {@At("TAIL")}
   )
   private void yppowers$collectTimeShards(CallbackInfo ci) {
      if ((Object)this instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
         TimeWarperPower.tryCollectTimeShard(player);
      }
   }

   @Inject(
      method = {"attack"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$onAttack(Entity target, CallbackInfo ci) {
      if ((Object)this instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
         if (!GraceManager.isActive(player.getEntityWorld().getServer())
            && this.yppowers$getPower() instanceof IllusionistPower illusionist
            && illusionist.tryThrowGripOnAttack(player)) {
            ci.cancel();
            return;
         }

         if (target instanceof ServerPlayerEntity && GraceManager.isActive(player.getEntityWorld().getServer())) {
            GraceManager.notifyPvpBlocked(player);
            ci.cancel();
            return;
         }

         if (target instanceof ServerPlayerEntity targetPlayer && AllianceManager.get(player.getEntityWorld().getServer()).isAlly(player, targetPlayer)) {
            ci.cancel();
            return;
         }

         VanillaMeleeTracker.begin(player);
      }
   }

   @Inject(
      method = {"attack"},
      at = {@At("TAIL")}
   )
   private void yppowers$afterAttack(Entity target, CallbackInfo ci) {
      if ((Object)this instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
         VanillaMeleeTracker.end(player);
         if (GraceManager.isActive(player.getEntityWorld().getServer())) {
            return;
         }

         Power power = this.yppowers$getPower();
         if (power != null) {
            power.onAttack(player, target);
         }
      }
   }
}
