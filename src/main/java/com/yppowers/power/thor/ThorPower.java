package com.yppowers.power.thor;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.item.ItemMarkerUtil;
import com.yppowers.item.ThorMaceHook;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.SoulboundItemSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;

import java.util.List;

/**
 * Thor: Unbreakable soulbound mace with hold-right-click Riptide III anywhere,
 * lightning on melee hit, lightning immunity, and permanent Speed II.
 */
public final class ThorPower extends Power {
    public static final String MACE_FLAG = "YPThorMace";
    private static final String SOULBOUND_TAG = "thor_mace";
    private static final int SPEED_AMPLIFIER = 1;

    @Override
    public PowerId id() {
        return PowerId.THOR;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of();
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        return "Riptide Mace • Speed II";
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureMace(player);
        ensureSpeed(player);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        ensureMace(player);
        ensureSpeed(player);
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        ensureMace(newPlayer);
        ensureSpeed(newPlayer);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        ensureSpeed(player);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        removeManagedSpeed(player);
    }

    @Override
    public boolean isLightningImmune(ServerPlayerEntity player) {
        return true;
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return ThorMaceHook.isThorMace(stack);
    }

    @Override
    public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (!YPConfig.get().powers.thor.lightningOnHit) return;
        if (!ThorMaceHook.isThorMace(attacker.getMainHandStack())) return;
        if (!(target instanceof LivingEntity living) || !CombatUtil.canHarm(attacker, living)) return;
        if (attacker.getEntityWorld() instanceof ServerWorld world) {
            LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
            if (lightning != null) {
                lightning.setChanneler(attacker);
                lightning.refreshPositionAfterTeleport(living.getX(), living.getY(), living.getZ());
                world.spawnEntity(lightning);
                VfxSystem.burst(world, ParticleTypes.ELECTRIC_SPARK, living.getEntityPos().add(0, 1.0, 0), 14, 0.5, 0.15);
            }
        }
    }

    private void ensureMace(ServerPlayerEntity player) {
        ItemStack stack = SoulboundItemSystem.ensureEquippedOrPresent(player, PowerId.THOR, SOULBOUND_TAG, () -> {
            ItemStack mace = new ItemStack(Items.MACE);
            ItemMarkerUtil.setFlag(mace, MACE_FLAG, true);
            applyEnchantments(player, mace);
            return mace;
        });
        applyEnchantments(player, stack);
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void applyEnchantments(ServerPlayerEntity player, ItemStack mace) {
        PowerConfigs.Thor cfg = YPConfig.get().powers.thor;
        ItemMarkerUtil.setFlag(mace, MACE_FLAG, true);
        mace.remove(DataComponentTypes.CUSTOM_NAME);
        mace.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        mace.setDamage(0);

        var reg = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        mace.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        EnchantmentHelper.apply(mace, builder -> {
            if (cfg.maceDensityLevel > 0) builder.add(reg.getOrThrow(Enchantments.DENSITY), cfg.maceDensityLevel);
            if (cfg.maceWindBurstLevel > 0) builder.add(reg.getOrThrow(Enchantments.WIND_BURST), cfg.maceWindBurstLevel);
            if (cfg.maceFireAspectLevel > 0) builder.add(reg.getOrThrow(Enchantments.FIRE_ASPECT), cfg.maceFireAspectLevel);
            builder.add(reg.getOrThrow(Enchantments.VANISHING_CURSE), 1);
            builder.add(reg.getOrThrow(Enchantments.MENDING), 1);
            if (cfg.maceUnbreakingLevel > 0) builder.add(reg.getOrThrow(Enchantments.UNBREAKING), cfg.maceUnbreakingLevel);
        });
    }

    private static void ensureSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current == null || current.getAmplifier() < SPEED_AMPLIFIER) {
            player.addStatusEffect(new StatusEffectInstance(
                    StatusEffects.SPEED, StatusEffectInstance.INFINITE, SPEED_AMPLIFIER, false, false, true));
        }
    }

    private static void removeManagedSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        if (current != null && current.getDuration() == StatusEffectInstance.INFINITE
                && current.getAmplifier() == SPEED_AMPLIFIER) {
            player.removeStatusEffect(StatusEffects.SPEED);
        }
    }
}
