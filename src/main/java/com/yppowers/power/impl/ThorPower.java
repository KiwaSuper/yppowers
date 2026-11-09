package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;

/**
 * Thor: a soulbound Unbreakable enchanted mace (Density 5, Wind Burst 1, Fire Aspect 2,
 * Curse of Vanishing, Mending, Unbreaking 3) with lightning on hit, lightning immunity,
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

    @Override
    public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (source.isOf(DamageTypes.LIGHTNING_BOLT)
                || source.isIn(DamageTypeTags.IS_LIGHTNING)
                || source.getAttacker() instanceof LightningEntity
                || source.getSource() instanceof LightningEntity) {
            return 0.0f;
        }
        return amount;
    }

    @Override
    public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (!isThorMace(attacker.getMainHandStack())
                || !(target instanceof LivingEntity living)
                || !YPConfig.getInstance().thor_mace_lightning_on_hit) {
            return;
        }
        if (living instanceof ServerPlayerEntity other
                && attacker.getEntityWorld().getServer() != null
                && AllianceManager.get(attacker.getEntityWorld().getServer()).isAlly(attacker, other)) {
            return;
        }
        if (attacker.getEntityWorld() instanceof ServerWorld world) {
            LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
            if (lightning != null) {
                lightning.setChanneler(attacker);
                lightning.refreshPositionAfterTeleport(living.getX(), living.getY(), living.getZ());
                world.spawnEntity(lightning);
            }
        }
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

    /** Applies Unbreakable and the fixed enchantment spec (Density 5, Wind Burst 1, Fire Aspect 2, Curse of Vanishing, Mending, Unbreaking 3). */
    private static void applyThorEnchantments(ServerPlayerEntity player, ItemStack mace) {
        for (int i = 1; i <= 4; i++) {
            YPItemData.setFlag(mace, "YPThorMode" + i, false);
        }
        mace.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        mace.setDamage(0);

        var enchRegistry = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        mace.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        EnchantmentHelper.apply(mace, builder -> {
            builder.add(enchRegistry.getOrThrow(Enchantments.DENSITY), 5);
            builder.add(enchRegistry.getOrThrow(Enchantments.WIND_BURST), 1);
            builder.add(enchRegistry.getOrThrow(Enchantments.FIRE_ASPECT), 2);
            builder.add(enchRegistry.getOrThrow(Enchantments.VANISHING_CURSE), 1);
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
