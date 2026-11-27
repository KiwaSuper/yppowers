package com.yppowers.mixin;

import com.yppowers.item.GauntletOfDarknessManager;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({Slot.class})
public abstract class SlotMixin {
   @Shadow
   @Final
   public Inventory inventory;

   @Inject(
      method = {"canInsert"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$restrictGauntletStorage(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
      if (GauntletOfDarknessManager.isGauntlet(stack) && !GauntletOfDarknessManager.isAllowedStorageInventory(this.inventory)) {
         cir.setReturnValue(false);
      }
   }
}
