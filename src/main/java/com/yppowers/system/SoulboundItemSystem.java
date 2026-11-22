package com.yppowers.system;

import com.yppowers.core.PowerId;
import com.yppowers.item.ItemMarkerUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.Supplier;

/**
 * Unified soulbound item management: tagging, death-drop prevention, re-issuance, and removal.
 */
public final class SoulboundItemSystem {
    private static final String KEY_POWER = "YPSoulboundPower";
    private static final String KEY_TAG = "YPSoulboundTag";

    private SoulboundItemSystem() {}

    public static ItemStack mark(ItemStack stack, PowerId powerId, String tag) {
        ItemMarkerUtil.setString(stack, KEY_POWER, powerId.id());
        ItemMarkerUtil.setString(stack, KEY_TAG, tag);
        return stack;
    }

    public static boolean isSoulbound(ItemStack stack) {
        return !ItemMarkerUtil.getString(stack, KEY_POWER, "").isEmpty();
    }

    public static boolean isSoulbound(ItemStack stack, PowerId powerId) {
        return powerId != null && powerId.id().equals(ItemMarkerUtil.getString(stack, KEY_POWER, ""));
    }

    public static boolean isSoulbound(ItemStack stack, PowerId powerId, String tag) {
        return isSoulbound(stack, powerId) && tag.equals(ItemMarkerUtil.getString(stack, KEY_TAG, ""));
    }

    /** Ensures the player has a copy of the given soulbound item; creates one if missing and returns the live inventory stack. */
    public static ItemStack ensureEquippedOrPresent(ServerPlayerEntity player, PowerId powerId, String tag, Supplier<ItemStack> factory) {
        ItemStack existing = find(player, powerId, tag);
        if (!existing.isEmpty()) return existing;
        ItemStack created = mark(factory.get(), powerId, tag);
        if (player.giveItemStack(created)) {
            player.currentScreenHandler.sendContentUpdates();
            ItemStack live = find(player, powerId, tag);
            return live.isEmpty() ? created : live;
        }
        player.dropItem(created, false);
        return created;
    }

    public static ItemStack find(ServerPlayerEntity player, PowerId powerId, String tag) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (isSoulbound(s, powerId, tag)) return s;
        }
        return ItemStack.EMPTY;
    }

    /** Removes all soulbound items belonging to {@code powerId} from the player's inventory. */
    public static void stripAllForPower(ServerPlayerEntity player, PowerId powerId) {
        if (player == null || powerId == null) return;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (isSoulbound(s, powerId)) {
                inv.setStack(i, ItemStack.EMPTY);
            }
        }
        if (isSoulbound(player.currentScreenHandler.getCursorStack(), powerId)) {
            player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
        }
    }
}
