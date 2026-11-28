package com.yppowers.mixin;

import com.yppowers.power.teleportgambit.TeleportGambitDomainManager;
import com.yppowers.power.thor.ThorPower;
import com.yppowers.power.timewarper.TimeWarperDomainManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ServerPlayerInteractionManager.class})
public abstract class ServerPlayerInteractionManagerMixin {
   @Shadow
   @Final
   protected ServerPlayerEntity player;

   @Inject(
      method = {"tryBreakBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$protectThorHeavyCore(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      ServerWorld var4 = this.player.getEntityWorld();
      if (var4 instanceof ServerWorld && TeleportGambitDomainManager.isProtectedChargeFrame(var4, pos)) {
         cir.setReturnValue(false);
      } else if (!TimeWarperDomainManager.mayModifyBlock(this.player, pos)) {
         cir.setReturnValue(false);
      } else {
         var4 = this.player.getEntityWorld();
         if (var4 instanceof ServerWorld && ThorPower.isProtectedHeavyCore(var4, pos)) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"interactBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$protectTimeDomainStorage(
      ServerPlayerEntity player, World world, ItemStack stack, Hand hand, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir
   ) {
      if (world instanceof ServerWorld serverWorld
         && TimeWarperDomainManager.isTimeDomain(serverWorld)
         && !TimeWarperDomainManager.mayModifyBlock(player, hit.getBlockPos())) {
         cir.setReturnValue(ActionResult.FAIL);
      }
   }
}
