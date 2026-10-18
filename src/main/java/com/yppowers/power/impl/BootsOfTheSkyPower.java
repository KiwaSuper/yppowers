package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BootsOfTheSkyPower extends Power {
    private final Map<UUID, Integer> flightTicksRemaining = new HashMap<>();
    private final Map<UUID, Integer> rechargeTicks = new HashMap<>();
    private final Map<UUID, Boolean> flightActive = new HashMap<>();

    @Override
    public PowerType getType() {
        return PowerType.BOOTS_SKY;
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        giveBoots(player);
        flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
    }

    @Override
    public void onPlayerJoin(ServerPlayerEntity player) {
        giveBoots(player);
        flightTicksRemaining.putIfAbsent(player.getUuid(), YPConfig.getInstance().boots_flight_duration_ticks);
    }

    @Override
    public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        flightActive.put(player.getUuid(), false);
        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
        giveBoots(player);
    }

    private void giveBoots(ServerPlayerEntity player) {
        boolean hasBoots = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(stack, "YPSkyBoots")) {
                hasBoots = true;
                break;
            }
        }
        if (!hasBoots) {
            ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
            YPItemData.setFlag(boots, "YPSkyBoots", true);
            boots.set(net.minecraft.component.DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            EnchantmentHelper.apply(boots, builder -> builder.add(
                    player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4));
            player.giveOrDropStack(boots);
        }
    }

    private boolean hasSkyBootsEquipped(ServerPlayerEntity player) {
        ItemStack feet = player.getEquippedStack(EquipmentSlot.FEET);
        return feet.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(feet, "YPSkyBoots");
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return stack.isOf(Items.NETHERITE_BOOTS) && YPItemData.hasFlag(stack, "YPSkyBoots");
    }

    @Override
    public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID pId = player.getUuid();
        if (rechargeTicks.getOrDefault(pId, 0) > 0) {
            player.sendMessage(Text.literal("Flight recharging! " + (rechargeTicks.get(pId) / 20) + " seconds remaining."), true);
            return;
        }

        boolean active = !flightActive.getOrDefault(pId, false);
        if (active && !hasSkyBootsEquipped(player)) {
            player.sendMessage(Text.literal("Equip the Boots of the Sky to fly."), true);
            return;
        }
        flightActive.put(pId, active);
        if (active) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        } else {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
        }
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        UUID pId = player.getUuid();
        if (flightActive.getOrDefault(pId, false)) {
            // Equipping these marked boots is required while using the power.
            if (!hasSkyBootsEquipped(player)) {
                flightActive.put(pId, false);
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
                player.sendMessage(Text.literal("Flight deactivated: Boots of the Sky are not equipped."), true);
            } else if (!player.getAbilities().flying) {
                // Vanilla's double-tap jump disables abilities.flying. Treat that transition as toggle-off.
                flightActive.put(pId, false);
                player.getAbilities().allowFlying = false;
                player.sendAbilitiesUpdate();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
                player.sendMessage(Text.literal("Flight deactivated."), true);
            }
        }

        if (flightActive.getOrDefault(pId, false)) {
            int ticks = flightTicksRemaining.getOrDefault(pId, YPConfig.getInstance().boots_flight_duration_ticks);
            ticks--;
            flightTicksRemaining.put(pId, ticks);

            if (ticks % 200 == 0) {
                player.sendMessage(Text.literal("Flight Time Remaining: " + (ticks / 20) + "s"), true);
            }

            ((ServerWorld) player.getEntityWorld()).spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() - 0.2, player.getZ(), 1, 0.2, 0, 0.2, 0);

            if (ticks <= 0) {
                flightActive.put(pId, false);
                player.getAbilities().allowFlying = false;
                player.getAbilities().flying = false;
                player.sendAbilitiesUpdate();
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
                rechargeTicks.put(pId, YPConfig.getInstance().boots_recharge_duration_ticks); // 3 minutes by default
                player.sendMessage(Text.literal("Flight depleted! Recharging..."), true);
            }
        }

        if (rechargeTicks.getOrDefault(pId, 0) > 0) {
            int recharge = rechargeTicks.get(pId) - 1;
            rechargeTicks.put(pId, recharge);
            if (recharge <= 0) {
                flightTicksRemaining.put(pId, YPConfig.getInstance().boots_flight_duration_ticks);
                player.sendMessage(Text.literal("Flight recharged!"), true);
                player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0f, 1.0f);
            }
        }
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        if (flightActive.getOrDefault(player.getUuid(), false)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 80, 0, false, false));
        }
        flightActive.put(player.getUuid(), false);
        flightTicksRemaining.remove(player.getUuid());
        rechargeTicks.remove(player.getUuid());
        player.getAbilities().allowFlying = false;
        player.getAbilities().flying = false;
        player.sendAbilitiesUpdate();
    }
}
