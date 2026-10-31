package com.yppowers.power.impl;

import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Thor: a soulbound enchanted mace (Density 5, Wind Burst 1, Mending, Unbreaking 3),
 * hold-and-release right-click Riptide III anywhere, and permanent Speed II.
 */
public class ThorPower extends Power {
    public static final String MACE_FLAG = "YPThorMace";
    private static final int SPEED_AMPLIFIER = 1; // Speed II

    @Override
    public PowerType getType() {
        return PowerType.THOR;
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureMace(player);
        ensureSpeed(player);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        ensureMace(player);
        ensureSpeed(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        ensureMace(player);
        ensureSpeed(player);
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        ensureSpeed(player);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        removeManagedSpeed(player);
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return isThorMace(stack);
    }

    public static boolean isThorMace(ItemStack stack) {
        return stack.isOf(Items.MACE) && YPItemData.hasFlag(stack, MACE_FLAG);
    }

    /** Builds the enchanted Thor mace stack. */
    public static ItemStack createThorMace(ServerPlayerEntity player) {
        ItemStack mace = new ItemStack(Items.MACE);
        YPItemData.setFlag(mace, MACE_FLAG, true);
        applyThorEnchantments(player, mace);
        return mace;
    }

    /** Applies the fixed enchantment spec (Density 5, Wind Burst 1, Mending 1, Unbreaking 3). */
    private static void applyThorEnchantments(ServerPlayerEntity player, ItemStack mace) {
        for (int i = 1; i <= 4; i++) {
            YPItemData.setFlag(mace, "YPThorMode" + i, false);
        }
        mace.remove(DataComponentTypes.UNBREAKABLE);

        var enchRegistry = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        mace.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        EnchantmentHelper.apply(mace, builder -> {
            builder.add(enchRegistry.getOrThrow(Enchantments.DENSITY), 5);
            builder.add(enchRegistry.getOrThrow(Enchantments.WIND_BURST), 1);
            builder.add(enchRegistry.getOrThrow(Enchantments.MENDING), 1);
            builder.add(enchRegistry.getOrThrow(Enchantments.UNBREAKING), 3);
        });
    }

    private void ensureMace(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isThorMace(stack)) {
                applyThorEnchantments(player, stack);
                return;
            }
        }
        player.giveOrDropStack(createThorMace(player));
    }

    private void ensureSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current == null || current.getAmplifier() < SPEED_AMPLIFIER) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, StatusEffectInstance.INFINITE, SPEED_AMPLIFIER, false, false, true));
        }
    }

    private void removeManagedSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current != null && current.getDuration() == StatusEffectInstance.INFINITE
                && current.getAmplifier() == SPEED_AMPLIFIER) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
    }

    public static boolean isAuthorizedHeavyCoreRemoval(ServerWorld world, BlockPos pos) {
        return false;
    }

    public static boolean isProtectedHeavyCore(ServerWorld world, BlockPos pos) {
        return false;
    }
}
