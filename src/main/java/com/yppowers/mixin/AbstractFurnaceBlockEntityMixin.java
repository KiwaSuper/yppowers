package com.yppowers.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.FuelRegistry;
import com.yppowers.item.YPItemData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {
    @Inject(method = "getFuelTime", at = @At("HEAD"), cancellable = true)
    private void yppowers$infiniteEmberFuel(FuelRegistry fuelRegistry, ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        if (YPItemData.hasFlag(fuel, "YPSoulbound")) cir.setReturnValue(Integer.MAX_VALUE);
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;decrement(I)V"))
    private static void yppowers$preserveEmber(ItemStack stack, int amount) {
        if (!YPItemData.hasFlag(stack, "YPSoulbound")) stack.decrement(amount);
    }
}
