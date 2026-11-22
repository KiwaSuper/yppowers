package com.yppowers.power.bootssky;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.EnergySystem;
import com.yppowers.system.SoulboundItemSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
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
import net.minecraft.util.Unit;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boots of the Sky: limited-duration survival flight (G / double-tap Space) with recharge & upkeep,
 * independent persistent Feather Falling IV toggle (N), and Raigo thunder-orb ultimate (R).
 */
public final class BootsOfTheSkyPower extends Power {
    private static final String CD_RAIGO = "boots_raigo";
    private static final String BOOTS_TAG = "boots_sky";
    private static final String NBT_FF = "FeatherFallingEnabled";

    private static final class FlightState {
        boolean active;
        int chargeTicks;
        int upkeepClock;
        FlightState(int maxCharge) { this.chargeTicks = maxCharge; }
    }

    private static final class RaigoStrike {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d target;
        final int chargeTicks;
        final double radius;
        final double maxDamage;
        int tick;

        RaigoStrike(UUID ownerId, ServerWorld world, Vec3d target, int chargeTicks, double radius, double maxDamage) {
            this.ownerId = ownerId;
            this.world = world;
            this.target = target;
            this.chargeTicks = chargeTicks;
            this.radius = radius;
            this.maxDamage = maxDamage;
        }
    }

    private final Map<UUID, FlightState> flights = new HashMap<>();
    private final Map<UUID, RaigoStrike> activeRaigos = new HashMap<>();

    @Override
    public PowerId id() {
        return PowerId.BOOTS_SKY;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.toggle(AbilitySlot.PRIMARY, "Flight"),
                AbilitySpec.active(AbilitySlot.ALTERNATE, "Raigo", CD_RAIGO),
                AbilitySpec.toggle(AbilitySlot.FEATHER_FALLING_TOGGLE, "Feather Fall")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        FlightState f = flightState(player.getUuid());
        int max = Math.max(1, YPConfig.get().powers.bootsOfTheSky.flightDurationTicks);
        int pct = (int) Math.round((100.0 * f.chargeTicks) / max);
        return (f.active ? "Flying " : "Flight ") + pct + "%";
    }

    @Override
    public boolean isToggleActive(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.PRIMARY) return flightState(player.getUuid()).active;
        if (slot == AbilitySlot.FEATHER_FALLING_TOGGLE) return isFeatherFallingEnabled(player);
        return false;
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.DOUBLE_JUMP
                || slot == AbilitySlot.ALTERNATE || slot == AbilitySlot.FEATHER_FALLING_TOGGLE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        return switch (slot) {
            case PRIMARY, DOUBLE_JUMP, FEATHER_FALLING_TOGGLE -> 0.0;
            case ALTERNATE -> YPConfig.get().powers.bootsOfTheSky.raigoEnergyCost;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean pausesEnergyRegen(ServerPlayerEntity player) {
        FlightState f = flights.get(player.getUuid());
        return f != null && f.active && player.getAbilities().flying;
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.FEATHER_FALLING_TOGGLE) return true;
        if (slot == AbilitySlot.PRIMARY || slot == AbilitySlot.DOUBLE_JUMP) {
            if (!isWearingMarkedBoots(player)) {
                if (slot == AbilitySlot.PRIMARY) {
                    player.sendMessage(Text.literal("Equip your Boots of the Sky to fly!").formatted(Formatting.YELLOW), true);
                }
                return false;
            }
            return true;
        }
        if (slot == AbilitySlot.ALTERNATE) {
            if (activeRaigos.containsKey(player.getUuid())) {
                player.sendMessage(Text.literal("Raigo is already charging.").formatted(Formatting.YELLOW), true);
                return false;
            }
            MinecraftServer server = player.getEntityWorld().getServer();
            return server != null && CooldownSystem.get(server).checkReady(player, CD_RAIGO, "Raigo");
        }
        return false;
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY, DOUBLE_JUMP -> toggleFlight(player);
            case FEATHER_FALLING_TOGGLE -> toggleFeatherFalling(player);
            case ALTERNATE -> startRaigo(player);
            default -> {}
        }
    }

    private void toggleFlight(ServerPlayerEntity player) {
        FlightState f = flightState(player.getUuid());
        if (f.active) {
            stopFlight(player, true, "Flight: OFF");
            return;
        }
        if (f.chargeTicks <= 20) {
            player.sendMessage(Text.literal("Boots of the Sky are recharging!").formatted(Formatting.YELLOW), true);
            return;
        }
        f.active = true;
        f.upkeepClock = 0;
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().flying = true;
            player.sendAbilitiesUpdate();
        }
        if (player.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.ring(world, ParticleTypes.CLOUD, player.getEntityPos(), 1.4, 16, 0.04);
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 0.6f, 1.3f);
        }
        player.sendMessage(Text.literal("Flight: ON").formatted(Formatting.AQUA), true);
    }

    private void stopFlight(ServerPlayerEntity player, boolean grantSlowFall, String notice) {
        FlightState f = flightState(player.getUuid());
        if (!f.active) return;
        f.active = false;
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().flying = false;
            player.getAbilities().allowFlying = false;
            player.sendAbilitiesUpdate();
        }
        if (grantSlowFall && !player.isOnGround()) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 120, 0, false, true));
        }
        if (notice != null && !notice.isEmpty()) {
            player.sendMessage(Text.literal(notice).formatted(Formatting.YELLOW), true);
        }
    }

    private void toggleFeatherFalling(ServerPlayerEntity player) {
        boolean next = !isFeatherFallingEnabled(player);
        setFeatherFallingEnabled(player, next);
        ItemStack boots = ensureBoots(player);
        applyBootsEnchants(player, boots, next);
        player.sendMessage(Text.literal("Boots Feather Falling IV: " + (next ? "ON" : "OFF"))
                .formatted(next ? Formatting.GREEN : Formatting.YELLOW), true);
    }

    private void startRaigo(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.BootsOfTheSky cfg = YPConfig.get().powers.bootsOfTheSky;
        HitResult hit = player.raycast(cfg.raigoRange, 0.0f, false);
        Vec3d target = hit.getPos();

        activeRaigos.put(player.getUuid(), new RaigoStrike(
                player.getUuid(), world, target, cfg.raigoChargeTicks, cfg.raigoRadius, cfg.raigoMaxDamage));
        CooldownSystem.get(world.getServer()).setPersistent(player.getUuid(), CD_RAIGO, cfg.raigoCooldownTicks);
        VfxSystem.playSound(world, target, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.WEATHER, 1.5f, 0.7f);
        player.sendMessage(Text.literal("RAIGO CHARGING!").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        if (activeRaigos.isEmpty()) return;
        Iterator<Map.Entry<UUID, RaigoStrike>> it = activeRaigos.entrySet().iterator();
        while (it.hasNext()) {
            RaigoStrike r = it.next().getValue();
            r.tick++;
            double progress = (double) r.tick / Math.max(1, r.chargeTicks);
            Vec3d orbPos = r.target.add(0, 4.0 + progress * 14.0, 0);

            if (r.tick % 2 == 0) {
                VfxSystem.ring(r.world, ParticleTypes.ELECTRIC_SPARK, orbPos, 2.0 + progress * 3.0, 20, 0.08);
                VfxSystem.ring(r.world, ParticleTypes.CLOUD, r.target.add(0, 0.3, 0), r.radius * progress, 24, 0.02);
            }

            if (r.tick >= r.chargeTicks) {
                detonateRaigo(server, r);
                it.remove();
            }
        }
    }

    private void detonateRaigo(MinecraftServer server, RaigoStrike r) {
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(r.ownerId);
        VfxSystem.flash(r.world, r.target.add(0, 1.0, 0));
        VfxSystem.lightPillar(r.world, ParticleTypes.ELECTRIC_SPARK, r.target, 22.0, 32);
        VfxSystem.shockwave(r.world, ParticleTypes.ELECTRIC_SPARK, ParticleTypes.EXPLOSION, r.target.add(0, 0.4, 0), r.radius, 40);
        VfxSystem.playSound(r.world, r.target, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.WEATHER, 2.0f, 0.6f);

        for (int i = 0; i < 6; i++) {
            double angle = (Math.PI * 2.0 * i) / 6.0;
            double dist = i == 0 ? 0.0 : r.radius * 0.45;
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(r.world, SpawnReason.TRIGGERED);
            if (bolt != null) {
                if (owner != null) bolt.setChanneler(owner);
                bolt.refreshPositionAfterTeleport(r.target.x + Math.cos(angle) * dist, r.target.y, r.target.z + Math.sin(angle) * dist);
                r.world.spawnEntity(bolt);
            }
        }

        if (owner != null) {
            Box area = Box.of(r.target, r.radius * 2.0, r.radius * 2.0, r.radius * 2.0);
            for (LivingEntity target : r.world.getEntitiesByClass(LivingEntity.class, area,
                    e -> CombatUtil.canHarm(owner, e))) {
                double d = target.getEntityPos().distanceTo(r.target);
                if (d > r.radius) continue;
                double falloff = Math.max(0.25, 1.0 - (d / r.radius) * 0.75);
                target.damage(r.world, r.world.getDamageSources().lightningBolt(), (float) (r.maxDamage * falloff));
            }
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        PowerConfigs.BootsOfTheSky cfg = YPConfig.get().powers.bootsOfTheSky;
        FlightState f = flightState(player.getUuid());
        int maxCharge = cfg.flightDurationTicks;

        if (f.active) {
            if (!isWearingMarkedBoots(player)) {
                stopFlight(player, true, "Flight ended (boots unequipped).");
                return;
            }
            if (!player.isCreative() && !player.isSpectator() && !player.getAbilities().allowFlying) {
                stopFlight(player, true, "Flight cancelled.");
                return;
            }
            if (player.getAbilities().flying) {
                f.chargeTicks = Math.max(0, f.chargeTicks - 1);
                f.upkeepClock++;
                if (f.upkeepClock >= 20) {
                    f.upkeepClock = 0;
                    MinecraftServer server = player.getEntityWorld().getServer();
                    if (server != null && !EnergySystem.get(server).trySpendUpkeep(player, cfg.flightEnergyDrainPerSecond)) {
                        stopFlight(player, true, "Flight ended (Energy depleted).");
                        return;
                    }
                }
                if (f.chargeTicks <= 0) {
                    stopFlight(player, true, "Flight charge depleted! Recharging...");
                }
            }
        } else if (f.chargeTicks < maxCharge) {
            double rechargeRate = (double) maxCharge / Math.max(1, cfg.rechargeDurationTicks);
            f.chargeTicks = Math.min(maxCharge, f.chargeTicks + Math.max(1, (int) Math.round(rechargeRate)));
        }
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureBoots(player);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        ensureBoots(player);
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        ensureBoots(newPlayer);
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        stopFlight(player, false, null);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        stopFlight(player, false, null);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        stopFlight(player, false, null);
        flights.remove(player.getUuid());
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return SoulboundItemSystem.isSoulbound(stack, PowerId.BOOTS_SKY, BOOTS_TAG);
    }

    private FlightState flightState(UUID uuid) {
        return flights.computeIfAbsent(uuid, ignored -> new FlightState(YPConfig.get().powers.bootsOfTheSky.flightDurationTicks));
    }

    private static boolean isWearingMarkedBoots(ServerPlayerEntity player) {
        return SoulboundItemSystem.isSoulbound(player.getEquippedStack(EquipmentSlot.FEET), PowerId.BOOTS_SKY, BOOTS_TAG);
    }

    private boolean isFeatherFallingEnabled(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return true;
        return PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.BOOTS_SKY).getBoolean(NBT_FF, true);
    }

    private void setFeatherFallingEnabled(ServerPlayerEntity player, boolean enabled) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.BOOTS_SKY).putBoolean(NBT_FF, enabled);
        store.markDirty();
    }

    private ItemStack ensureBoots(ServerPlayerEntity player) {
        boolean ff = isFeatherFallingEnabled(player);
        ItemStack stack = SoulboundItemSystem.ensureEquippedOrPresent(player, PowerId.BOOTS_SKY, BOOTS_TAG, () -> {
            ItemStack boots = new ItemStack(Items.NETHERITE_BOOTS);
            boots.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Boots of the Sky").formatted(Formatting.AQUA, Formatting.BOLD));
            boots.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
            applyBootsEnchants(player, boots, ff);
            return boots;
        });
        applyBootsEnchants(player, stack, ff);
        player.currentScreenHandler.sendContentUpdates();
        return stack;
    }

    private static void applyBootsEnchants(ServerPlayerEntity player, ItemStack boots, boolean featherFalling) {
        boots.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
        var reg = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        boots.set(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
        if (featherFalling) {
            EnchantmentHelper.apply(boots, builder -> builder.add(reg.getOrThrow(Enchantments.FEATHER_FALLING), 4));
        }
    }
}
