package com.yppowers.mixin;

import com.yppowers.progression.RitualManager;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Item.class})
public abstract class PotionItemMixin {
   @Inject(
      method = {"finishUsing"},
      at = {@At("TAIL")}
   )
   private void yppowers$startFireGodPotionTrial(ItemStack stack, World world, LivingEntity user, CallbackInfoReturnable<ItemStack> cir) {
      if (world instanceof ServerWorld && user instanceof ServerPlayerEntity player && stack.isOf(Items.POTION)) {
         PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
         if (contents != null) {
            for (StatusEffectInstance effect : contents.getEffects()) {
               if (effect.getEffectType().equals(StatusEffects.FIRE_RESISTANCE)) {
                  RitualManager.beginFireGodTrial(player);
                  return;
               }
            }
         }
      }
   }
}
