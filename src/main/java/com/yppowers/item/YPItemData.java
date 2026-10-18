package com.yppowers.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Small compatibility helper for YP Powers item flags stored in 1.20.5+ custom-data components. */
public final class YPItemData {
    private YPItemData() {}

    public static boolean hasFlag(ItemStack stack, String key) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData != null && customData.copyNbt().getBoolean(key, false);
    }

    public static void setFlag(ItemStack stack, String key, boolean value) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        nbt.putBoolean(key, value);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }
}
