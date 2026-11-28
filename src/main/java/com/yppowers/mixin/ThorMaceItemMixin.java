package com.yppowers.mixin;

import com.yppowers.command.GraceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.YPItemData;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
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

   @Unique
   private static boolean yppowers$canUseThorMace(ServerPlayerEntity player, boolean notify) {
      MinecraftServer server = player.getEntityWorld().getServer();
      if (server == null || !player.isAlive() || player.isRemoved()) {
         return false;
      }
      PowerManager manager = PowerManager.get(server);
      if (manager.getPowerType(player.getUuid()) != PowerType.THOR
         || !manager.isPowerEnabled(player, PowerType.THOR)
         || manager.isGrimoireSkillsetEnabled(player.getUuid())) {
         return false;
      }
      if (notify ? GraceManager.blockAbility(player) : GraceManager.isActive(server)) {
         return false;
      }
      return EnergyManager.get(server).getBleedingTicks(player.getUuid()) <= 0;
   }

   @Inject(
      method = {"use"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$thorMaceUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
      if (yppowers$isThorMace(user.getStackInHand(hand))) {
         if (!world.isClient() && user instanceof ServerPlayerEntity serverPlayer && !yppowers$canUseThorMace(serverPlayer, true)) {
            cir.setReturnValue(ActionResult.FAIL);
            return;
         }
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
         if (world.isClient()) {
            return;
         }
         if (!(player instanceof ServerPlayerEntity serverPlayer) || !yppowers$canUseThorMace(serverPlayer, true)) {
            cir.setReturnValue(false);
            return;
         }
         int charge = 72000 - remainingUseTicks;
         if (charge >= YPConfig.getInstance().thor_riptide_min_charge_ticks) {
            yppowers$launchRiptide(world, serverPlayer, stack);
            cir.setReturnValue(true);
         }
      }
   }

   @Unique
   private static void yppowers$launchRiptide(World world, ServerPlayerEntity player, ItemStack stack) {
      float strength = (float)YPConfig.getInstance().thor_riptide_strength;
      Vec3d look = player.getRotationVec(1.0F);
      player.setVelocity(look.x * strength, look.y * strength, look.z * strength);
      player.velocityDirty = true;
      ((EntityVelocityAccessor)player).invokeYppowersScheduleVelocityUpdate();
      player.useRiptide(20, 11.0F, stack);
      world.playSoundFromEntity(null, player, SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
   }
}
