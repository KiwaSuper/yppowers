package com.yppowers.mixin;

import com.yppowers.power.poseidon.PoseidonPower;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({HungerManager.class})
public abstract class HungerManagerMixin {
   @Shadow
   private float exhaustion;

   @Inject(
      method = {"update"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$preventPoseidonHungerDepletion(ServerPlayerEntity player, CallbackInfo ci) {
      if (PoseidonPower.shouldPreventHungerDepletion(player)) {
         this.exhaustion = 0.0F;
         ci.cancel();
      }
   }
}
