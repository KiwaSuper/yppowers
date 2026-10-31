package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.item.YPItemData;
import com.yppowers.power.PowerType;
import com.yppowers.power.PowerManager;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import java.util.UUID;
import net.minecraft.util.Identifier;

public class PoseidonPower extends Power {
    private final Map<UUID, Integer> swapCooldowns = new HashMap<>();
    private final Map<UUID, Integer> tsunamiCooldowns = new HashMap<>();
    private final Map<UUID, TsunamiWaveState> activeTsunamiWaves = new HashMap<>();

    private enum WaterEffect { WATER_BREATHING, DOLPHINS_GRACE, REGENERATION, HASTE, RESISTANCE, STRENGTH }
    private enum LandEffect { WEAKNESS }
    private static final int EFFECT_REFRESH_TICKS = 40;
    private static final class EffectLease {
        final StatusEffectInstance previous;
        final int appliedAmplifier;
        int elapsedTicks;
        EffectLease(StatusEffectInstance previous, int appliedAmplifier) {
            this.previous = previous == null ? null : new StatusEffectInstance(previous);
            this.appliedAmplifier = appliedAmplifier;
        }
    }
    private final Map<UUID, Map<WaterEffect, EffectLease>> waterEffectLeases = new HashMap<>();
    private final Map<UUID, Map<LandEffect, EffectLease>> landEffectLeases = new HashMap<>();
    private static final Identifier LAND_SPEED_ID = Identifier.of("yppowers", "poseidon_land_weakness");

    @Override
    public PowerType getType() {
        return PowerType.POSEIDON;
    }

    @Override
    public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        tickCooldown(swapCooldowns, uuid);
        tickCooldown(tsunamiCooldowns, uuid);
        tickTsunamiWave(player);
        advanceEffectLeases(uuid);
        if (!player.isAlive()) {
            cleanupAllManagedEffects(player);
            removeLandPenalty(player);
            return;
        }

        YPConfig config = YPConfig.getInstance();
        if (isWaterConditionMet(player, config)) {
            removeLandPenalty(player);
            cleanupLandWeakness(player);
            applyWaterEffect(player, WaterEffect.WATER_BREATHING, StatusEffects.WATER_BREATHING, 0);
            applyWaterEffect(player, WaterEffect.DOLPHINS_GRACE, StatusEffects.DOLPHINS_GRACE, 0);
            applyWaterEffect(player, WaterEffect.REGENERATION, StatusEffects.REGENERATION,
                    Math.max(0, config.poseidon_water_regeneration_amplifier));
            if (config.poseidon_water_buffs_enabled) {
                applyWaterEffect(player, WaterEffect.HASTE, StatusEffects.HASTE, config.poseidon_water_haste_amplifier);
                applyWaterEffect(player, WaterEffect.RESISTANCE, StatusEffects.RESISTANCE, config.poseidon_water_resistance_amplifier);
                applyWaterEffect(player, WaterEffect.STRENGTH, StatusEffects.STRENGTH, config.poseidon_water_strength_amplifier);
            } else {
                cleanupWaterEffects(player, EnumSet.of(WaterEffect.HASTE, WaterEffect.RESISTANCE, WaterEffect.STRENGTH));
            }
        } else {
            cleanupWaterEffects(player, EnumSet.allOf(WaterEffect.class));
            applyLandPenalty(player);
            applyLeasedEffect(player, landEffectLeases, LandEffect.WEAKNESS, StatusEffects.WEAKNESS, 0, EFFECT_REFRESH_TICKS);
        }
    }

    /** Used by both the passive effects and HungerManagerMixin; defaults preserve the old touching-water rule. */
    public static boolean isWaterConditionMet(ServerPlayerEntity player) {
        return isWaterConditionMet(player, YPConfig.getInstance());
    }

    private static boolean isWaterConditionMet(ServerPlayerEntity player, YPConfig config) {
        String condition = config.poseidon_water_condition == null ? "TOUCHING" : config.poseidon_water_condition;
        return switch (condition) {
            case "SUBMERGED" -> player.isSubmergedInWater();
            case "SWIMMING_OR_SUBMERGED" -> player.isSwimming() || player.isSubmergedInWater();
            default -> player.isTouchingWater() || player.isSubmergedInWater();
        };
    }

    /** HungerManager calls this on the server and skips depletion only for an assigned, enabled Poseidon in water. */
    public static boolean shouldPreventHungerDepletion(ServerPlayerEntity player) {
        if (player == null || player.getEntityWorld().getServer() == null) return false;
        YPConfig config = YPConfig.getInstance();
        if (!config.poseidon_water_hunger_protection_enabled || !isWaterConditionMet(player, config)) return false;
        PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
        return manager.getPowerType(player.getUuid()) == PowerType.POSEIDON
                && manager.isPowerEnabled(player, PowerType.POSEIDON);
    }

    private static RegistryEntry<StatusEffect> waterEffectType(WaterEffect effect) {
        return switch (effect) {
            case WATER_BREATHING -> StatusEffects.WATER_BREATHING;
            case DOLPHINS_GRACE -> StatusEffects.DOLPHINS_GRACE;
            case REGENERATION -> StatusEffects.REGENERATION;
            case HASTE -> StatusEffects.HASTE;
            case RESISTANCE -> StatusEffects.RESISTANCE;
            case STRENGTH -> StatusEffects.STRENGTH;
        };
    }

    private static RegistryEntry<StatusEffect> landEffectType(LandEffect effect) {
        return StatusEffects.WEAKNESS;
    }

    private void applyWaterEffect(ServerPlayerEntity player, WaterEffect key,
                                  RegistryEntry<StatusEffect> type, int amplifier) {
        applyLeasedEffect(player, waterEffectLeases, key, type, amplifier, EFFECT_REFRESH_TICKS);
    }

    private <K> void applyLeasedEffect(ServerPlayerEntity player, Map<UUID, Map<K, EffectLease>> store,
                                       K key, RegistryEntry<StatusEffect> type, int amplifier, int duration) {
        UUID id = player.getUuid();
        Map<K, EffectLease> leases = store.computeIfAbsent(id, ignored -> new HashMap<>());
        StatusEffectInstance current = player.getStatusEffect(type);
        EffectLease lease = leases.get(key);
        if (lease == null) {
            // Do not shorten stronger or independently long-running effects. If an independent
            // effect already provides the same buff for longer, Poseidon does not claim ownership.
            if (current != null && (current.getAmplifier() > amplifier
                    || (current.getAmplifier() == amplifier && current.getDuration() > duration + 2))) return;
            lease = new EffectLease(current, amplifier);
            leases.put(key, lease);
        }
        // If another source upgrades or renews the effect, respect it instead of overwriting it.
        if (current != null && (current.getAmplifier() > amplifier
                || (current.getAmplifier() == amplifier && current.getDuration() > duration + 2))) return;
        // A 40-tick lease is refreshed once it falls to half duration, avoiding per-tick reapplication.
        if (current != null && current.getAmplifier() == amplifier && current.getDuration() > duration / 2) return;
        player.addStatusEffect(new StatusEffectInstance(type, duration, amplifier, false, false, true));
    }

    private void advanceEffectLeases(UUID id) {
        Map<WaterEffect, EffectLease> water = waterEffectLeases.get(id);
        if (water != null) water.values().forEach(lease -> lease.elapsedTicks++);
        Map<LandEffect, EffectLease> land = landEffectLeases.get(id);
        if (land != null) land.values().forEach(lease -> lease.elapsedTicks++);
    }

    private void cleanupWaterEffects(ServerPlayerEntity player, Set<WaterEffect> effects) {
        Map<WaterEffect, EffectLease> leases = waterEffectLeases.get(player.getUuid());
        if (leases == null) return;
        for (WaterEffect effect : effects) {
            EffectLease lease = leases.remove(effect);
            if (lease != null) restoreEffectLease(player, waterEffectType(effect), lease);
        }
        if (leases.isEmpty()) waterEffectLeases.remove(player.getUuid());
    }

    private void cleanupLandWeakness(ServerPlayerEntity player) {
        Map<LandEffect, EffectLease> leases = landEffectLeases.get(player.getUuid());
        if (leases == null) return;
        EffectLease lease = leases.remove(LandEffect.WEAKNESS);
        if (lease != null) restoreEffectLease(player, landEffectType(LandEffect.WEAKNESS), lease);
        if (leases.isEmpty()) landEffectLeases.remove(player.getUuid());
    }

    private void cleanupAllManagedEffects(ServerPlayerEntity player) {
        cleanupWaterEffects(player, EnumSet.allOf(WaterEffect.class));
        cleanupLandWeakness(player);
    }

    private static void restoreEffectLease(ServerPlayerEntity player, RegistryEntry<StatusEffect> type, EffectLease lease) {
        StatusEffectInstance current = player.getStatusEffect(type);
        boolean stillOurs = current == null || (current.getAmplifier() == lease.appliedAmplifier
                && current.getDuration() <= EFFECT_REFRESH_TICKS + 2);
        // A higher/longer effect likely came from another power or potion; leave it untouched.
        if (!stillOurs) return;
        if (current != null) player.removeStatusEffect(type);
        if (lease.previous == null) return;
        int duration = lease.previous.getDuration();
        if (duration >= 0) duration -= lease.elapsedTicks;
        if (duration == 0 || duration < -1) return;
        player.addStatusEffect(new StatusEffectInstance(lease.previous.getEffectType(), duration,
                lease.previous.getAmplifier(), lease.previous.isAmbient(),
                lease.previous.shouldShowParticles(), lease.previous.shouldShowIcon()));
    }

    private void tickCooldown(Map<UUID, Integer> map, UUID id) {
        int remaining = map.getOrDefault(id, 0);
        if (remaining <= 1) map.remove(id); else map.put(id, remaining - 1);
    }

    @Override
    public void onSecondaryActivate(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        YPConfig config = YPConfig.getInstance();
        if (tsunamiCooldowns.getOrDefault(id, 0) > 0) {
            player.sendMessage(Text.literal("Tsunami recharging: " + (tsunamiCooldowns.get(id) / 20) + "s")
                    .formatted(Formatting.AQUA), true);
            return;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        BlockPos water = findNearestWater(world, player.getBlockPos(), Math.min(100, config.poseidon_tsunami_search_radius));
        if (water == null) {
            player.sendMessage(Text.literal("No body of water found within 100 blocks.").formatted(Formatting.YELLOW), true);
            return;
        }

        Vec3d center = Vec3d.ofCenter(water);
        double radius = Math.max(1.0, config.poseidon_tsunami_wave_radius);
        int duration = Math.max(10, config.poseidon_tsunami_wave_duration_ticks);
        activeTsunamiWaves.put(id, new TsunamiWaveState(player, world, center, radius, duration));

        // Poseidon's personal blessing lasts longer than the physical wave.
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION,
                Math.max(1, config.poseidon_tsunami_buff_ticks), Math.max(0, config.poseidon_tsunami_buff_regeneration_amplifier), false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE,
                Math.max(1, config.poseidon_tsunami_buff_ticks), 2, false, true, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH,
                Math.max(1, config.poseidon_tsunami_buff_ticks), 1, false, true, true));

        world.playSound(null, water, SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.WEATHER, 4.0f, 0.55f);
        player.sendMessage(Text.literal("POSEIDON, LORD OF THE SEVEN SEAS — THE TIDAL WRATH IS UNLEASHED!")
                .formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    /** Advances a particle-only tidal wall; it never places or schedules water blocks. */
    private void tickTsunamiWave(ServerPlayerEntity tickingPlayer) {
        UUID ownerId = tickingPlayer.getUuid();
        TsunamiWaveState state = activeTsunamiWaves.get(ownerId);
        if (state == null) return;

        if (state.world.getServer() == null || state.age >= state.durationTicks) {
            activeTsunamiWaves.remove(ownerId);
            state.world.playSound(null, BlockPos.ofFloored(state.center.x, state.center.y, state.center.z),
                    SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.WEATHER, 2.5f, 0.85f);
            return;
        }

        state.age++;
        double previousRadius = state.maxRadius * Math.max(0, state.age - 1) / state.durationTicks;
        double currentRadius = state.maxRadius * state.age / state.durationTicks;

        // The wave is resolved each tick, while expensive decorative particle calls are throttled.
        applyTsunamiWaveHits(state, previousRadius, currentRadius);
        if (state.age % 3 == 1 || state.age == state.durationTicks) {
            renderTsunamiWaveFrame(state, currentRadius);
        }
        if (state.age >= state.durationTicks) {
            state.world.playSound(null, BlockPos.ofFloored(state.center.x, state.center.y, state.center.z),
                    SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.WEATHER, 3.0f, 0.7f);
            activeTsunamiWaves.remove(ownerId);
        }
    }

    private void applyTsunamiWaveHits(TsunamiWaveState state, double previousRadius, double currentRadius) {
        double queryRadius = Math.max(1.0, currentRadius + 2.0);
        Box area = new Box(state.center.x - queryRadius, state.center.y - 3.0, state.center.z - queryRadius,
                state.center.x + queryRadius, state.center.y + 8.0, state.center.z + queryRadius);
        YPConfig config = YPConfig.getInstance();
        for (LivingEntity target : state.world.getEntitiesByClass(LivingEntity.class, area,
                entity -> entity != state.caster && entity.isAlive() && !entity.isRemoved())) {
            UUID targetId = target.getUuid();
            if (state.hitTargets.contains(targetId)) continue;
            if (target instanceof ServerPlayerEntity other
                    && com.yppowers.alliance.AllianceManager.get(state.world.getServer()).isAlly(state.caster, other)) continue;

            Vec3d offset = target.getEntityPos().subtract(state.center);
            double distance = offset.horizontalLength();
            // The crest has a little thickness so entities are not skipped between update ticks.
            if (distance > state.maxRadius || distance + 1.35 < previousRadius || distance > currentRadius + 1.35) continue;
            state.hitTargets.add(targetId);

            if (config.poseidon_tsunami_damage > 0) {
                target.damage(state.world, state.world.getDamageSources().playerAttack(state.caster),
                        (float) config.poseidon_tsunami_damage);
            }
            int stunTicks = Math.max(0, config.poseidon_tsunami_stun_ticks);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, stunTicks, 10, false, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, stunTicks, 128, false, true, true));
            Vec3d horizontal = new Vec3d(offset.x, 0.0, offset.z);
            Vec3d push = horizontal.horizontalLengthSquared() < 1.0e-5
                    ? new Vec3d(0, 0, 1) : horizontal.normalize();
            target.setVelocity(push.multiply(1.8).add(0, 0.8, 0));
        }
    }

    private void renderTsunamiWaveFrame(TsunamiWaveState state, double radius) {
        // A broad foaming wall with a taller, broken crest sells the scale without world block updates.
        int points = Math.max(18, Math.min(48, (int) Math.ceil(Math.PI * 2 * Math.max(1.0, radius) / 2.2)));
        double crestHeight = 3.4;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points;
            double x = state.center.x + Math.cos(angle) * radius;
            double z = state.center.z + Math.sin(angle) * radius;
            double crestY = state.center.y + crestHeight + Math.sin(angle * 3.0 + state.age * 0.42) * 0.55;

            state.world.spawnParticles(ParticleTypes.SPLASH, x, state.center.y + 1.0, z,
                    4, 0.3, 1.7, 0.3, 0.16);
            if (i % 3 == 0) {
                state.world.spawnParticles(ParticleTypes.FALLING_WATER, x, crestY, z,
                        2, 0.25, 0.65, 0.25, 0.06);
            }
            if (i % 6 == 0) {
                state.world.spawnParticles(ParticleTypes.BUBBLE, x, state.center.y + 0.25, z,
                        2, 0.4, 0.3, 0.4, 0.12);
                state.world.spawnParticles(ParticleTypes.CLOUD, x, crestY + 0.2, z,
                        1, 0.25, 0.4, 0.25, 0.03);
            }
        }

        // A short-lived sea-spray plume rises at the water source as the crest passes it.
        if (state.age <= 6) {
            // Three rising jets evoke Poseidon's trident before the crest breaks outward.
            for (int y = 1; y <= 5; y++) {
                state.world.spawnParticles(ParticleTypes.SPLASH, state.center.x, state.center.y + y, state.center.z,
                        7, 0.35, 0.3, 0.35, 0.12);
                state.world.spawnParticles(ParticleTypes.SPLASH, state.center.x - 1.35, state.center.y + y, state.center.z,
                        4, 0.25, 0.3, 0.25, 0.1);
                state.world.spawnParticles(ParticleTypes.SPLASH, state.center.x + 1.35, state.center.y + y, state.center.z,
                        4, 0.25, 0.3, 0.25, 0.1);
            }
        }
    }

    private static final class TsunamiWaveState {
        private final ServerPlayerEntity caster;
        private final ServerWorld world;
        private final Vec3d center;
        private final double maxRadius;
        private final int durationTicks;
        private final Set<UUID> hitTargets = new HashSet<>();
        private int age;

        private TsunamiWaveState(ServerPlayerEntity caster, ServerWorld world, Vec3d center,
                                 double maxRadius, int durationTicks) {
            this.caster = caster;
            this.world = world;
            this.center = center;
            this.maxRadius = maxRadius;
            this.durationTicks = durationTicks;
        }
    }

    private BlockPos findNearestWater(ServerWorld world, BlockPos origin, double maxDistance) {
        int max = (int)Math.ceil(maxDistance);
        BlockPos best = null;
        double bestDistance = maxDistance * maxDistance;
        // Coarse radial sampling keeps the 100-block search bounded; inspect nearby heights around each sample.
        for (int radius = 0; radius <= max; radius += 4) {
            int samples = Math.max(16, (int)(radius * 1.5));
            for (int i = 0; i < samples; i++) {
                double angle = Math.PI * 2 * i / samples;
                int x = origin.getX() + (int)Math.round(Math.cos(angle) * radius);
                int z = origin.getZ() + (int)Math.round(Math.sin(angle) * radius);
                for (int dy = -8; dy <= 8; dy++) {
                    BlockPos pos = new BlockPos(x, origin.getY() + dy, z);
                    if (!world.isChunkLoaded(ChunkPos.toLong(pos))) continue;
                    if (!world.getBlockState(pos).getFluidState().isIn(FluidTags.WATER)) continue;
                    double distance = pos.getSquaredDistance(origin);
                    if (distance < bestDistance) { bestDistance = distance; best = pos.toImmutable(); }
                }
            }
            if (best != null && radius * radius > bestDistance) break;
        }
        return best;
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, DamageSource source, float amount) {
        boolean meleeOrTrident = source.isOf(DamageTypes.PLAYER_ATTACK) || source.isOf(DamageTypes.TRIDENT);
        if (meleeOrTrident && isWaterConditionMet(player)) {
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
        cleanupAllManagedEffects(player);
        activeTsunamiWaves.remove(player.getUuid());
        swapCooldowns.remove(player.getUuid());
        tsunamiCooldowns.remove(player.getUuid());
        removeLandPenalty(player);
    }

    @Override public void onPlayerDeath(ServerPlayerEntity player) {
        cleanupAllManagedEffects(player);
        removeLandPenalty(player);
        activeTsunamiWaves.remove(player.getUuid());
    }

    @Override public void onPlayerDisconnect(ServerPlayerEntity player) {
        cleanupAllManagedEffects(player);
        removeLandPenalty(player);
    }

    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        cleanupAllManagedEffects(player);
        removeLandPenalty(player);
        ensureTrident(player);
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
        boolean currentlyRiptide = hasRiptide(player, trident);
        boolean newRiptideMode = !currentlyRiptide;
        applyMaxedTridentEnchantments(player, trident, newRiptideMode);

        if (newRiptideMode) {
            player.sendMessage(Text.literal("Poseidon Trident: Riptide III | Impaling V | Unbreaking III | Mending I | Vanishing I")
                    .formatted(Formatting.AQUA), true);
        } else {
            player.sendMessage(Text.literal("Poseidon Trident: Loyalty III + Channeling I | Impaling V | Unbreaking III | Mending I | Vanishing I")
                    .formatted(Formatting.GOLD), true);
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

    private boolean hasRiptide(ServerPlayerEntity player, ItemStack trident) {
        return EnchantmentHelper.getLevel(
                player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.RIPTIDE), trident) > 0;
    }

    /**
     * Applies every max-level vanilla trident enchantment. Riptide is mutually exclusive
     * with Loyalty and Channeling, so quick-swap selects exactly one of those configurations.
     */
    private void applyMaxedTridentEnchantments(ServerPlayerEntity player, ItemStack trident, boolean riptideMode) {
        var enchantmentRegistry = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        trident.set(net.minecraft.component.DataComponentTypes.ENCHANTMENTS,
                net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);
        EnchantmentHelper.apply(trident, builder -> {
            builder.add(enchantmentRegistry.getOrThrow(Enchantments.IMPALING), 5);
            builder.add(enchantmentRegistry.getOrThrow(Enchantments.UNBREAKING), 3);
            builder.add(enchantmentRegistry.getOrThrow(Enchantments.MENDING), 1);
            builder.add(enchantmentRegistry.getOrThrow(Enchantments.VANISHING_CURSE), 1);
            if (riptideMode) {
                builder.add(enchantmentRegistry.getOrThrow(Enchantments.RIPTIDE), 3);
            } else {
                builder.add(enchantmentRegistry.getOrThrow(Enchantments.LOYALTY), 3);
                builder.add(enchantmentRegistry.getOrThrow(Enchantments.CHANNELING), 1);
            }
        });
    }

    private void ensureTrident(ServerPlayerEntity player) {
        boolean hasTrident = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(Items.TRIDENT) && YPItemData.hasFlag(stack, "YPPoseidonTrident")) {
                hasTrident = true;
                // Upgrade older Poseidon tridents in place while preserving the current swap mode.
                applyMaxedTridentEnchantments(player, stack, hasRiptide(player, stack));
                tridentMakeUnbreakable(stack);
            }
        }
        if (!hasTrident) {
            ItemStack trident = new ItemStack(Items.TRIDENT);
            applyMaxedTridentEnchantments(player, trident, true);
            YPItemData.setFlag(trident, "YPPoseidonTrident", true);
            tridentMakeUnbreakable(trident);
            player.giveOrDropStack(trident);
        }
    }

    private void tridentMakeUnbreakable(ItemStack trident) {
        trident.set(net.minecraft.component.DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return (stack.isOf(Items.TRIDENT) && YPItemData.hasFlag(stack, "YPPoseidonTrident"))
                || stack.isOf(Items.SHIELD);
    }
}
