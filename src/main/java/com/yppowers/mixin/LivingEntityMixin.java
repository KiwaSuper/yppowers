package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.combat.CombatTagManager;
import com.yppowers.command.GraceManager;
import com.yppowers.energy.EnergyManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.RitualManager;
import com.yppowers.power.impl.BootsOfTheSkyPower;
import com.yppowers.power.impl.IceEmperorPower;
import com.yppowers.power.impl.IllusionistPower;
import com.yppowers.power.impl.TimeWarperPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LivingEntity.class})
public abstract class LivingEntityMixin {
   @Unique
   private DamageSource yppowers$currentDamageSource;

   @Inject(
      method = {"damage"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$captureDamageSourceAndApplyCombatRules(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      this.yppowers$currentDamageSource = source;
      LivingEntity target = (LivingEntity)(Object)this;
      if (TimeWarperPower.isEntityFrozen(target)) {
         target.timeUntilRegen = 0;
         target.hurtTime = 0;
         target.maxHurtTime = 0;
      }

      if (target instanceof ServerPlayerEntity targetPlayer && GraceManager.isActive(targetPlayer.getEntityWorld().getServer())) {
         ServerPlayerEntity attacker = GraceManager.findPlayerAttacker(source);
         if (attacker != null && attacker != targetPlayer) {
            GraceManager.notifyPvpBlocked(attacker);
            this.yppowers$currentDamageSource = null;
            cir.setReturnValue(false);
         }
      }

      if (!cir.isCancelled() && world.getServer() != null) {
         Entity attacker = source.getAttacker();
         if (attacker instanceof ProjectileEntity projectile && projectile.getOwner() != null) {
            attacker = projectile.getOwner();
         }

         if (attacker instanceof ServerPlayerEntity player) {
            RitualManager.onDamage(player, target);
         }
      }
   }

   @Inject(
      method = {"damage"},
      at = {@At("RETURN")}
   )
   private void yppowers$clearDamageSource(ServerWorld world, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
      if (cir.getReturnValueZ() && (Object)this instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
         IllusionistPower.onOwnerDamaged(player);
         ServerPlayerEntity attacker = CombatTagManager.resolvePlayerAttacker(world.getServer(), source);
         if (attacker != null && attacker != player) {
            CombatTagManager.tagPvp(attacker, player);
         }
      }

      this.yppowers$currentDamageSource = null;
   }

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

   @ModifyVariable(
      method = {"damage"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private float yppowers$modifyDamage(float amount) {
      LivingEntity target = (LivingEntity)(Object)this;
      if (target.getEntityWorld().isClient()) {
         return amount;
      } else {
         DamageSource source = this.yppowers$currentDamageSource;
         if (source == null) {
            return amount;
         } else if (target instanceof ServerPlayerEntity elytraPlayer
            && source.isOf(DamageTypes.FLY_INTO_WALL)
            && BootsOfTheSkyPower.isElytraCrashProtected(elytraPlayer)) {
            return 0.0F;
         } else if (target instanceof ServerPlayerEntity playerTarget
            && playerTarget.getEntityWorld().getServer() != null
            && PowerManager.get(playerTarget.getEntityWorld().getServer()).getPowerType(playerTarget.getUuid()) == PowerType.THOR
            && (
               source.isOf(DamageTypes.LIGHTNING_BOLT)
                  || source.isIn(DamageTypeTags.IS_LIGHTNING)
                  || source.getAttacker() instanceof LightningEntity
                  || source.getSource() instanceof LightningEntity
            )) {
            return 0.0F;
         } else if (target.getEntityWorld().getServer() != null && GraceManager.isActive(target.getEntityWorld().getServer())) {
            return Math.max(0.0F, amount);
         } else {
            Entity attackerEntity = source.getAttacker();
            if (target instanceof ServerPlayerEntity targetPlayer && IceEmperorPower.shouldGuardIgnore(attackerEntity, targetPlayer)) {
               return 0.0F;
            } else if (target instanceof ServerPlayerEntity targetPlayer
               && attackerEntity instanceof ServerPlayerEntity attackerPlayer
               && AllianceManager.get(targetPlayer.getEntityWorld().getServer()).isAlly(attackerPlayer, targetPlayer)) {
               return 0.0F;
            } else {
               if (attackerEntity instanceof ServerPlayerEntity attacker && attacker.getEntityWorld().getServer() != null) {
                  PowerManager manager = PowerManager.get(attacker.getEntityWorld().getServer());
                  PowerType type = manager.getPowerType(attacker.getUuid());
                  Power power = type == null ? null : manager.getPowerInstance(type);
                  if (power != null) {
                     amount = power.modifyOutgoingDamage(attacker, target, source, amount);
                  }
               }

               Power power = this.yppowers$getPower();
               if (power != null && target instanceof ServerPlayerEntity player) {
                  if (power.isFireImmune(player) && source.isIn(DamageTypeTags.IS_FIRE)) {
                     return 0.0F;
                  }

                  if (power.getType() == PowerType.GOAT
                     && player.getEntityWorld().getServer() != null
                     && EnergyManager.get(player.getEntityWorld().getServer()).isOverdriveActive(player.getUuid())) {
                     return Math.max(0.0F, amount);
                  }

                  amount = power.modifyIncomingDamage(player, source, amount);
               }

               return Math.max(0.0F, amount);
            }
         }
      }
   }

   @Inject(
      method = {"modifyAppliedDamage"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void yppowers$deferGoatDamageAfterMitigation(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
      if ((Object)this instanceof ServerPlayerEntity player
         && player.getEntityWorld().getServer() != null
         && !GraceManager.isActive(player.getEntityWorld().getServer())) {
         PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
         if (manager.getPowerType(player.getUuid()) == PowerType.GOAT) {
            EnergyManager energy = EnergyManager.get(player.getEntityWorld().getServer());
            if (energy.isOverdriveActive(player.getUuid())) {
               if (!source.isOf(DamageTypes.OUT_OF_WORLD) && !source.isOf(DamageTypes.GENERIC_KILL)) {
                  float mitigated = Math.max(0.0F, (Float)cir.getReturnValue());
                  float absorption = Math.max(0.0F, player.getAbsorptionAmount());
                  float absorbed = Math.min(absorption, mitigated);
                  if (absorbed > 0.0F) {
                     player.setAbsorptionAmount(absorption - absorbed);
                  }

                  float healthDamageDebt = mitigated - absorbed;
                  double deferred = healthDamageDebt > 0.0F ? energy.addGoatDamageDebt(player, healthDamageDebt) : 0.0;
                  cir.setReturnValue((float)Math.max(0.0, healthDamageDebt - deferred));
               }
            }
         }
      }
   }

   @ModifyVariable(
      method = {"handleFallDamage"},
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private float yppowers$modifyFallMultiplier(float damageMultiplier) {
      Power power = this.yppowers$getPower();
      return power != null && (Object)this instanceof ServerPlayerEntity player && !GraceManager.isActive(player.getEntityWorld().getServer())
         ? power.modifyFallDamageMultiplier(player, damageMultiplier)
         : damageMultiplier;
   }

   @Inject(
      method = {"handleFallDamage"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$handleFallDamage(double fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
      Power power = this.yppowers$getPower();
      if (power != null
         && (Object)this instanceof ServerPlayerEntity player
         && !GraceManager.isActive(player.getEntityWorld().getServer())
         && power.onFallDamage(player, (float)fallDistance, damageMultiplier)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"jump"},
      at = {@At("TAIL")}
   )
   private void yppowers$onJump(CallbackInfo ci) {
      Power power = this.yppowers$getPower();
      if (power != null && (Object)this instanceof ServerPlayerEntity player && !GraceManager.isActive(player.getEntityWorld().getServer())) {
         power.onJump(player);
      }
   }

   @Inject(
      method = {"onDeath"},
      at = {@At("TAIL")}
   )
   private void yppowers$iceGuardDeath(DamageSource source, CallbackInfo ci) {
      IceEmperorPower.onIceGuardDeath((LivingEntity)(Object)this);
   }
}
