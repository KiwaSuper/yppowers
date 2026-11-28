package com.yppowers.mixin;

import com.yppowers.artifact.GrimoireOfLightManager;
import com.yppowers.item.YPItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ItemEntity.class})
public abstract class GrimoireItemEntityMixin {
   @Inject(
      method = {"tick"},
      at = {@At("HEAD")}
   )
   private void yppowers$emitGrimoireLight(CallbackInfo ci) {
      ItemEntity itemEntity = (ItemEntity)(Object)this;
      if (itemEntity.getStack().isOf(YPItems.GRIMOIRE_OF_LIGHT)) {
         if (itemEntity.getEntityWorld() instanceof ServerWorld world && world.getTime() % 10L == 0L) {
            GrimoireOfLightManager.emitLightParticles(world, itemEntity.getX(), itemEntity.getY() + 0.15, itemEntity.getZ(), 2);
         }
      }
   }
}
