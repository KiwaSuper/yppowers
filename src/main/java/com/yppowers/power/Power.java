package com.yppowers.power;

import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class Power {
   public abstract PowerType getType();

   public void onPrimaryActivate(ServerPlayerEntity player) {
   }

   public void onSecondaryActivate(ServerPlayerEntity player) {
   }

   public void onTertiaryActivate(ServerPlayerEntity player) {
   }

   public void onQuaternaryActivate(ServerPlayerEntity player) {
   }

   public void onAlternateActivate(ServerPlayerEntity player) {
   }

   public boolean canUseKey(ServerPlayerEntity player, int keyType) {
      return true;
   }

   public void tick(ServerPlayerEntity player) {
   }

   public void onAssign(ServerPlayerEntity player) {
   }

   public void onRemove(ServerPlayerEntity player) {
   }

   public void onPlayerJoin(ServerPlayerEntity player) {
   }

   public void onPlayerDisconnect(ServerPlayerEntity player) {
   }

   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
   }

   public void onPlayerDeath(ServerPlayerEntity player) {
   }

   public void onAttack(ServerPlayerEntity attacker, Entity target) {
   }

   public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
      return amount;
   }

   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, float amount) {
      return amount;
   }

   public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
      return this.modifyOutgoingDamage(player, target, amount);
   }

   public boolean onFallDamage(ServerPlayerEntity player, float fallDistance, float damageMultiplier) {
      return false;
   }

   public float modifyFallDamageMultiplier(ServerPlayerEntity player, float damageMultiplier) {
      return damageMultiplier;
   }

   public void onJump(ServerPlayerEntity player) {
   }

   public boolean isFireImmune(ServerPlayerEntity player) {
      return false;
   }

   public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
      return false;
   }

   public boolean shouldPreventShieldDisable(ServerPlayerEntity player) {
      return false;
   }

   public boolean shouldMobIgnore(ServerPlayerEntity player, MobEntity mob) {
      return false;
   }

   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      return List.of();
   }

   public void clearCooldowns(UUID playerId) {
   }
}
