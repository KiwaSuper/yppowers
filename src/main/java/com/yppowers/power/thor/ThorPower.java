package com.yppowers.power.thor;
import com.yppowers.core.AbilityKey;
import com.yppowers.core.HudPill;
import com.yppowers.system.EpicVfx;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.item.YPItemData;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import com.yppowers.system.PowerCombatUtil;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
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
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;

public class ThorPower extends Power {
   public static final String MACE_FLAG = "YPThorMace";
   private static final int SPEED_AMPLIFIER = 1;

   @Override
   public PowerType getType() {
      return PowerType.THOR;
   }

   @Override
   public void onAssign(ServerPlayerEntity player) {
      this.ensureMace(player);
      this.ensureSpeed(player);
   }

   @Override
   public void onPlayerJoin(ServerPlayerEntity player) {
      this.ensureMace(player);
      this.ensureSpeed(player);
   }

   @Override
   public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
      this.ensureMace(player);
      this.ensureSpeed(player);
   }

   @Override
   public void tick(ServerPlayerEntity player) {
      this.ensureSpeed(player);
   }

   @Override
   public void onRemove(ServerPlayerEntity player) {
      this.removeManagedSpeed(player);
   }

   @Override
   public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
      return isThorMace(stack);
   }

   @Override
   public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
      return !source.isOf(DamageTypes.LIGHTNING_BOLT)
            && !source.isIn(DamageTypeTags.IS_LIGHTNING)
            && !(source.getAttacker() instanceof LightningEntity)
            && !(source.getSource() instanceof LightningEntity)
         ? amount
         : 0.0F;
   }

   @Override
   public void onAttack(ServerPlayerEntity attacker, Entity target) {
      if (isThorMace(attacker.getMainHandStack())
         && target instanceof LivingEntity living
         && YPConfig.getInstance().thor_mace_lightning_on_hit
         && PowerCombatUtil.canHarm(attacker, living)) {
         ServerWorld world = attacker.getEntityWorld();
         if (world != null) {
            LightningEntity lightningx = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
            if (lightningx != null) {
               lightningx.setCosmetic(true);
               lightningx.setChanneler(attacker);
               lightningx.refreshPositionAfterTeleport(living.getX(), living.getY(), living.getZ());
               world.spawnEntity(lightningx);
            }
            living.damage(world, world.getDamageSources().indirectMagic(attacker, attacker), 5.0F);
            EpicVfx.impact(world, living.getX(), living.getBodyY(0.5), living.getZ(), 0.75);
            EpicVfx.cue(world, attacker.getId(), "thor", "lightning_hit", living.getX(), living.getBodyY(0.5), living.getZ(), null, 0.85F, 16, 0.0F);
         }
      }
   }

   public static boolean isThorMace(ItemStack stack) {
      return stack.isOf(Items.MACE) && YPItemData.hasFlag(stack, "YPThorMace");
   }

   public static ItemStack createThorMace(ServerPlayerEntity player) {
      ItemStack mace = new ItemStack(Items.MACE);
      YPItemData.setFlag(mace, "YPThorMace", true);
      applyThorEnchantments(player, mace);
      return mace;
   }

   private static void applyThorEnchantments(ServerPlayerEntity player, ItemStack mace) {
      for (int i = 1; i <= 4; i++) {
         YPItemData.setFlag(mace, "YPThorMode" + i, false);
      }

      mace.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
      mace.setDamage(0);
      Registry<Enchantment> enchRegistry = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
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

   private boolean ensureMace(ServerPlayerEntity player) {
      for (int i = 0; i < player.getInventory().size(); i++) {
         ItemStack stack = player.getInventory().getStack(i);
         if (isThorMace(stack)) {
            applyThorEnchantments(player, stack);
            return false;
         }
      }

      player.giveOrDropStack(createThorMace(player));
      return true;
   }

   @Override
   public List<String> ensureSpecialGear(ServerPlayerEntity player) {
      return this.ensureMace(player) ? List.of("Mjolnir") : List.of();
   }

   private void ensureSpeed(ServerPlayerEntity player) {
      StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
      if (current == null || current.getAmplifier() < 1) {
         player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, -1, 1, false, false, true));
      }
   }

   private void removeManagedSpeed(ServerPlayerEntity player) {
      StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
      if (current != null && current.getDuration() == -1 && current.getAmplifier() == 1) {
         player.removeStatusEffect(StatusEffects.SPEED);
      }
   }

   public static boolean isAuthorizedHeavyCoreRemoval(ServerWorld world, BlockPos pos) {
      return false;
   }

   public static boolean isProtectedHeavyCore(ServerWorld world, BlockPos pos) {
      return false;
   }

   @Override
   public String hudStatusChip(ServerPlayerEntity player) {
      return isThorMace(player.getMainHandStack()) ? "STORM READY" : "";
   }

   @Override
   public List<HudPill> hudPills(ServerPlayerEntity player, double energy) {
      YPConfig cfg = YPConfig.getInstance();
      return List.of(
         HudPill.ability(AbilityKey.PRIMARY, "Hold RMB Riptide", 0, 1, energy >= cfg.thor_riptide_energy_cost)
      );
   }
}
