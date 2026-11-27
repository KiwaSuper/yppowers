package com.yppowers.mixin;

import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItemData;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Item.class})
public abstract class ThorMaceItemMixin {
   @Unique
   private static boolean yppowers$isThorMace(ItemStack stack) {
      return stack.isOf(Items.MACE) && YPItemData.hasFlag(stack, "YPThorMace");
   }

   @Inject(
      method = {"use"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$thorMaceUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (yppowers$isThorMace(user.getStackInHand(hand))) {
         user.setCurrentHand(hand);
         cir.setReturnValue(ActionResult.CONSUME);
      }
   }

   @Inject(
      method = {"getMaxUseTime"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$thorMaceMaxUseTime(ItemStack stack, LivingEntity user, CallbackInfoReturnable<Integer> cir) {
      if (yppowers$isThorMace(stack)) {
         cir.setReturnValue(72000);
      }
   }

   @Inject(
      method = {"getUseAction"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$thorMaceUseAction(ItemStack stack, CallbackInfoReturnable<UseAction> cir) {
      if (yppowers$isThorMace(stack)) {
         cir.setReturnValue(UseAction.TRIDENT);
      }
   }

   @Inject(
      method = {"onStoppedUsing"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$thorMaceOnStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks, CallbackInfoReturnable<Boolean> cir) {
      if (yppowers$isThorMace(stack) && user instanceof PlayerEntity player) {
         int charge = 72000 - remainingUseTicks;
         if (charge >= YPConfig.getInstance().thor_riptide_min_charge_ticks) {
            yppowers$launchRiptide(world, player, stack);
            cir.setReturnValue(true);
         }
      }
   }

   @Unique
   private static void yppowers$launchRiptide(World world, PlayerEntity player, ItemStack stack) {
      float strength = (float)YPConfig.getInstance().thor_riptide_strength;
      Vec3d look = player.getRotationVec(1.0F);
      player.setVelocity(look.x * strength, look.y * strength, look.z * strength);
      player.velocityDirty = true;
      player.useRiptide(20, 11.0F, stack);
      world.playSoundFromEntity(null, player, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
   }
}
