package com.yppowers.item;

import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.PowerId;
import com.yppowers.system.GraceSystem;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BooleanSupplier;

/** Common authorization helper for Thor's Riptide mace across both server and client prediction. */
public final class ThorMaceHook {
    private static volatile BooleanSupplier clientCanUse = () -> true;

    private ThorMaceHook() {}

    public static void setClientCanUsePredicate(BooleanSupplier predicate) {
        clientCanUse = predicate != null ? predicate : () -> true;
    }

    public static boolean isThorMace(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isOf(Items.MACE) && ItemMarkerUtil.hasFlag(stack, "YPThorMace");
    }

    public static boolean canUseRiptide(PlayerEntity player, ItemStack stack) {
        if (!isThorMace(stack) || player == null) return false;
        if (player instanceof ServerPlayerEntity serverPlayer) {
            if (serverPlayer.getEntityWorld().getServer() == null) return false;
            if (!PlayerDataStore.get(serverPlayer.getEntityWorld().getServer()).hasPower(serverPlayer, PowerId.THOR)) {
                return false;
            }
            return !GraceSystem.blockAbility(serverPlayer);
        }
        return clientCanUse.getAsBoolean();
    }
}
