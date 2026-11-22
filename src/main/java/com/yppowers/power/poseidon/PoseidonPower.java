package com.yppowers.power.poseidon;

import com.yppowers.YPPowersMod;
import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.item.ItemMarkerUtil;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.FreezeSystem;
import com.yppowers.system.SoulboundItemSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Poseidon: water dominance passives (Dolphin's Grace, Water Breathing, Regen V, Haste/Res/Strength,
 * hunger protection, +20% water combat), land penalties, mode-swapping soulbound Trident (G/R), and Tsunami (H).
 */
public final class PoseidonPower extends Power {
    private static final String CD_SWAP = "poseidon_swap";
    private static final String CD_TSUNAMI = "poseidon_tsunami";
    private static final String TRIDENT_TAG = "poseidon_trident";
    private static final String KEY_RIPTIDE = "YPPoseidonRiptide";
    private static final Identifier MOD_LAND_SLOW = Identifier.of(YPPowersMod.MOD_ID, "poseidon_land_slow");

    private static final class ActiveTsunami {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d origin;
        final double maxRadius;
        final int maxTicks;
        final double damage;
        final int stunTicks;
        int tick;
        final Set<UUID> hitEntities = new HashSet<>();

        ActiveTsunami(UUID ownerId, ServerWorld world, Vec3d origin, double maxRadius, int maxTicks, double damage, int stunTicks) {
            this.ownerId = ownerId;
            this.world = world;
            this.origin = origin;
            this.maxRadius = maxRadius;
            this.maxTicks = maxTicks;
            this.damage = damage;
            this.stunTicks = stunTicks;
        }
    }

    private final List<ActiveTsunami> activeWaves = new ArrayList<>();

    @Override
    public PowerId id() {
        return PowerId.POSEIDON;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Trident Mode", CD_SWAP),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Tsunami", CD_TSUNAMI),
                AbilitySpec.utility(AbilitySlot.ALTERNATE, "Swap Mode")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        ItemStack trident = SoulboundItemSystem.find(player, PowerId.POSEIDON, TRIDENT_TAG);
        String mode = !trident.isEmpty() && ItemMarkerUtil.hasFlag(trident, KEY_RIPTIDE) ? "Riptide" : "Loyalty";
        return (meetsWaterCondition(player) ? "In Water" : "On Land") + " • " + mode;
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        return (slot == AbilitySlot.PRIMARY || slot == AbilitySlot.ALTERNATE) ? 0.0 : super.energyCost(player, slot);
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        if (slot == AbilitySlot.PRIMARY || slot == AbilitySlot.ALTERNATE) {
            return cds.checkReady(player, CD_SWAP, "Trident Swap");
        }
        if (slot == AbilitySlot.SECONDARY) {
            if (!cds.checkReady(player, CD_TSUNAMI, "Tsunami")) return false;
            if (!hasNearbyWater(player)) {
                player.sendMessage(Text.literal("Tsunami requires water nearby!").formatted(Formatting.RED), true);
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.PRIMARY || slot == AbilitySlot.ALTERNATE) {
            toggleTridentMode(player);
        } else if (slot == AbilitySlot.SECONDARY) {
            launchTsunami(player);
        }
    }

    private void toggleTridentMode(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        ItemStack trident = ensureTrident(player);
        boolean riptide = !ItemMarkerUtil.hasFlag(trident, KEY_RIPTIDE);
        ItemMarkerUtil.setFlag(trident, KEY_RIPTIDE, riptide);
        applyTridentEnchants(player, trident, riptide);
        CooldownSystem.get(server).set(player.getUuid(), CD_SWAP, YPConfig.get().powers.poseidon.tridentSwapCooldownTicks);
        if (player.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 0.9f, riptide ? 1.3f : 0.9f);
        }
        player.sendMessage(Text.literal("Trident Mode: " + (riptide ? "Riptide III" : "Loyalty III + Channeling"))
                .formatted(Formatting.AQUA), true);
    }

    private void launchTsunami(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.Poseidon cfg = YPConfig.get().powers.poseidon;
        activeWaves.add(new ActiveTsunami(
                player.getUuid(), world, player.getEntityPos(),
                cfg.tsunamiWaveRadius, cfg.tsunamiWaveDurationTicks, cfg.tsunamiDamage, cfg.tsunamiStunTicks
        ));
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_TSUNAMI, cfg.tsunamiCooldownTicks);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, cfg.tsunamiBuffTicks, cfg.tsunamiBuffRegenerationAmplifier, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, cfg.tsunamiBuffTicks, 1, false, true));
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.9f, 1.2f);
        player.sendMessage(Text.literal("TSUNAMI UNLEASHED!").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        if (activeWaves.isEmpty()) return;
        Iterator<ActiveTsunami> it = activeWaves.iterator();
        while (it.hasNext()) {
            ActiveTsunami wave = it.next();
            wave.tick++;
            double progress = (double) wave.tick / Math.max(1, wave.maxTicks);
            double radius = Math.max(1.5, wave.maxRadius * progress);
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(wave.ownerId);

            VfxSystem.ring(wave.world, ParticleTypes.SPLASH, wave.origin.add(0, 0.6, 0), radius, 36, 0.15);
            VfxSystem.ring(wave.world, ParticleTypes.BUBBLE_POP, wave.origin.add(0, 1.5, 0), radius, 24, 0.05);

            if (owner != null) {
                Box box = Box.of(wave.origin, radius * 2.0, 6.0, radius * 2.0);
                for (LivingEntity target : wave.world.getEntitiesByClass(LivingEntity.class, box,
                        e -> !wave.hitEntities.contains(e.getUuid()) && CombatUtil.canHarm(owner, e))) {
                    double dist = target.getEntityPos().distanceTo(wave.origin);
                    if (Math.abs(dist - radius) <= 2.5) {
                        wave.hitEntities.add(target.getUuid());
                        target.damage(wave.world, wave.world.getDamageSources().drown(), (float) wave.damage);
                        Vec3d push = target.getEntityPos().subtract(wave.origin).normalize();
                        target.setVelocity(push.x * 1.4, 0.55, push.z * 1.4);
                        ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
                        if (wave.stunTicks > 0) {
                            FreezeSystem.freeze(target, FreezeSystem.Source.POSEIDON_TSUNAMI, wave.stunTicks);
                        }
                    }
                }
            }
            if (wave.tick >= wave.maxTicks) it.remove();
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        PowerConfigs.Poseidon cfg = YPConfig.get().powers.poseidon;
        boolean inWater = meetsWaterCondition(player);
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);

        if (inWater) {
            if (speed != null && speed.getModifier(MOD_LAND_SLOW) != null) speed.removeModifier(MOD_LAND_SLOW);
            refreshEffect(player, StatusEffects.WATER_BREATHING, 0);
            refreshEffect(player, StatusEffects.DOLPHINS_GRACE, 0);
            refreshEffect(player, StatusEffects.REGENERATION, cfg.waterRegenerationAmplifier);
            if (cfg.waterBuffsEnabled) {
                refreshEffect(player, StatusEffects.HASTE, cfg.waterHasteAmplifier);
                refreshEffect(player, StatusEffects.RESISTANCE, cfg.waterResistanceAmplifier);
                refreshEffect(player, StatusEffects.STRENGTH, cfg.waterStrengthAmplifier);
            }
        } else {
            if (speed != null && speed.getModifier(MOD_LAND_SLOW) == null) {
                speed.addTemporaryModifier(new EntityAttributeModifier(
                        MOD_LAND_SLOW, -0.10, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            refreshEffect(player, StatusEffects.WEAKNESS, 0);
        }
    }

    @Override
    public boolean shouldPreventHungerDepletion(ServerPlayerEntity player) {
        return YPConfig.get().powers.poseidon.waterHungerProtectionEnabled && meetsWaterCondition(player);
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity attacker, Entity target, DamageSource source, float amount) {
        return meetsWaterCondition(attacker) ? amount * 1.20f : amount;
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureTrident(player);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        ensureTrident(player);
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        ensureTrident(newPlayer);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(MOD_LAND_SLOW) != null) speed.removeModifier(MOD_LAND_SLOW);
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return SoulboundItemSystem.isSoulbound(stack, PowerId.POSEIDON, TRIDENT_TAG);
    }

    private boolean meetsWaterCondition(ServerPlayerEntity player) {
        String mode = YPConfig.get().powers.poseidon.waterCondition;
        return switch (mode) {
            case "SUBMERGED" -> player.isSubmergedInWater();
            case "SWIMMING_OR_SUBMERGED" -> player.isSubmergedInWater() || (player.isSwimming() && player.isTouchingWater());
            default -> player.isTouchingWaterOrRain();
        };
    }

    private boolean hasNearbyWater(ServerPlayerEntity player) {
        if (player.isTouchingWater()) return true;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return false;
        int r = (int) Math.min(32, Math.ceil(YPConfig.get().powers.poseidon.tsunamiSearchRadius));
        BlockPos center = player.getBlockPos();
        int step = r > 16 ? 2 : 1;
        for (int dx = -r; dx <= r; dx += step) {
            for (int dz = -r; dz <= r; dz += step) {
                for (int dy = -6; dy <= 6; dy += 2) {
                    if (world.getBlockState(center.add(dx, dy, dz)).isOf(Blocks.WATER)) return true;
                }
            }
        }
        return false;
    }

    private static void refreshEffect(ServerPlayerEntity player, net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effect, int amp) {
        StatusEffectInstance cur = player.getStatusEffect(effect);
        if (cur == null || (cur.getAmplifier() == amp && cur.getDuration() <= 15)) {
            player.addStatusEffect(new StatusEffectInstance(effect, 45, amp, false, false));
        }
    }

    private static ItemStack ensureTrident(ServerPlayerEntity player) {
        ItemStack stack = SoulboundItemSystem.ensureEquippedOrPresent(player, PowerId.POSEIDON, TRIDENT_TAG, () -> {
            ItemStack trident = new ItemStack(Items.TRIDENT);
            trident.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Trident of Poseidon").formatted(Formatting.AQUA, Formatting.BOLD));
            trident.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
            ItemMarkerUtil.setFlag(trident, KEY_RIPTIDE, true);
            applyTridentEnchants(player, trident, true);
            return trident;
        });
        applyTridentEnchants(player, stack, ItemMarkerUtil.hasFlag(stack, KEY_RIPTIDE));
        player.currentScreenHandler.sendContentUpdates();
        return stack;
    }

    private static void applyTridentEnchants(ServerPlayerEntity player, ItemStack trident, boolean riptide) {
        trident.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        var reg = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        trident.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        EnchantmentHelper.apply(trident, builder -> {
            if (riptide) {
                builder.add(reg.getOrThrow(Enchantments.RIPTIDE), 3);
            } else {
                builder.add(reg.getOrThrow(Enchantments.LOYALTY), 3);
                builder.add(reg.getOrThrow(Enchantments.CHANNELING), 1);
            }
            builder.add(reg.getOrThrow(Enchantments.IMPALING), 5);
        });
    }
}
