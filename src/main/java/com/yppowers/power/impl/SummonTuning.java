package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PillagerEntity;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.entity.mob.VindicatorEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;

public final class SummonTuning {
   private SummonTuning() {
   }

   public static void strengthen(ServerPlayerEntity owner, MobEntity mob) {
      if (mob != null) {
         YPConfig cfg = YPConfig.getInstance();
         scale(mob, EntityAttributes.MAX_HEALTH, cfg.summon_health_multiplier);
         scale(mob, EntityAttributes.ATTACK_DAMAGE, cfg.summon_damage_multiplier);
         scale(mob, EntityAttributes.MOVEMENT_SPEED, cfg.summon_movement_multiplier);
         setAtLeast(mob, EntityAttributes.ARMOR, cfg.summon_armor_bonus);
         setAtLeast(mob, EntityAttributes.ARMOR_TOUGHNESS, cfg.summon_armor_bonus * 0.25);
         setAtLeast(mob, EntityAttributes.KNOCKBACK_RESISTANCE, cfg.summon_knockback_resistance_bonus);
         setAtLeast(mob, EntityAttributes.FOLLOW_RANGE, cfg.summon_follow_range);
         mob.setHealth(mob.getMaxHealth());
         mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, -1, 0, false, false, false));
         if (cfg.summon_regeneration_enabled) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, -1, 0, false, false, false));
         }

         if (cfg.summon_fire_resistance_enabled) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, -1, 0, false, false, false));
         }

         if (cfg.summon_equipment_upgrade_enabled) {
            upgradeEquipment(mob);
         }
      }
   }

   private static void scale(MobEntity mob, RegistryEntry<EntityAttribute> attribute, double multiplier) {
      if (multiplier > 0.0 && Double.isFinite(multiplier)) {
         EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
         if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * multiplier);
         }
      }
   }

   private static void setAtLeast(MobEntity mob, RegistryEntry<EntityAttribute> attribute, double value) {
      if (value > 0.0 && Double.isFinite(value)) {
         EntityAttributeInstance instance = mob.getAttributeInstance(attribute);
         if (instance != null) {
            instance.setBaseValue(Math.max(instance.getBaseValue(), value));
         }
      }
   }

   private static void upgradeEquipment(MobEntity mob) {
      if (mob instanceof VindicatorEntity) {
         mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_AXE));
      } else if (mob instanceof ZombieEntity) {
         mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
      } else if (mob instanceof VexEntity) {
         if (mob.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()) {
            mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
         }
      } else if (mob instanceof PillagerEntity && mob.getEquippedStack(EquipmentSlot.MAINHAND).isEmpty()) {
         mob.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.CROSSBOW));
      }

      if (mob instanceof ZombieEntity || mob instanceof VindicatorEntity || mob instanceof PillagerEntity) {
         equipIfEmpty(mob, EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
         equipIfEmpty(mob, EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
         equipIfEmpty(mob, EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
         equipIfEmpty(mob, EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
      }
   }

   private static void equipIfEmpty(MobEntity mob, EquipmentSlot slot, ItemStack stack) {
      if (mob.getEquippedStack(slot).isEmpty()) {
         mob.equipStack(slot, stack);
      }
   }
}
