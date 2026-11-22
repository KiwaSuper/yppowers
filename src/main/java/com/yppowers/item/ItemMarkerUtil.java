package com.yppowers.item;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Helpers for reading and writing mod-private tags on {@link DataComponentTypes#CUSTOM_DATA}. */
public final class ItemMarkerUtil {
    private ItemMarkerUtil() {}

    public static boolean hasFlag(ItemStack stack, String key) {
        if (stack == null || stack.isEmpty()) return false;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data != null && data.copyNbt().getBoolean(key, false);
    }

    public static void setFlag(ItemStack stack, String key, boolean value) {
        if (stack == null || stack.isEmpty()) return;
        NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = existing != null ? existing.copyNbt() : new NbtCompound();
        if (value) nbt.putBoolean(key, true);
        else nbt.remove(key);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static String getString(ItemStack stack, String key, String fallback) {
        if (stack == null || stack.isEmpty()) return fallback;
        NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
        return data == null ? fallback : data.copyNbt().getString(key, fallback);
    }

    public static void setString(ItemStack stack, String key, String value) {
        if (stack == null || stack.isEmpty()) return;
        NbtComponent existing = stack.get(DataComponentTypes.CUSTOM_DATA);
        NbtCompound nbt = existing != null ? existing.copyNbt() : new NbtCompound();
        if (value == null || value.isEmpty()) nbt.remove(key);
        else nbt.putString(key, value);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }
}
