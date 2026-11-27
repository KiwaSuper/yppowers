package com.yppowers.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

public final class YPItemData {
   private YPItemData() {
   }

   public static boolean hasFlag(ItemStack stack, String key) {
      NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
      return customData != null && customData.copyNbt().getBoolean(key, false);
   }

   public static void setFlag(ItemStack stack, String key, boolean value) {
      NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
      nbt.putBoolean(key, value);
      stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
   }

   public static int getInt(ItemStack stack, String key, int fallback) {
      NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
      return customData == null ? fallback : customData.copyNbt().getInt(key, fallback);
   }

   public static void setInt(ItemStack stack, String key, int value) {
      NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
      nbt.putInt(key, value);
      stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
   }
}
