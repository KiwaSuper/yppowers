package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.item.YPItemData;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Thor's soulbound mace and lightning abilities.
 *
 * Each thrown hammer places a real Heavy Core block as its impact marker. The marker is
 * tracked to its owner, protected from player mining, repaired if externally replaced, and
 * removed only when the owner dies or recalls Mjölnir.
 */
public class ThorPower extends Power {
    private static final int MAX_PICKUP_DELAY = 32767;
    private static final ThreadLocal<CoreRemovalAuthorization> AUTHORIZED_CORE_REMOVAL = new ThreadLocal<>();

    private final Map<UUID, Integer> swapCooldowns = new HashMap<>();
    private final Map<UUID, Integer> rageCooldowns = new HashMap<>();
    private final Map<UUID, Integer> throwCooldowns = new HashMap<>();
    private final Map<UUID, HammerState> thrownHammers = new HashMap<>();
    private final Map<UUID, Integer> windups = new HashMap<>();
    private final Map<UUID, Integer> launchWindows = new HashMap<>();
    private final Map<UUID, Integer> launchCooldowns = new HashMap<>();
    private final Map<UUID, Long> bufferedLaunches = new HashMap<>();
    private final Map<UUID, Integer> steeringTicks = new HashMap<>();
    /** Players who just launched and may queue the next launch during chain delay. */
    private final Set<UUID> chainableLaunches = new HashSet<>();
    private final Set<UUID> queuedChainLaunches = new HashSet<>();
    /** Chained follow-up launches fire automatically once their charge completes. */
    private final Set<UUID> autoLaunchAfterWindup = new HashSet<>();
    private final Set<UUID> flightChainCueShown = new HashSet<>();
    /** Players who missed the launch window and are waiting for the cooldown to finish. */
    private final Set<UUID> awaitingRechargeCue = new HashSet<>();

    private static final class CoreRemovalAuthorization {
        private final ServerWorld world;
        private final BlockPos pos;

        private CoreRemovalAuthorization(ServerWorld world, BlockPos pos) {
            this.world = world;
            this.pos = pos.toImmutable();
        }
    }

    private static final class HammerState {
        private final ServerWorld world;
        private final UUID entityId;
        private BlockPos corePos;
        private boolean impacted;
        private boolean returning;

        private HammerState(ServerWorld world, UUID entityId) {
            this.world = world;
            this.entityId = entityId;
        }
    }

    @Override public PowerType getType() { return PowerType.THOR; }

    @Override public void onAssign(ServerPlayerEntity player) { ensureMace(player); }

    @Override public void onPlayerJoin(ServerPlayerEntity player) { ensureMace(player); }

    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        ensureMace(player);
    }

    @Override public void onPlayerDeath(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        cleanupHammerState(id, player.getEntityWorld().getServer());
        windups.remove(id);
        launchWindows.remove(id);
        launchCooldowns.remove(id);
        bufferedLaunches.remove(id);
        steeringTicks.remove(id);
        chainableLaunches.remove(id);
        queuedChainLaunches.remove(id);
        autoLaunchAfterWindup.remove(id);
        flightChainCueShown.remove(id);
        awaitingRechargeCue.remove(id);
        rageCooldowns.remove(id);
        swapCooldowns.remove(id);
        throwCooldowns.remove(id);
    }

    @Override public void tick(ServerPlayerEntity player) {
        tickMap(swapCooldowns, player);
        tickMap(rageCooldowns, player);
        tickMap(throwCooldowns, player);
        tickMap(launchCooldowns, player);

        UUID id = player.getUuid();
        int windup = windups.getOrDefault(id, 0);
        boolean becameReadyThisTick = false;
        if (windup > 0) {
            windup--;
            if (windup == 0) {
                windups.remove(id);
                boolean autoLaunch = autoLaunchAfterWindup.remove(id);
                Long bufferedUntil = bufferedLaunches.remove(id);
                boolean bufferedLaunchIsValid = bufferedUntil != null && player.getEntityWorld().getServer() != null
                        && player.getEntityWorld().getServer().getTicks() <= bufferedUntil;
                if (autoLaunch || bufferedLaunchIsValid) {
                    executeRiptideLaunch(player);
                } else {
                    launchWindows.put(id, Math.max(1, YPConfig.getInstance().thor_riptide_ready_window_ticks));
                    becameReadyThisTick = true;
                    player.sendMessage(Text.literal("READY TO LAUNCH — right-click now!")
                            .formatted(Formatting.YELLOW), true);
                }
            } else {
                windups.put(id, windup);
            }
        }

        // Keep the ready window alive through this tick so an input callback can use it.
        if (!becameReadyThisTick) {
            int window = launchWindows.getOrDefault(id, 0);
            if (window > 0) {
                if (window == 1) {
                    launchWindows.remove(id);
                    if (launchCooldowns.getOrDefault(id, 0) > 0) {
                        awaitingRechargeCue.add(id);
                        player.sendMessage(Text.literal("Launch window closed — wait for RECHARGED, then right-click.")
                                .formatted(Formatting.GRAY), true);
                    } else {
                        player.sendMessage(Text.literal("Launch window closed — right-click to charge again.")
                                .formatted(Formatting.GRAY), true);
                    }
                } else {
                    launchWindows.put(id, window - 1);
                }
            }
        }

        Long bufferExpiry = bufferedLaunches.get(id);
        if (bufferExpiry != null && player.getEntityWorld().getServer() != null
                && player.getEntityWorld().getServer().getTicks() > bufferExpiry) bufferedLaunches.remove(id);

        // Keep the chain input available through the delay and the steering/burst window.
        // A queued follow-up starts charging as soon as the delay ends, then launches automatically.
        int remainingLaunchCooldown = launchCooldowns.getOrDefault(id, 0);
        if (chainableLaunches.contains(id) && !windups.containsKey(id) && !launchWindows.containsKey(id)) {
            if (remainingLaunchCooldown <= 0 && queuedChainLaunches.remove(id)) {
                chainableLaunches.remove(id);
                flightChainCueShown.remove(id);
                startRiptideWindup(player, true);
            } else if (remainingLaunchCooldown <= 0) {
                if (steeringTicks.getOrDefault(id, 0) > 0) {
                    if (flightChainCueShown.add(id)) {
                        player.sendMessage(Text.literal("FLIGHT READY — right-click during this burst to queue the next launch.")
                                .formatted(Formatting.AQUA), true);
                    }
                } else {
                    chainableLaunches.remove(id);
                    queuedChainLaunches.remove(id);
                    flightChainCueShown.remove(id);
                }
            }
        }

        if (awaitingRechargeCue.contains(id) && remainingLaunchCooldown <= 0
                && !windups.containsKey(id) && !launchWindows.containsKey(id)) {
            awaitingRechargeCue.remove(id);
            player.sendMessage(Text.literal("RECHARGED — right-click to start another launch.")
                    .formatted(Formatting.AQUA), true);
        }

        int steering = steeringTicks.getOrDefault(id, 0);
        if (steering > 0) {
            steering--;
            if (steering <= 0) steeringTicks.remove(id); else steeringTicks.put(id, steering);
            Vec3d current = player.getVelocity();
            Vec3d look = player.getRotationVec(1.0f).normalize();
            Vec3d horizontalLook = new Vec3d(look.x, Math.max(-0.25, Math.min(0.45, look.y)), look.z);
            if (horizontalLook.lengthSquared() > 1.0e-5) {
                double horizontalSpeed = Math.max(0.65, Math.sqrt(current.x * current.x + current.z * current.z));
                Vec3d desired = horizontalLook.normalize().multiply(horizontalSpeed);
                double steeringStrength = Math.max(0.0, Math.min(1.0, YPConfig.getInstance().thor_riptide_air_steering));
                Vec3d adjusted = current.multiply(1.0 - steeringStrength).add(desired.multiply(steeringStrength));
                double maximum = Math.max(0.1, YPConfig.getInstance().thor_riptide_max_velocity);
                if (adjusted.lengthSquared() > maximum * maximum) adjusted = adjusted.normalize().multiply(maximum);
                player.setVelocity(adjusted);
                ((EntityVelocityAccessor) (Object) player).invokeYppowersScheduleVelocityUpdate();
            }
        }
    }

    /**
     * Ticks thrown hammers separately from online-player ticking. This keeps a Heavy Core
     * protected if Thor disconnects while the hammer is out.
     */
    public static void serverTick(MinecraftServer server) {
        Power power = PowerManager.get(server).getPowerInstance(PowerType.THOR);
        if (power instanceof ThorPower thor) {
            thor.restorePersistentHammerStates(server);
            thor.tickHammers(server);
        }
    }

    /** True while Thor's own recall/death cleanup is removing its tracked Heavy Core. */
    public static boolean isAuthorizedHeavyCoreRemoval(ServerWorld world, BlockPos pos) {
        CoreRemovalAuthorization authorization = AUTHORIZED_CORE_REMOVAL.get();
        return authorization != null && authorization.world == world && authorization.pos.equals(pos);
    }

    private static void removeHeavyCoreForCleanup(ServerWorld world, BlockPos pos) {
        CoreRemovalAuthorization previous = AUTHORIZED_CORE_REMOVAL.get();
        AUTHORIZED_CORE_REMOVAL.set(new CoreRemovalAuthorization(world, pos));
        try {
            world.setBlockState(pos, Blocks.AIR.getDefaultState());
        } finally {
            if (previous == null) AUTHORIZED_CORE_REMOVAL.remove();
            else AUTHORIZED_CORE_REMOVAL.set(previous);
        }
    }

    /** True only for the tracked Heavy Core block belonging to an active thrown hammer. */
    public static boolean isProtectedHeavyCore(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null || world.getServer() == null) return false;
        Power power = PowerManager.get(world.getServer()).getPowerInstance(PowerType.THOR);
        if (!(power instanceof ThorPower thor)) return false;
        thor.restorePersistentHammerStates(world.getServer());
        for (HammerState state : thor.thrownHammers.values()) {
            if (state.impacted && state.world == world && pos.equals(state.corePos)) return true;
        }
        return false;
    }

    private void tickHammers(MinecraftServer server) {
        YPConfig config = YPConfig.getInstance();
        for (Map.Entry<UUID, HammerState> entry : thrownHammers.entrySet()) {
            UUID ownerId = entry.getKey();
            HammerState state = entry.getValue();
            Entity entity = state.world.getEntity(state.entityId);
            if (!(entity instanceof ItemEntity hammer) || hammer.isRemoved()) continue;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(ownerId);
            if (!state.impacted) {
                int timeout = Math.max(1, config.thor_hammer_impact_timeout_ticks);
                if (hammer.age >= Math.max(0, config.thor_riptide_min_charge_ticks)
                        && (hammer.horizontalCollision || hammer.verticalCollision || hammer.isOnGround() || hammer.age > timeout)) {
                    impactHammer(ownerId, owner, state, hammer);
                }
            } else {
                state.returning = true;
                hammer.setInvulnerable(true);
                hammer.setNoGravity(true);
                hammer.setPickupDelay(MAX_PICKUP_DELAY);
                hammer.age = 0;
                if (owner == null || owner.isRemoved()) continue;
                Vec3d delta = owner.getEyePos().subtract(hammer.getEntityPos());
                if (delta.lengthSquared() < 2.25) {
                    finishHammerReturn(ownerId, owner, state, hammer);
                    continue;
                }
                // Loyalty-style homing; break only ordinary, non-block-entity blocks in the path.
                Vec3d direction = delta.normalize();
                hammer.setVelocity(direction.multiply(Math.max(0.2, config.thor_throw_speed)));
                hammer.setPosition(hammer.getX() + direction.x * Math.max(0.2, config.thor_throw_speed),
                        hammer.getY() + direction.y * Math.max(0.2, config.thor_throw_speed),
                        hammer.getZ() + direction.z * Math.max(0.2, config.thor_throw_speed));
                BlockPos path = hammer.getBlockPos();
                if (state.world.isChunkLoaded(ChunkPos.toLong(path))) {
                    var blockState = state.world.getBlockState(path);
                    if (!blockState.isAir() && !blockState.isOf(Blocks.BEDROCK)
                            && !blockState.isOf(Blocks.OBSIDIAN) && !blockState.isOf(Blocks.REINFORCED_DEEPSLATE)
                            && state.world.getBlockEntity(path) == null && blockState.getHardness(state.world, path) >= 0) {
                        state.world.breakBlock(path, false, owner);
                    }
                }
                state.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, hammer.getX(), hammer.getY(), hammer.getZ(), 2, .12, .12, .12, .02);
            }
        }
    }

    private void finishHammerReturn(UUID ownerId, ServerPlayerEntity owner, HammerState state, ItemEntity hammer) {
        cleanupHammerState(ownerId, owner.getEntityWorld().getServer());
        owner.giveOrDropStack(createMace(owner));
        owner.sendMessage(Text.literal("MJÖLNIR RETURNED").formatted(Formatting.GOLD), true);
        state.world.playSound(null, owner.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.2f, .8f);
    }

    private void maintainHeavyCore(HammerState state) {
        if (state.corePos == null) return;
        // Do not force-load a chunk just to protect the marker. It is repaired as soon as
        // the relevant chunk is loaded again (including after server restart).
        if (!state.world.isChunkLoaded(ChunkPos.toLong(state.corePos))) return;
        if (!state.world.getBlockState(state.corePos).isOf(Blocks.HEAVY_CORE)) {
            // Repair attempts, explosions, or commands must not permanently remove the core.
            state.world.setBlockState(state.corePos, Blocks.HEAVY_CORE.getDefaultState());
        }
    }

    private void tickMap(Map<UUID, Integer> map, ServerPlayerEntity player) {
        UUID id = player.getUuid();
        int value = map.getOrDefault(id, 0);
        if (value <= 1) map.remove(id); else map.put(id, value - 1);
    }

    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        YPConfig config = YPConfig.getInstance();
        UUID id = player.getUuid();
        if (swapCooldowns.getOrDefault(id, 0) > 0) return;

        ItemStack mace = findMace(player);
        if (mace.isEmpty()) {
            ensureMace(player);
            mace = findMace(player);
        }
        if (mace.isEmpty()) return;

        int mode = (currentMaceMode(mace) + 1) % 5;
        applyMaceEnchantments(player, mace, mode);
        player.sendMessage(Text.literal("Thor's Mace: " + modeName(mode) + " " + modeLevel(config, mode)
                + " + shared enchantments").formatted(Formatting.GOLD), true);
        swapCooldowns.put(id, Math.max(0, config.thor_mace_swap_cooldown_ticks));
        player.getEntityWorld().playSound(
                null, player.getBlockPos(), SoundEvents.ITEM_MACE_SMASH_GROUND,
                SoundCategory.PLAYERS, (float) config.thor_mace_swap_sound_volume,
                (float) config.thor_mace_swap_sound_pitch
        );
    }

    private int currentMaceMode(ItemStack mace) {
        for (int i = 1; i < 5; i++) {
            if (YPItemData.hasFlag(mace, "YPThorMode" + i)) return i;
        }
        return 0;
    }

    private String modeName(int mode) {
        return switch (mode) {
            case 1 -> "Breach";
            case 2 -> "Sharpness";
            case 3 -> "Smite";
            case 4 -> "Bane of Arthropods";
            default -> "Density";
        };
    }

    private int modeLevel(YPConfig config, int mode) {
        return switch (mode) {
            case 1 -> config.thor_mace_breach_level;
            case 2 -> config.thor_mace_sharpness_level;
            case 3 -> config.thor_mace_smite_level;
            case 4 -> config.thor_mace_bane_of_arthropods_level;
            default -> config.thor_mace_density_level;
        };
    }

    /** Called by the Fabric use-item callback for right-clicking the soulbound mace. */
    public void beginRiptideLaunch(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        YPConfig config = YPConfig.getInstance();
        if (!YPItemData.hasFlag(player.getMainHandStack(), "YPThorMace")) return;

        // Right-clicking during the ready window launches immediately.
        if (launchWindows.containsKey(id)) {
            launchWindows.remove(id);
            executeRiptideLaunch(player);
            return;
        }

        // During charging, a late tap queues the launch instead of requiring frame-perfect timing.
        if (windups.containsKey(id)) {
            int remaining = windups.getOrDefault(id, 0);
            if (autoLaunchAfterWindup.contains(id)) {
                player.sendMessage(Text.literal("NEXT LAUNCH ALREADY QUEUED — wait for it to fire.")
                        .formatted(Formatting.GRAY), true);
            } else if (config.thor_riptide_input_buffer_ticks > 0 && player.getEntityWorld().getServer() != null
                    && remaining <= Math.max(1, config.thor_riptide_input_buffer_ticks)) {
                Long previous = bufferedLaunches.putIfAbsent(id,
                        (long) player.getEntityWorld().getServer().getTicks()
                                + Math.max(1, config.thor_riptide_input_buffer_ticks));
                if (previous == null) {
                    player.sendMessage(Text.literal("LAUNCH QUEUED — it will fire automatically when charged.")
                            .formatted(Formatting.YELLOW), true);
                }
            } else {
                player.sendMessage(Text.literal("CHARGING — tap again near the end to queue the launch.")
                        .formatted(Formatting.GRAY), true);
            }
            return;
        }

        // During the delay or remaining flight burst, one tap queues the next full launch.
        if (chainableLaunches.contains(id)) {
            if (!queuedChainLaunches.add(id)) {
                player.sendMessage(Text.literal("NEXT LAUNCH ALREADY QUEUED — wait for it to fire.")
                        .formatted(Formatting.GRAY), true);
            } else if (launchCooldowns.getOrDefault(id, 0) > 0) {
                player.sendMessage(Text.literal("NEXT LAUNCH QUEUED — it will charge and fire automatically.")
                        .formatted(Formatting.YELLOW), true);
            } else {
                // The delay has already elapsed; begin the automatic follow-up charge now.
                chainableLaunches.remove(id);
                queuedChainLaunches.remove(id);
                flightChainCueShown.remove(id);
                startRiptideWindup(player, true);
            }
            return;
        }

        if (launchCooldowns.getOrDefault(id, 0) > 0) {
            player.sendMessage(Text.literal("RECHARGING — wait for the RECHARGED cue.")
                    .formatted(Formatting.GRAY), true);
            return;
        }

        startRiptideWindup(player, false);
    }

    private void startRiptideWindup(ServerPlayerEntity player, boolean autoLaunchOnReady) {
        UUID id = player.getUuid();
        YPConfig config = YPConfig.getInstance();
        MinecraftServer launchServer = player.getEntityWorld().getServer();
        if (launchServer == null || !EnergyManager.get(launchServer).trySpendDirect(player,
                config.thor_riptide_energy_cost, "Thor's airborne launch")) return;

        int delay = Math.max(1, config.thor_riptide_windup_ticks);
        windups.put(id, delay);
        launchWindows.remove(id);
        bufferedLaunches.remove(id);
        chainableLaunches.remove(id);
        queuedChainLaunches.remove(id);
        autoLaunchAfterWindup.remove(id);
        flightChainCueShown.remove(id);
        if (autoLaunchOnReady) autoLaunchAfterWindup.add(id);
        awaitingRechargeCue.remove(id);
        launchCooldowns.put(id, Math.max(delay + Math.max(1, config.thor_riptide_ready_window_ticks),
                Math.max(0, config.thor_riptide_cooldown_ticks)));
        player.sendMessage(Text.literal(autoLaunchOnReady
                        ? "NEXT LAUNCH QUEUED — charging now; it will fire automatically."
                        : "CHARGING — right-click near the end to queue the launch.")
                .formatted(autoLaunchOnReady ? Formatting.YELLOW : Formatting.AQUA), true);
        player.getEntityWorld().playSound(
                null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, (float) config.thor_riptide_windup_sound_volume,
                (float) config.thor_riptide_windup_sound_pitch
        );
    }

    private void executeRiptideLaunch(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        YPConfig config = YPConfig.getInstance();
        windups.remove(id);
        launchWindows.remove(id);
        bufferedLaunches.remove(id);
        queuedChainLaunches.remove(id);
        autoLaunchAfterWindup.remove(id);
        flightChainCueShown.remove(id);
        awaitingRechargeCue.remove(id);
        chainableLaunches.add(id);
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d velocity = look.multiply(config.thor_riptide_launch_forward_velocity)
                .add(0, config.thor_riptide_launch_upward_velocity, 0);
        double maximum = Math.max(0.1, config.thor_riptide_max_velocity);
        if (velocity.lengthSquared() > maximum * maximum) velocity = velocity.normalize().multiply(maximum);
        player.setVelocity(velocity);
        ((EntityVelocityAccessor) (Object) player).invokeYppowersScheduleVelocityUpdate();
        player.fallDistance = 0;
        steeringTicks.put(id, Math.max(0, config.thor_riptide_steering_ticks));
        launchCooldowns.put(id, Math.max(0, config.thor_riptide_chain_delay_ticks));
        player.sendMessage(Text.literal("LAUNCHED — right-click during the burst to queue your next launch.")
                .formatted(Formatting.GOLD), true);
        player.getEntityWorld().playSound(
                null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(),
                SoundCategory.PLAYERS, (float) config.thor_riptide_launch_sound_volume,
                (float) config.thor_riptide_launch_sound_pitch
        );
        if (player.getEntityWorld() instanceof ServerWorld world) {
            double spread = Math.max(0.0, config.thor_riptide_launch_particle_spread);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                    clampCount(config.thor_riptide_launch_particle_count), spread, spread, spread,
                    Math.max(0.0, config.thor_riptide_launch_particle_speed));
        }
    }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) restoreHammerState(server, id);
        HammerState active = thrownHammers.get(id);
        if (active != null) {
            Entity entity = active.world.getEntity(active.entityId);
            if (entity != null && !entity.isRemoved()) {
                player.teleport(active.world, entity.getX(), entity.getY() + 1.0, entity.getZ(),
                        Set.of(), player.getYaw(), player.getPitch(), false);
            }
            recallHammer(player);
            return;
        }
        throwHammer(player);
    }

    @Override public void onSecondaryActivate(ServerPlayerEntity player) { ancientRage(player); }

    private void ancientRage(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        YPConfig config = YPConfig.getInstance();
        if (rageCooldowns.getOrDefault(id, 0) > 0) return;
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;

        double radius = Math.max(1.0, config.thor_ancient_rage_radius);
        Box box = player.getBoundingBox().expand(radius);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                entity -> entity != player && !entity.isRemoved())) {
            if (target.squaredDistanceTo(player) > radius * radius || areAllies(player, target)) continue;
            strikeLightning(world, target.getX(), target.getY(), target.getZ());
        }

        int strengthDuration = Math.max(0, config.thor_ancient_rage_strength_duration_ticks);
        if (strengthDuration > 0) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, strengthDuration,
                    Math.max(0, config.thor_ancient_rage_strength_amplifier), false, true, true));
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, (float) config.thor_ancient_rage_sound_volume,
                (float) config.thor_ancient_rage_sound_pitch);
        double spread = Math.max(0.0, radius * config.thor_ancient_rage_particle_spread_multiplier);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                clampCount(config.thor_ancient_rage_particle_count), spread,
                Math.max(0.0, config.thor_ancient_rage_particle_vertical_spread), spread,
                Math.max(0.0, config.thor_ancient_rage_particle_speed));
        rageCooldowns.put(id, Math.max(0, config.thor_ancient_rage_cooldown_ticks));
    }

    private void throwHammer(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (throwCooldowns.getOrDefault(id, 0) > 0) return;
        ItemStack mace = findMace(player);
        if (mace.isEmpty()) { ensureMace(player); mace = findMace(player); }
        if (mace.isEmpty() || !(player.getEntityWorld() instanceof ServerWorld world)) return;
        YPConfig config = YPConfig.getInstance();
        ItemStack thrownStack = mace.copy(); thrownStack.setCount(1);
        ItemEntity thrown = new ItemEntity(world, player.getX(), player.getEyeY() - 0.2, player.getZ(), thrownStack);
        thrown.setPickupDelay(MAX_PICKUP_DELAY); thrown.setInvulnerable(true); thrown.setNoGravity(false);
        thrown.setVelocity(player.getRotationVec(1.0F).normalize().multiply(Math.max(0.1, config.thor_throw_speed)));
        world.spawnEntity(thrown);
        HammerState state = new HammerState(world, thrown.getUuid());
        thrownHammers.put(id, state); persistHammerState(world.getServer(), id, state); removeMaceFromInventory(player);
        player.sendMessage(Text.literal("MJÖLNIR THROWN — press G again to blink to it and recall.").formatted(Formatting.AQUA), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.PLAYERS,
                (float) config.thor_hammer_throw_sound_volume, (float) config.thor_hammer_throw_sound_pitch);
    }

    private void impactHammer(UUID ownerId, ServerPlayerEntity owner, HammerState state, ItemEntity hammer) {
        YPConfig config = YPConfig.getInstance();
        BlockPos impactPos = hammer.getBlockPos();

        state.impacted = true;
        state.returning = true;
        persistHammerState(state.world.getServer(), ownerId, state);
        hammer.setInvulnerable(true);
        hammer.setNoGravity(true);
        hammer.setPickupDelay(MAX_PICKUP_DELAY);
        hammer.age = 0;
        if (config.thor_crater_enabled) digCrater(state.world, impactPos, owner, config);
        double impactRadius = Math.max(0.0, config.thor_impact_radius);
        if (owner != null && impactRadius > 0 && config.thor_impact_damage > 0) {
            Box impactBox = new Box(impactPos).expand(impactRadius);
            for (LivingEntity target : state.world.getEntitiesByClass(LivingEntity.class, impactBox,
                    e -> e != owner && e.isAlive() && !areAllies(owner, e))) {
                if (target.squaredDistanceTo(Vec3d.ofCenter(impactPos)) <= impactRadius * impactRadius) {
                    target.damage(state.world, state.world.getDamageSources().playerAttack(owner), (float) config.thor_impact_damage);
                    Vec3d away = target.getEntityPos().subtract(Vec3d.ofCenter(impactPos));
                    if (away.lengthSquared() > 1.0e-5) {
                        target.setVelocity(away.normalize().multiply(Math.max(0.0, config.thor_riptide_strength)).add(0, 0.35, 0));
                    }
                }
            }
        }

        int lightningCount = clampCount(config.thor_hammer_impact_lightning_count);
        double nearOffset = config.thor_hammer_impact_near_offset;
        double farOffset = config.thor_hammer_impact_far_offset;
        for (int i = 0; i < lightningCount; i++) {
            double distance = (i % 2 == 0) ? nearOffset : farOffset;
            double sign = (i / 2) % 2 == 0 ? -1.0 : 1.0;
            double x = impactPos.getX() + 0.5 + sign * distance;
            double z = impactPos.getZ() + 0.5 + ((i % 3 == 0) ? distance : -distance);
            strikeLightning(state.world, x, impactPos.getY(), z);
        }

        state.world.spawnParticles(ParticleTypes.EXPLOSION, impactPos.getX() + 0.5, impactPos.getY() + 1,
                impactPos.getZ() + 0.5, clampCount(config.thor_hammer_impact_particle_count),
                Math.max(0.0, config.thor_hammer_impact_particle_spread),
                Math.max(0.0, config.thor_hammer_impact_particle_vertical_spread),
                Math.max(0.0, config.thor_hammer_impact_particle_spread),
                Math.max(0.0, config.thor_hammer_impact_particle_speed));
        EpicVfx.impact(state.world, impactPos.getX() + 0.5, impactPos.getY() + 1.0, impactPos.getZ() + 0.5, 1.5);
        state.world.playSound(null, impactPos, SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY,
                SoundCategory.BLOCKS, (float) config.thor_hammer_impact_sound_volume,
                (float) config.thor_hammer_impact_sound_pitch);
        if (owner != null) owner.sendMessage(Text.literal("IMPACT — MJÖLNIR IS RETURNING!").formatted(Formatting.GOLD), true);
    }

    private void digCrater(ServerWorld world, BlockPos center, ServerPlayerEntity owner, YPConfig config) {
        int radius = Math.max(1, Math.min(12, config.thor_crater_radius));
        int depth = Math.max(1, Math.min(12, config.thor_crater_depth));
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            double normalized = (double)(x*x + z*z) / (radius * radius);
            if (normalized > 1.0) continue;
            int columnDepth = Math.max(1, (int)Math.round(depth * (1.0 - normalized)));
            for (int y = 0; y < columnDepth; y++) {
                BlockPos pos = center.add(x, -y, z);
                if (!world.isChunkLoaded(ChunkPos.toLong(pos))) continue;
                var block = world.getBlockState(pos);
                if (block.isAir() || block.isOf(Blocks.BEDROCK) || block.isOf(Blocks.OBSIDIAN)
                        || block.isOf(Blocks.REINFORCED_DEEPSLATE) || world.getBlockEntity(pos) != null
                        || block.getHardness(world, pos) < 0) continue;
                world.breakBlock(pos, true, owner);
            }
        }
    }

    private BlockPos placeHeavyCoreMarker(ServerWorld world, BlockPos impactPos) {
        // Prefer the impact block, then nearby air blocks. Never overwrite ordinary blocks.
        List<BlockPos> candidates = List.of(
                impactPos, impactPos.up(), impactPos.up(2),
                impactPos.north(), impactPos.south(), impactPos.east(), impactPos.west(),
                impactPos.up().north(), impactPos.up().south(), impactPos.up().east(), impactPos.up().west(),
                impactPos.north().up(), impactPos.south().up(), impactPos.east().up(), impactPos.west().up()
        );
        for (BlockPos candidate : candidates) {
            if (world.isInBuildLimit(candidate) && world.getBlockState(candidate).isAir()) {
                world.setBlockState(candidate, Blocks.HEAVY_CORE.getDefaultState());
                return candidate.toImmutable();
            }
        }
        return null;
    }

    private void recallHammer(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        YPConfig config = YPConfig.getInstance();

        cleanupHammerState(id, player.getEntityWorld().getServer());
        player.giveOrDropStack(createMace(player));

        if (player.getEntityWorld() instanceof ServerWorld world) {
            double radius = Math.max(1.0, config.thor_recall_stun_radius);
            int duration = Math.max(0, config.thor_recall_stun_duration_ticks);
            for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class,
                    player.getBoundingBox().expand(radius), entity -> entity != player)) {
                if (target.squaredDistanceTo(player) > radius * radius || areAllies(player, target)) continue;
                if (duration > 0) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, duration,
                            Math.max(0, config.thor_recall_slowness_amplifier), false, true, true));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, duration,
                            Math.max(0, config.thor_recall_weakness_amplifier), false, true, true));
                }
                target.setVelocity(Vec3d.ZERO);
                ((EntityVelocityAccessor) (Object) target).invokeYppowersScheduleVelocityUpdate();
            }

            double spread = Math.max(0.0, radius * config.thor_recall_particle_spread_multiplier);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(),
                    clampCount(config.thor_recall_particle_count), spread,
                    Math.max(0.0, config.thor_recall_particle_vertical_spread), spread,
                    Math.max(0.0, config.thor_recall_particle_speed));
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN,
                    SoundCategory.PLAYERS, (float) config.thor_recall_sound_volume,
                    (float) config.thor_recall_sound_pitch);
        }
        player.sendMessage(Text.literal("MJÖLNIR RECALLED").formatted(Formatting.GOLD), true);
    }

    private void cleanupHammerState(UUID ownerId, MinecraftServer server) {
        if (server != null) restoreHammerState(server, ownerId);
        HammerState state = thrownHammers.remove(ownerId);
        if (state != null) {
            Entity entity = state.world.getEntity(state.entityId);
            if (entity != null) entity.discard();

            if (state.corePos != null) {
                // Explicit recall/death cleanup may load this one marker chunk so it is not
                // orphaned merely because the owner died in another dimension or far away.
                state.world.getChunk(state.corePos);
                if (state.world.getBlockState(state.corePos).isOf(Blocks.HEAVY_CORE)) {
                    removeHeavyCoreForCleanup(state.world, state.corePos);
                }
            }
        }
        if (server != null) PowerManager.get(server).clearThorHammerState(ownerId);
    }

    private void restorePersistentHammerStates(MinecraftServer server) {
        for (UUID ownerId : PowerManager.get(server).getThorHammerStates().keySet()) {
            restoreHammerState(server, ownerId);
        }
    }

    private void restoreHammerState(MinecraftServer server, UUID ownerId) {
        if (server == null || thrownHammers.containsKey(ownerId)) return;
        NbtCompound saved = PowerManager.get(server).getThorHammerState(ownerId);
        if (saved == null) return;

        try {
            UUID entityId = UUID.fromString(saved.getString("EntityId", ""));
            Identifier dimensionId = Identifier.tryParse(saved.getString("Dimension", ""));
            if (dimensionId == null) return;
            RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
            ServerWorld world = server.getWorld(dimensionKey);
            if (world == null) return;

            HammerState state = new HammerState(world, entityId);
            state.impacted = saved.getBoolean("Impacted", false);
            if (saved.getBoolean("HasCore", false)) {
                state.corePos = new BlockPos(saved.getInt("CoreX", 0),
                        saved.getInt("CoreY", 0), saved.getInt("CoreZ", 0));
            }
            thrownHammers.put(ownerId, state);
        } catch (IllegalArgumentException ignored) {
            // Discard malformed identifiers gracefully instead of breaking login or ticking.
        }
    }

    private void persistHammerState(MinecraftServer server, UUID ownerId, HammerState state) {
        if (server == null) return;
        NbtCompound saved = new NbtCompound();
        saved.putString("EntityId", state.entityId.toString());
        saved.putString("Dimension", state.world.getRegistryKey().getValue().toString());
        saved.putBoolean("Impacted", state.impacted);
        saved.putBoolean("HasCore", state.corePos != null);
        if (state.corePos != null) {
            saved.putInt("CoreX", state.corePos.getX());
            saved.putInt("CoreY", state.corePos.getY());
            saved.putInt("CoreZ", state.corePos.getZ());
        }
        PowerManager.get(server).saveThorHammerState(ownerId, saved);
    }

    @Override public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (source.isOf(DamageTypes.LIGHTNING_BOLT) || source.isIn(DamageTypeTags.IS_LIGHTNING)
                || source.getAttacker() instanceof LightningEntity || source.getSource() instanceof LightningEntity) {
            return 0.0f;
        }
        return amount;
    }

    @Override public void onAttack(ServerPlayerEntity attacker, Entity target) {
        if (!YPItemData.hasFlag(attacker.getMainHandStack(), "YPThorMace")
                || !(target instanceof LivingEntity living)
                || !YPConfig.getInstance().thor_mace_lightning_on_hit) return;
        if (areAllies(attacker, living)) return;
        if (attacker.getEntityWorld() instanceof ServerWorld world) {
            strikeLightning(world, living.getX(), living.getY(), living.getZ());
        }
    }

    private boolean areAllies(ServerPlayerEntity player, LivingEntity target) {
        return target instanceof ServerPlayerEntity other
                && AllianceManager.get(player.getEntityWorld().getServer()).isAlly(player, other);
    }

    private void strikeLightning(ServerWorld world, double x, double y, double z) {
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(x, y, z);
            world.spawnEntity(lightning);
        }
    }

    private static int clampCount(int value) {
        return Math.max(0, Math.min(1000, value));
    }

    private static int clampEnchantLevel(int value) {
        return Math.max(0, Math.min(255, value));
    }

    @Override public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return YPItemData.hasFlag(stack, "YPThorMace");
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) restoreHammerState(server, id);
        boolean hammerStillOut = thrownHammers.containsKey(id)
                || (server != null && PowerManager.get(server).getThorHammerState(id) != null);

        windups.remove(id);
        launchWindows.remove(id);
        launchCooldowns.remove(id);
        bufferedLaunches.remove(id);
        steeringTicks.remove(id);
        chainableLaunches.remove(id);
        queuedChainLaunches.remove(id);
        autoLaunchAfterWindup.remove(id);
        flightChainCueShown.remove(id);
        awaitingRechargeCue.remove(id);
        rageCooldowns.remove(id);
        swapCooldowns.remove(id);
        throwCooldowns.remove(id);

        // Changing someone's assigned power is not a recall. Keep the core and owner state
        // intact; the owner's death or a later Thor recall remains the only normal cleanup path.
        if (!hammerStillOut && findMace(player).isEmpty()) player.giveOrDropStack(createMace(player));
    }

    private ItemStack findMace(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (YPItemData.hasFlag(stack, "YPThorMace")) return stack;
        }
        return ItemStack.EMPTY;
    }

    private void removeMaceFromInventory(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (YPItemData.hasFlag(player.getInventory().getStack(i), "YPThorMace")) {
                player.getInventory().setStack(i, ItemStack.EMPTY);
            }
        }
    }

    private void ensureMace(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) restoreHammerState(server, player.getUuid());
        if (thrownHammers.containsKey(player.getUuid())) return;
        ItemStack mace = findMace(player);
        if (mace.isEmpty()) {
            player.giveOrDropStack(createMace(player));
        } else {
            applyMaceEnchantments(player, mace, currentMaceMode(mace));
            mace.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        }
    }

    private ItemStack createMace(ServerPlayerEntity player) {
        ItemStack mace = new ItemStack(Items.MACE);
        YPItemData.setFlag(mace, "YPThorMace", true);
        applyMaceEnchantments(player, mace, 0);
        mace.set(DataComponentTypes.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
        return mace;
    }

    private void applyMaceEnchantments(ServerPlayerEntity player, ItemStack mace, int mode) {
        YPConfig config = YPConfig.getInstance();
        YPItemData.setFlag(mace, "YPThorMode1", mode == 1);
        YPItemData.setFlag(mace, "YPThorMode2", mode == 2);
        YPItemData.setFlag(mace, "YPThorMode3", mode == 3);
        YPItemData.setFlag(mace, "YPThorMode4", mode == 4);

        var registry = player.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
        mace.set(DataComponentTypes.ENCHANTMENTS, net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);
        EnchantmentHelper.apply(mace, builder -> {
            int unbreaking = clampEnchantLevel(config.thor_mace_unbreaking_level);
            int mending = clampEnchantLevel(config.thor_mace_mending_level);
            int vanishing = clampEnchantLevel(config.thor_mace_vanishing_curse_level);
            int fireAspect = clampEnchantLevel(config.thor_mace_fire_aspect_level);
            int windBurst = clampEnchantLevel(config.thor_mace_wind_burst_level);
            int modeLevel = clampEnchantLevel(modeLevel(config, mode));

            if (unbreaking > 0) builder.add(registry.getOrThrow(Enchantments.UNBREAKING), unbreaking);
            if (mending > 0) builder.add(registry.getOrThrow(Enchantments.MENDING), mending);
            if (vanishing > 0) builder.add(registry.getOrThrow(Enchantments.VANISHING_CURSE), vanishing);
            if (fireAspect > 0) builder.add(registry.getOrThrow(Enchantments.FIRE_ASPECT), fireAspect);
            if (windBurst > 0) builder.add(registry.getOrThrow(Enchantments.WIND_BURST), windBurst);
            if (modeLevel > 0) {
                switch (mode) {
                    case 1 -> builder.add(registry.getOrThrow(Enchantments.BREACH), modeLevel);
                    case 2 -> builder.add(registry.getOrThrow(Enchantments.SHARPNESS), modeLevel);
                    case 3 -> builder.add(registry.getOrThrow(Enchantments.SMITE), modeLevel);
                    case 4 -> builder.add(registry.getOrThrow(Enchantments.BANE_OF_ARTHROPODS), modeLevel);
                    default -> builder.add(registry.getOrThrow(Enchantments.DENSITY), modeLevel);
                }
            }
        });
    }
}
