package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.item.YPItemData;
import com.yppowers.power.PowerType;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.Entity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.util.Identifier;

public class PoseidonPower extends Power {
    private final Map<UUID, Integer> swapCooldowns = new HashMap<>();
    private static final Identifier LAND_SPEED_ID = Identifier.of("yppowers", "poseidon_land_weakness");

    @Override
    public PowerType getType() {
        return PowerType.POSEIDON;
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (swapCooldowns.containsKey(uuid)) {
            int cd = swapCooldowns.get(uuid) - 1;
            if (cd <= 0) swapCooldowns.remove(uuid);
            else swapCooldowns.put(uuid, cd);
        }

        if (player.isTouchingWater() || player.isSubmergedInWater()) {
            removeLandPenalty(player);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 40, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 40, 0, false, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 40, 0, false, false, true));
        } else {
            applyLandPenalty(player);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false, true));
            player.removeStatusEffect(StatusEffects.WATER_BREATHING);
            player.removeStatusEffect(StatusEffects.DOLPHINS_GRACE);
            player.removeStatusEffect(StatusEffects.REGENERATION);
        }
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
        boolean meleeOrTrident = source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.TRIDENT);
        if (meleeOrTrident && (player.isTouchingWater() || player.isSubmergedInWater())) {
            return amount * 1.2f;
        }
        return amount;
    }

    private void applyLandPenalty(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(LAND_SPEED_ID) == null) {
            speed.addPersistentModifier(new EntityAttributeModifier(LAND_SPEED_ID, -0.10, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private void removeLandPenalty(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(LAND_SPEED_ID);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        removeLandPenalty(player);
        player.removeStatusEffect(StatusEffects.WATER_BREATHING);
        player.removeStatusEffect(StatusEffects.DOLPHINS_GRACE);
        player.removeStatusEffect(StatusEffects.REGENERATION);
        player.removeStatusEffect(StatusEffects.WEAKNESS);
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        handleTridentSwap(player);
    }

    @Override
    public void onAlternateActivate(ServerPlayerEntity player) {
        handleTridentSwap(player);
    }

    private void handleTridentSwap(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (swapCooldowns.getOrDefault(uuid, 0) > 0) return;

        ItemStack trident = player.getMainHandStack();
        if (!trident.isOf(Items.TRIDENT) || !YPItemData.hasFlag(trident, "YPPoseidonTrident")) return;

        // Read the real stack state: the per-session cache is lost on reconnect, while the
        // enchantment component persists. This keeps the toggle correct after relogging.
        boolean currentlyRiptide = EnchantmentHelper.getLevel(
                player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.RIPTIDE), trident) > 0;
        boolean newMode = !currentlyRiptide;
        
        trident.set(net.minecraft.component.DataComponentTypes.ENCHANTMENTS, net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);

        if (newMode) {
            EnchantmentHelper.apply(trident, builder -> builder.add(
                    player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.RIPTIDE), 3));
            player.sendMessage(Text.literal("Trident: Riptide III").formatted(Formatting.AQUA), true);
        } else {
            EnchantmentHelper.apply(trident, builder -> {
                builder.add(player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.LOYALTY), 3);
                builder.add(player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.CHANNELING), 1);
            });
            player.sendMessage(Text.literal("Trident: Loyalty III + Channeling").formatted(Formatting.GOLD), true);
        }

        swapCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().poseidon_trident_swap_cooldown_ticks));
        player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureTrident(player);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        ensureTrident(player);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        ensureTrident(player);
    }

    private void ensureTrident(ServerPlayerEntity player) {
        boolean hasTrident = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.TRIDENT) && YPItemData.hasFlag(stack, "YPPoseidonTrident")) {
                hasTrident = true;
                break;
            }
        }
        if (!hasTrident) {
            ItemStack trident = new ItemStack(Items.TRIDENT);
            EnchantmentHelper.apply(trident, builder -> builder.add(
                        player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.RIPTIDE), 3));
            YPItemData.setFlag(trident, "YPPoseidonTrident", true);
            trident.set(net.minecraft.component.DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            player.giveOrDropStack(trident);
        }
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return (stack.isOf(Items.TRIDENT) && YPItemData.hasFlag(stack, "YPPoseidonTrident"))
                || stack.isOf(Items.SHIELD);
    }
}
