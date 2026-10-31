package com.yppowers.power.impl;

import com.yppowers.config.YPConfig;
import com.yppowers.energy.EnergyManager;
import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.network.YPNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.particle.DragonBreathParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.collection.WeightedPool;
import net.minecraft.world.World;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.HitResult;

import java.util.*;

public class ShadowArcherPower extends Power {
    private final Map<UUID, ArrowType> selectedArrow = new HashMap<>();
    private final Map<UUID, Set<ArrowType>> unlockedArrows = new HashMap<>();
    private final Map<UUID, Integer> morphCooldowns = new HashMap<>();
    private final Map<UUID, EntityType<?>> currentMorph = new HashMap<>();
    private final Map<UUID, ArrowType> arrowTypes = new HashMap<>();
    private final Set<UUID> processedArrows = new HashSet<>();
    private final Map<UUID, UUID> arrowOwners = new HashMap<>();
    private final Map<UUID, Entity> morphDisguises = new HashMap<>();
    private final Map<UUID, MorphViewState> syncedMorphViews = new HashMap<>();
    private long lastArrowCleanupTick = Long.MIN_VALUE;
    private final Map<UUID, Integer> railshotHeldTicks = new HashMap<>();
    private final Map<UUID, Integer> railshotStreak = new HashMap<>();
    private final Map<UUID, Boolean> railshotWasHolding = new HashMap<>();
    private final Map<UUID, Integer> railshotArrowLevel = new HashMap<>();

    // Morph-specific ability cooldowns and a reversible snapshot of state changed by a form.
    private final Map<UUID, EnumMap<ShadowArcherMorphRegistry.Ability, Integer>> morphAbilityCooldowns = new HashMap<>();
    private final Map<UUID, Map<RegistryEntry<StatusEffect>, StatusEffectInstance>> morphSavedEffects = new HashMap<>();
    private final Map<UUID, FlightSnapshot> morphSavedFlight = new HashMap<>();
    private final Map<UUID, Text> morphSavedNames = new HashMap<>();
    private final Map<UUID, Boolean> morphSavedNameVisibility = new HashMap<>();
    private final Set<UUID> morphStateInitialized = new HashSet<>();
    private static final List<RegistryEntry<StatusEffect>> MORPH_MANAGED_EFFECTS = List.of(
            StatusEffects.INVISIBILITY, StatusEffects.SPEED, StatusEffects.STRENGTH,
            StatusEffects.JUMP_BOOST, StatusEffects.RESISTANCE, StatusEffects.FIRE_RESISTANCE,
            StatusEffects.WATER_BREATHING, StatusEffects.DOLPHINS_GRACE, StatusEffects.NIGHT_VISION,
            StatusEffects.SLOW_FALLING, StatusEffects.REGENERATION, StatusEffects.POISON,
            StatusEffects.WEAKNESS, StatusEffects.SLOWNESS, StatusEffects.BLINDNESS,
            StatusEffects.LEVITATION, StatusEffects.GLOWING
    );

    private record FlightSnapshot(boolean allowFlying, boolean flying) {}
    private record MorphViewState(boolean morphed, int disguiseEntityId, float eyeHeight, float entityHeight) {}

    @Override public PowerType getType() { return PowerType.SHADOW_ARCHER; }

    @Override public void onAssign(ServerPlayerEntity player) { initializePlayer(player); }
    @Override public void onPlayerJoin(ServerPlayerEntity player) { initializePlayer(player); }
    private void initializePlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        Set<ArrowType> unlocked = unlockedArrows.computeIfAbsent(uuid, k -> new HashSet<>());
        unlocked.add(ArrowType.SPLIT_SHOT);
        if (player.getEntityWorld().getServer() != null) {
            unlocked.addAll(PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherUnlockedArrows(uuid));
        }
        selectedArrow.putIfAbsent(uuid, ArrowType.SPLIT_SHOT);
        YPNetworking.syncShadowArcherMorphCatalog(player);
        // Reset stale client camera state on assignment/join. The active form, if any,
        // is sent again from refreshDisguise once its real dimensions are available.
        syncMorphView(player, false, -1, 1.62F, 1.8F, true);
    }

    @Override public void onQuaternaryActivate(ServerPlayerEntity player) {
        Set<ArrowType> unlocked = unlockedArrows.computeIfAbsent(player.getUuid(), k -> new HashSet<>(Collections.singleton(ArrowType.SPLIT_SHOT)));
        List<ArrowType> list = new ArrayList<>();
        for (ArrowType type : ArrowType.values()) if (unlocked.contains(type)) list.add(type);
        if (list.isEmpty()) list.add(ArrowType.SPLIT_SHOT);
        ArrowType current = selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
        int index = list.indexOf(current);
        if (index < 0) index = 0;
        if (list.size() == 1) {
            player.sendMessage(Text.literal(
                    "Only Split Shot is unlocked. Carry 8 arrows plus a themed item to unlock more: " +
                    "Ender Eye = Homing, TNT = TNT Arrow, Lightning Rod = Lightning, Packed Ice = Ice Prison."
            ), true);
            selectedArrow.put(player.getUuid(), ArrowType.SPLIT_SHOT);
            return;
        }
        ArrowType next = list.get((index + 1) % list.size());
        selectedArrow.put(player.getUuid(), next);
        // Switching arrow types always clears Railshot draw accumulation; an arrow switch
        // cannot preserve or exploit an in-progress charged shot.
        railshotHeldTicks.remove(player.getUuid()); railshotStreak.remove(player.getUuid()); railshotWasHolding.remove(player.getUuid());
        player.sendMessage(Text.literal("Selected Arrow: " + next.getDisplayName()), true);
    }

    @Override public void onTertiaryActivate(ServerPlayerEntity player) {
        // Kept for non-network invocation paths; normal V key requests open the UI in YPNetworking.
        player.sendMessage(Text.literal("Open the Shadow Archer morph menu with V."), true);
    }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) { activateMorphSlot(player, 0); }
    @Override public void onSecondaryActivate(ServerPlayerEntity player) { activateMorphSlot(player, 1); }
    @Override public void onAlternateActivate(ServerPlayerEntity player) { activateMorphSlot(player, 2); }

    public void requestMorph(ServerPlayerEntity player, String requestedName) {
        UUID uuid = player.getUuid();
        String requested = requestedName == null ? "" : requestedName.trim();
        if (requested.equalsIgnoreCase("human") || requested.equalsIgnoreCase("reset") || requested.equalsIgnoreCase("none")) {
            // Returning to human is always allowed; a cooldown should never trap a player in a form.
            endMorph(player);
            startMorphCooldown(uuid);
            player.sendMessage(Text.literal("Returned to human form."), true);
            return;
        }
        int cooldown = morphCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            player.sendMessage(Text.literal("Morph on cooldown: " + ((cooldown + 19) / 20) + "s"), true);
            return;
        }
        if (requested.isEmpty()) {
            player.sendMessage(Text.literal("Enter a mob name or registry ID."), true);
            return;
        }

        EntityType<?> matched = findKilledMob(player, requested);
        if (matched == null) {
            player.sendMessage(Text.literal("You must defeat that mob before you can morph into it."), true);
            return;
        }
        if (matched.equals(currentMorph.get(uuid))) {
            player.sendMessage(Text.literal("You are already morphed into " + matched.getName().getString() + "."), true);
            return;
        }

        endMorph(player);
        currentMorph.put(uuid, matched);
        beginMorphState(player);
        startMorphCooldown(uuid);
        refreshDisguise(player, matched);
        ShadowArcherMorphRegistry.Profile profile = ShadowArcherMorphRegistry.forEntityType(matched);
        player.sendMessage(Text.literal("Morphed into " + profile.displayName() + ". G: "
                + profile.primary().displayName() + " | H: " + profile.secondary().displayName()
                + " | R: " + profile.alternate().displayName()), true);
    }

    private void startMorphCooldown(UUID uuid) {
        int ticks = Math.max(0, YPConfig.getInstance().shadow_archer_morph_cooldown_ticks);
        if (ticks == 0) morphCooldowns.remove(uuid);
        else morphCooldowns.put(uuid, ticks);
    }

    private EntityType<?> findKilledMob(ServerPlayerEntity player, String requested) {
        if (player.getEntityWorld().getServer() == null) return null;
        Set<String> killedIds = PowerManager.get(player.getEntityWorld().getServer()).getShadowArcherKills(player.getUuid());
        String normalized = requested.toLowerCase(Locale.ROOT).replace(' ', '_');
        // Resolve the request directly first. The catalog sends namespaced registry IDs,
        // while the menu submits path-only IDs (e.g. "spider"); compare canonical IDs
        // before the fallback loop so formatting differences cannot reject a real unlock.
        Identifier requestedId = Identifier.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        if (requestedId != null) {
            String canonical = requestedId.toString();
            if (killedIds.contains(canonical) && "minecraft".equals(requestedId.getNamespace())) {
                EntityType<?> direct = Registries.ENTITY_TYPE.getOptionalValue(requestedId).orElse(null);
                if (direct != null && ShadowArcherMorphRegistry.getVanillaFormIds().contains(requestedId.getPath())) return direct;
            }
        }
        for (String rawId : killedIds) {
            Identifier id = Identifier.tryParse(rawId);
            if (id == null) continue;
            EntityType<?> type = Registries.ENTITY_TYPE.getOptionalValue(id).orElse(null);
            if (type == null) continue;
            // This morph catalog is intentionally vanilla-mob-only. Do not let a forged
            // request or an unlock saved for a non-mob entity bypass what the UI exposes.
            if (!"minecraft".equals(id.getNamespace()) || !ShadowArcherMorphRegistry.getVanillaFormIds().contains(id.getPath())) continue;
            if (normalized.equals(rawId.toLowerCase(Locale.ROOT))
                    || normalized.equals(id.getPath().toLowerCase(Locale.ROOT))
                    || requested.equalsIgnoreCase(type.getName().getString())) return type;
        }
        return null;
    }

    public void recordKill(ServerPlayerEntity player, EntityType<?> entityType) {
        if (entityType != null && player.getEntityWorld().getServer() != null) {
            PowerManager.get(player.getEntityWorld().getServer()).recordShadowArcherKill(player.getUuid(), entityType);
            YPNetworking.syncShadowArcherMorphCatalog(player);
        }
    }

    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        UUID uuid = player.getUuid();
        Entity oldDisguise = morphDisguises.remove(uuid);
        if (oldDisguise != null && !oldDisguise.isRemoved()) oldDisguise.discard();

        // The new player object should inherit the pre-morph effects/name/flight state, not
        // the temporary effects that happened to be active when death occurred.
        Map<RegistryEntry<StatusEffect>, StatusEffectInstance> savedEffects = morphSavedEffects.remove(uuid);
        if (savedEffects != null) {
            for (RegistryEntry<StatusEffect> effect : MORPH_MANAGED_EFFECTS) player.removeStatusEffect(effect);
            for (Map.Entry<RegistryEntry<StatusEffect>, StatusEffectInstance> entry : savedEffects.entrySet()) {
                player.addStatusEffect(new StatusEffectInstance(entry.getValue()));
            }
        }
        FlightSnapshot flight = morphSavedFlight.remove(uuid);
        if (flight != null && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().allowFlying = flight.allowFlying();
            player.getAbilities().flying = flight.flying();
            player.sendAbilitiesUpdate();
        }
        if (morphSavedNames.containsKey(uuid)) player.setCustomName(morphSavedNames.remove(uuid));
        Boolean visible = morphSavedNameVisibility.remove(uuid);
        if (visible != null) player.setCustomNameVisible(visible);
        morphStateInitialized.remove(uuid);
        // Ability cooldowns intentionally survive death so respawning cannot reset them.
        // The selected form persists through death; tick() rebuilds the disguise and ability state.
    }

    public void onPlayerDisconnect(ServerPlayerEntity player) {
        endMorph(player, false);
        syncedMorphViews.remove(player.getUuid());
        railshotHeldTicks.remove(player.getUuid()); railshotWasHolding.remove(player.getUuid()); railshotStreak.remove(player.getUuid());
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        endMorph(player);
        selectedArrow.remove(player.getUuid());
        unlockedArrows.remove(player.getUuid());
        morphCooldowns.remove(player.getUuid());
        morphAbilityCooldowns.remove(player.getUuid());
        railshotHeldTicks.remove(player.getUuid()); railshotStreak.remove(player.getUuid()); railshotWasHolding.remove(player.getUuid());
        // Killed-mob unlocks remain in this server session if the power is temporarily reassigned.
    }

    private void beginMorphState(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        if (morphStateInitialized.contains(uuid)) return;
        Map<RegistryEntry<StatusEffect>, StatusEffectInstance> snapshot = new HashMap<>();
        for (RegistryEntry<StatusEffect> effect : MORPH_MANAGED_EFFECTS) {
            StatusEffectInstance existing = player.getStatusEffect(effect);
            if (existing != null) snapshot.put(effect, new StatusEffectInstance(existing));
        }
        morphSavedEffects.put(uuid, snapshot);
        morphSavedFlight.put(uuid, new FlightSnapshot(player.getAbilities().allowFlying, player.getAbilities().flying));
        morphSavedNames.put(uuid, player.getCustomName());
        morphSavedNameVisibility.put(uuid, player.isCustomNameVisible());
        morphStateInitialized.add(uuid);
    }

    private void endMorph(ServerPlayerEntity player) {
        endMorph(player, true);
    }

    private void endMorph(ServerPlayerEntity player, boolean sendViewPacket) {
        UUID uuid = player.getUuid();
        currentMorph.remove(uuid);
        Entity disguise = morphDisguises.remove(uuid);
        if (disguise != null && !disguise.isRemoved()) disguise.discard();
        if (morphStateInitialized.remove(uuid)) {
            for (RegistryEntry<StatusEffect> effect : MORPH_MANAGED_EFFECTS) player.removeStatusEffect(effect);
            Map<RegistryEntry<StatusEffect>, StatusEffectInstance> saved = morphSavedEffects.remove(uuid);
            if (saved != null) {
                for (Map.Entry<RegistryEntry<StatusEffect>, StatusEffectInstance> entry : saved.entrySet()) {
                    player.addStatusEffect(new StatusEffectInstance(entry.getValue()));
                }
            }
            FlightSnapshot flight = morphSavedFlight.remove(uuid);
            if (flight != null && !player.isCreative() && !player.isSpectator()) {
                player.getAbilities().allowFlying = flight.allowFlying();
                player.getAbilities().flying = flight.flying();
                player.sendAbilitiesUpdate();
            }
            if (morphSavedNames.containsKey(uuid)) player.setCustomName(morphSavedNames.remove(uuid));
            Boolean visible = morphSavedNameVisibility.remove(uuid);
            if (visible != null) player.setCustomNameVisible(visible);
        } else {
            morphSavedEffects.remove(uuid);
            morphSavedFlight.remove(uuid);
            morphSavedNames.remove(uuid);
            morphSavedNameVisibility.remove(uuid);
        }
        syncMorphView(player, false, -1, 1.62F, 1.8F, sendViewPacket);
        // Keep active ability cooldowns across form changes and Human resets. They tick down
        // server-side even while the player is human, preventing cooldown bypass by switching.
    }

    private void refreshDisguise(ServerPlayerEntity player, EntityType<?> type) {
        UUID uuid = player.getUuid();
        Entity disguise = morphDisguises.get(uuid);
        if (disguise == null || disguise.isRemoved() || disguise.getEntityWorld() != player.getEntityWorld()
                || !type.equals(disguise.getType())) {
            if (disguise != null && !disguise.isRemoved()) disguise.discard();
            Entity replacement = type.create(((ServerWorld) player.getEntityWorld()), SpawnReason.MOB_SUMMONED);
            if (replacement == null) {
                endMorph(player);
                player.sendMessage(Text.literal("That mob cannot be used as a morph."), true);
                return;
            }
            replacement.setNoGravity(true);
            replacement.setInvulnerable(true);
            replacement.setSilent(true);
            replacement.setCustomName(null);
            if (replacement instanceof MobEntity mob) {
                mob.setAiDisabled(true);
                mob.setPersistent();
            }
            ((ServerWorld) player.getEntityWorld()).spawnEntity(replacement);
            morphDisguises.put(uuid, replacement);
            disguise = replacement;
        }
        applyMorphEffect(player, StatusEffects.INVISIBILITY, 0, 40);
        player.setCustomName(type.getName());
        player.setCustomNameVisible(false);
        disguise.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        disguise.setVelocity(Vec3d.ZERO);
        ((com.yppowers.mixin.EntityVelocityAccessor) disguise).invokeYppowersScheduleVelocityUpdate();
        if (disguise instanceof LivingEntity living) living.setHealth(living.getMaxHealth());
        // Use the actual spawned disguise's dimensions (not a hard-coded mob-name table),
        // then synchronize them for the owner's local first-/third-person camera.
        syncMorphView(player, true, disguise.getId(), disguise.getEyeHeight(disguise.getPose()), disguise.getHeight(), true);
    }

    private void syncMorphView(ServerPlayerEntity player, boolean morphed, int disguiseEntityId,
                               float eyeHeight, float entityHeight, boolean sendPacket) {
        UUID uuid = player.getUuid();
        float safeEyeHeight = Float.isFinite(eyeHeight) ? Math.max(0.1F, Math.min(8.0F, eyeHeight)) : 1.62F;
        float safeEntityHeight = Float.isFinite(entityHeight) ? Math.max(0.1F, Math.min(16.0F, entityHeight)) : 1.8F;
        int safeEntityId = morphed ? Math.max(0, disguiseEntityId) : -1;
        MorphViewState next = new MorphViewState(morphed, safeEntityId, safeEyeHeight, safeEntityHeight);
        MorphViewState previous = syncedMorphViews.put(uuid, next);
        if (sendPacket && !next.equals(previous)) {
            YPNetworking.syncShadowArcherMorphView(player, next.morphed(), next.disguiseEntityId(),
                    next.eyeHeight(), next.entityHeight());
        }
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        int cooldown = morphCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            if (cooldown == 1) morphCooldowns.remove(uuid);
            else morphCooldowns.put(uuid, cooldown - 1);
        }
        tickMorphAbilityCooldowns(uuid);
        boolean holdingBow = player.isUsingItem() && player.getActiveItem().isOf(Items.BOW)
                && selectedArrow.getOrDefault(uuid, ArrowType.SPLIT_SHOT) == ArrowType.RAILSHOT;
        if (holdingBow) {
            railshotWasHolding.put(uuid, true);
            railshotHeldTicks.put(uuid, Math.min(1000, railshotHeldTicks.getOrDefault(uuid, 0) + 1));
        } else if (railshotWasHolding.getOrDefault(uuid, false)) {
            railshotWasHolding.put(uuid, false);
            railshotHeldTicks.put(uuid, 0);
        }
        int streak = railshotStreak.getOrDefault(uuid, 0);
        if (!holdingBow && streak > 0 && player.getEntityWorld().getServer() != null
                && player.getEntityWorld().getServer().getTicks() % Math.max(1, YPConfig.getInstance().shadow_archer_railshot_charge_decay_ticks) == 0) {
            railshotStreak.put(uuid, Math.max(0, streak - 1));
        }
        EntityType<?> morph = currentMorph.get(uuid);
        if (morph != null) {
            beginMorphState(player);
            refreshDisguise(player, morph);
            tickMorphTraits(player, ShadowArcherMorphRegistry.forEntityType(morph));
        }

        checkArrowCrafting(player);
        processArrows(player);
        if (player.getEntityWorld().getServer() != null && player.getEntityWorld().getServer().getTicks() % 20 == 0
                && lastArrowCleanupTick != player.getEntityWorld().getServer().getTicks()) {
            lastArrowCleanupTick = player.getEntityWorld().getServer().getTicks();
            cleanupTrackedArrows(player.getEntityWorld().getServer());
        }
    }


    private void activateMorphSlot(ServerPlayerEntity player, int slot) {
        EntityType<?> type = currentMorph.get(player.getUuid());
        if (type == null) return; // Normal Shadow Archer bow/arrow behaviour remains unchanged.
        ShadowArcherMorphRegistry.Profile profile = ShadowArcherMorphRegistry.forEntityType(type);
        ShadowArcherMorphRegistry.Ability ability = switch (slot) {
            case 0 -> profile.primary();
            case 1 -> profile.secondary();
            default -> profile.alternate();
        };
        if (ability == ShadowArcherMorphRegistry.Ability.NONE) return;
        EnumMap<ShadowArcherMorphRegistry.Ability, Integer> cooldowns = morphAbilityCooldowns.computeIfAbsent(
                player.getUuid(), ignored -> new EnumMap<>(ShadowArcherMorphRegistry.Ability.class));
        int remaining = cooldowns.getOrDefault(ability, 0);
        if (remaining > 0) {
            player.sendMessage(Text.literal(ability.displayName() + " cooldown: " + ((remaining + 19) / 20) + "s"), true);
            return;
        }
        if (executeMorphAbility(player, ability)) {
            int ticks = abilityCooldownTicks(ability);
            if (ticks > 0) cooldowns.put(ability, ticks);
        }
    }

    private void tickMorphAbilityCooldowns(UUID uuid) {
        EnumMap<ShadowArcherMorphRegistry.Ability, Integer> cooldowns = morphAbilityCooldowns.get(uuid);
        if (cooldowns == null || cooldowns.isEmpty()) return;
        cooldowns.replaceAll((ability, ticks) -> Math.max(0, ticks - 1));
        cooldowns.values().removeIf(ticks -> ticks <= 0);
        if (cooldowns.isEmpty()) morphAbilityCooldowns.remove(uuid);
    }

    private int abilityCooldownTicks(ShadowArcherMorphRegistry.Ability ability) {
        return switch (ability) {
            case TELEPORT -> 80;
            case PHASE_DASH -> 60;
            case DRAGON_BREATH, DRAGON_ROAR, WITHER_BLAST, WITHER_BARRAGE, SONIC_BOOM, CREEPER_BLAST -> 100;
            case GROUND_SLAM, GUARD, MAGIC_FANGS, WITCH_BREW, WEB_SNARE, SPORE_CLOUD, SHULKER_LIFT,
                    HEALING_PULSE, UTILITY_PULSE, ARMADILLO_ROLL, TRADE_WINDS -> 80;
            case CHARGE, EQUINE_DASH, HEADBUTT, AQUA_DASH, DEEP_DASH, AIR_DASH, FOX_DASH, FROG_LEAP, BOUNCE, SLIME_SLAM, GUST -> 45;
            case LAVA_DASH -> 60;
            case RANGED_SHOT, FIRE_BLAST, FIRE_BURST, BEAM, SNOWBURST, SAND_SHOT -> 50;
            case SWORD_SLASH, POISON_BITE, INFECTION_BITE, BEE_STING, ROOT_PULSE, DARK_BURST, ECHO_PULSE,
                    PUFFER_BURST, HOWL -> 60;
            case NONE -> 0;
        };
    }

    private boolean executeMorphAbility(ServerPlayerEntity player, ShadowArcherMorphRegistry.Ability ability) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        double px = player.getX();
        double py = player.getY() + 1.0;
        double pz = player.getZ();
        switch (ability) {
            case DRAGON_BREATH -> {
                damageCone(player, 20, 35, 9.0F, StatusEffects.WEAKNESS, 0, 80, 0.55);
                world.spawnParticles(DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), px + look.x * 2, py + look.y * 2, pz + look.z * 2,
                        36, 1.0, 0.8, 1.0, 0.03);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
                return true;
            }
            case DRAGON_ROAR -> {
                damageRadius(player, 7.0, 7.0F, StatusEffects.WEAKNESS, 0, 60, 1.2);
                world.spawnParticles(DragonBreathParticleEffect.of(ParticleTypes.DRAGON_BREATH, 1.0F), px, py, pz, 45, 2.0, 1.2, 2.0, 0.05);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.6f);
                return true;
            }
            case WITHER_BARRAGE -> {
                damageCone(player, 26, 9, 8.0F, StatusEffects.WITHER, 1, 100, 0.25);
                world.spawnParticles(ParticleTypes.SOUL, px + look.x * 2, py + look.y * 2, pz + look.z * 2,
                        26, 0.6, 0.6, 0.6, 0.02);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.8f, 0.6f);
                return true;
            }
            case WITHER_BLAST -> {
                damageRadius(player, 6.0, 7.0F, StatusEffects.WITHER, 1, 80, 0.9);
                world.spawnParticles(ParticleTypes.SOUL, px, py, pz, 30, 1.4, 1.0, 1.4, 0.03);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.65f);
                return true;
            }
            case SONIC_BOOM -> {
                damageCone(player, 22, 5, 10.0F, StatusEffects.SLOWNESS, 0, 30, 1.0);
                world.spawnParticles(ParticleTypes.SONIC_BOOM, px + look.x * 5, py + look.y * 5, pz + look.z * 5,
                        1, 0, 0, 0, 0);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.55f);
                return true;
            }
            case GROUND_SLAM -> {
                damageRadius(player, 5.0, 8.0F, StatusEffects.SLOWNESS, 0, 35, 1.0);
                world.spawnParticles(ParticleTypes.CLOUD, px, player.getY() + 0.15, pz, 30, 1.4, 0.15, 1.4, 0.04);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.9f, 0.75f);
                return true;
            }
            case GUARD, ARMADILLO_ROLL -> {
                applyMorphEffect(player, StatusEffects.RESISTANCE, 1, ability == ShadowArcherMorphRegistry.Ability.ARMADILLO_ROLL ? 60 : 50);
                applyMorphEffect(player, StatusEffects.SLOWNESS, 0, 30);
                world.spawnParticles(ParticleTypes.CRIT, px, py, pz, 18, 0.75, 0.8, 0.75, 0.1);
                playMorphSound(player, SoundEvents.BLOCK_GLASS_BREAK, 0.5f, 0.7f);
                return true;
            }
            case BEAM -> {
                damageCone(player, 27, 4, 7.0F, StatusEffects.SLOWNESS, 0, 30, 0.15);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, px + look.x * 3, py + look.y * 3, pz + look.z * 3,
                        22, 0.55, 0.55, 0.55, 0.02);
                return true;
            }
            case FIRE_BLAST -> {
                damageCone(player, 22, 17, 7.0F, StatusEffects.WEAKNESS, 0, 60, 0.45);
                world.spawnParticles(ParticleTypes.FLAME, px + look.x * 2, py + look.y * 2, pz + look.z * 2,
                        28, 0.8, 0.7, 0.8, 0.05);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.7f, 1.2f);
                return true;
            }
            case FIRE_BURST -> {
                damageRadius(player, 4.5, 6.0F, StatusEffects.WEAKNESS, 0, 40, 0.75);
                applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 100);
                world.spawnParticles(ParticleTypes.FLAME, px, py, pz, 35, 1.2, 0.8, 1.2, 0.04);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);
                return true;
            }
            case MAGIC_FANGS -> {
                damageCone(player, 12, 25, 7.0F, StatusEffects.SLOWNESS, 1, 40, 0.35);
                world.spawnParticles(ParticleTypes.ENCHANT, px + look.x * 4, py + look.y * 2, pz + look.z * 4,
                        24, 1.0, 0.7, 1.0, 0.05);
                playMorphSound(player, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
                return true;
            }
            case WITCH_BREW, SPORE_CLOUD -> {
                RegistryEntry<StatusEffect> effect = ability == ShadowArcherMorphRegistry.Ability.WITCH_BREW
                        ? StatusEffects.WEAKNESS : StatusEffects.POISON;
                damageRadius(player, 5.0, 3.5F, effect, 0, 100, 0.2);
                world.spawnParticles(ParticleTypes.WITCH, px, py, pz, 36, 1.1, 0.8, 1.1, 0.04);
                playMorphSound(player, SoundEvents.BLOCK_BREWING_STAND_BREW, 0.8f, 1.0f);
                return true;
            }
            case TELEPORT -> {
                if (!safeMorphTeleport(player, 8.0)) {
                    player.sendMessage(Text.literal("No safe teleport destination in front of you."), true);
                    return false;
                }
                world.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 24, 0.5, 0.8, 0.5, 0.15);
                world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.5, 0.8, 0.5, 0.15);
                playMorphSound(player, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, 0.9f, 1.0f);
                return true;
            }
            case DARK_BURST -> {
                damageRadius(player, 5.0, 5.5F, StatusEffects.BLINDNESS, 0, 40, 0.65);
                world.spawnParticles(ParticleTypes.PORTAL, px, py, pz, 30, 1.1, 0.9, 1.1, 0.06);
                return true;
            }
            case WEB_SNARE -> {
                damageCone(player, 11, 28, 3.5F, StatusEffects.SLOWNESS, 3, 60, 0.15);
                world.spawnParticles(ParticleTypes.CLOUD, px + look.x * 3, py + look.y * 2, pz + look.z * 3,
                        25, 0.7, 0.7, 0.7, 0.01);
                return true;
            }
            case POISON_BITE, BEE_STING -> {
                damageCone(player, 4.5, 52, ability == ShadowArcherMorphRegistry.Ability.BEE_STING ? 5.0F : 6.0F,
                        StatusEffects.POISON, 0, ability == ShadowArcherMorphRegistry.Ability.BEE_STING ? 100 : 80, 0.3);
                world.spawnParticles(ParticleTypes.CRIT, px + look.x, py + look.y, pz + look.z, 12, 0.4, 0.4, 0.4, 0.04);
                return true;
            }
            case PHASE_DASH -> {
                if (!safeMorphTeleport(player, 5.0)) {
                    applyDash(player, 1.25, 0.2);
                }
                applyMorphEffect(player, StatusEffects.SLOW_FALLING, 0, 40);
                world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.45, 0.7, 0.45, 0.1);
                playMorphSound(player, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, 0.8f, 1.3f);
                return true;
            }
            case AIR_DASH -> {
                applyDash(player, 1.2, 0.55);
                applyMorphEffect(player, StatusEffects.SLOW_FALLING, 0, 45);
                world.spawnParticles(ParticleTypes.CLOUD, px, py, pz, 16, 0.4, 0.4, 0.4, 0.04);
                return true;
            }
            case GUST -> {
                pushCone(player, 14, 30, 1.4, 0.55);
                world.spawnParticles(ParticleTypes.CLOUD, px + look.x * 2, py + look.y * 2, pz + look.z * 2,
                        32, 0.75, 0.75, 0.75, 0.05);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.65f, 1.3f);
                return true;
            }
            case CHARGE, EQUINE_DASH, HEADBUTT -> {
                applyDash(player, ability == ShadowArcherMorphRegistry.Ability.EQUINE_DASH ? 1.65 : 1.4,
                        ability == ShadowArcherMorphRegistry.Ability.EQUINE_DASH ? 0.48 : 0.15);
                damageCone(player, 5.5, 42, ability == ShadowArcherMorphRegistry.Ability.EQUINE_DASH ? 5.0F : 7.0F,
                        null, 0, 0, 1.0);
                world.spawnParticles(ParticleTypes.CLOUD, px, py, pz, 16, 0.65, 0.4, 0.65, 0.03);
                playMorphSound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.85f, 0.8f);
                return true;
            }
            case CREEPER_BLAST -> {
                // Controlled, player-compatible blast: nearby non-allies are pushed/damaged, blocks and the user are safe.
                damageRadius(player, 4.5, 9.0F, StatusEffects.WEAKNESS, 0, 50, 1.5);
                world.spawnParticles(ParticleTypes.EXPLOSION, px, py, pz, 1, 0, 0, 0, 0);
                world.spawnParticles(ParticleTypes.SMOKE, px, py, pz, 35, 1.2, 0.8, 1.2, 0.08);
                playMorphSound(player, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
                return true;
            }
            case BOUNCE, SLIME_SLAM -> {
                Vec3d velocity = player.getVelocity();
                player.setVelocity(velocity.x + look.x * 0.75, Math.max(0.9, velocity.y + 1.05), velocity.z + look.z * 0.75);
                ((com.yppowers.mixin.EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
                if (ability == ShadowArcherMorphRegistry.Ability.SLIME_SLAM) {
                    damageRadius(player, 3.5, 5.0F, StatusEffects.SLOWNESS, 0, 30, 0.6);
                }
                world.spawnParticles(ParticleTypes.ITEM_SLIME, px, player.getY() + 0.2, pz, 20, 0.55, 0.15, 0.55, 0.03);
                return true;
            }
            case AQUA_DASH, DEEP_DASH -> {
                applyDash(player, player.isTouchingWater() ? 1.65 : 1.05, 0.15);
                applyMorphEffect(player, StatusEffects.WATER_BREATHING, 0, 100);
                if (player.isTouchingWater()) applyMorphEffect(player, StatusEffects.DOLPHINS_GRACE, 1, 100);
                world.spawnParticles(ParticleTypes.BUBBLE, px, py, pz, 24, 0.5, 0.7, 0.5, 0.06);
                return true;
            }
            case LAVA_DASH -> {
                // Strider form gets a stronger escape/boost movement while actually in lava,
                // rather than incorrectly treating the Nether dimension itself as lava.
                boolean inLava = player.isInLava();
                applyDash(player, inLava ? 1.95 : 1.15, inLava ? 0.68 : 0.22);
                applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 80);
                world.spawnParticles(ParticleTypes.FLAME, px, py, pz, inLava ? 28 : 14,
                        0.55, 0.45, 0.55, 0.035);
                world.spawnParticles(ParticleTypes.SMOKE, px, py, pz, 10, 0.35, 0.25, 0.35, 0.015);
                playMorphSound(player, SoundEvents.ENTITY_BLAZE_SHOOT, 0.6f, 1.3f);
                return true;
            }
            case SNOWBURST -> {
                damageCone(player, 18, 22, 3.0F, StatusEffects.SLOWNESS, 1, 80, 0.25);
                world.spawnParticles(ParticleTypes.SNOWFLAKE, px + look.x * 2, py + look.y * 2, pz + look.z * 2,
                        30, 0.8, 0.7, 0.8, 0.04);
                playMorphSound(player, SoundEvents.BLOCK_GLASS_BREAK, 0.6f, 1.4f);
                return true;
            }
            case FROG_LEAP -> {
                Vec3d v = player.getVelocity();
                player.setVelocity(v.x + look.x * 0.9, Math.max(1.35, v.y + 0.9), v.z + look.z * 0.9);
                ((com.yppowers.mixin.EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
                world.spawnParticles(ParticleTypes.SPLASH, px, player.getY(), pz, 20, 0.45, 0.2, 0.45, 0.02);
                return true;
            }
            case FOX_DASH -> {
                applyDash(player, 1.6, 0.25);
                applyMorphEffect(player, StatusEffects.SPEED, 1, 45);
                return true;
            }
            case HOWL -> {
                applyMorphEffect(player, StatusEffects.STRENGTH, 0, 100);
                applyMorphEffect(player, StatusEffects.SPEED, 0, 100);
                Box area = player.getBoundingBox().expand(8.0);
                for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e instanceof ServerPlayerEntity && e != player)) {
                    if (AllianceManager.get(world.getServer()).isAlly(player, target)) {
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 100, 0));
                        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 0));
                    }
                }
                world.spawnParticles(ParticleTypes.HEART, px, py, pz, 12, 0.9, 0.6, 0.9, 0.02);
                playMorphSound(player, SoundEvents.ENTITY_WOLF_SHAKE, 1.0f, 1.0f);
                return true;
            }
            case UTILITY_PULSE, TRADE_WINDS, ECHO_PULSE -> {
                Box area = player.getBoundingBox().expand(14.0);
                for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                        e -> e != player && !(e instanceof ServerPlayerEntity && AllianceManager.get(world.getServer()).isAlly(player, e)))) {
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0));
                }
                applyMorphEffect(player, StatusEffects.REGENERATION, 0, 60);
                if (ability == ShadowArcherMorphRegistry.Ability.TRADE_WINDS) applyMorphEffect(player, StatusEffects.SPEED, 1, 100);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, px, py, pz, 24, 1.0, 0.8, 1.0, 0.03);
                playMorphSound(player, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.65f, 1.4f);
                return true;
            }
            case SHULKER_LIFT -> {
                damageCone(player, 20, 9, 4.0F, StatusEffects.LEVITATION, 0, 50, 0.15);
                world.spawnParticles(ParticleTypes.PORTAL, px + look.x * 3, py + look.y * 3, pz + look.z * 3,
                        22, 0.6, 0.6, 0.6, 0.03);
                return true;
            }
            case RANGED_SHOT, SAND_SHOT -> {
                RegistryEntry<StatusEffect> effect = ability == ShadowArcherMorphRegistry.Ability.SAND_SHOT
                        ? StatusEffects.BLINDNESS : null;
                damageCone(player, 24, 5, ability == ShadowArcherMorphRegistry.Ability.SAND_SHOT ? 4.5F : 6.0F,
                        effect, 0, 40, 0.15);
                world.spawnParticles(ParticleTypes.CRIT, px + look.x * 2, py + look.y * 2, pz + look.z * 2,
                        18, 0.25, 0.25, 0.25, 0.04);
                playMorphSound(player, SoundEvents.ENTITY_ARROW_SHOOT, 0.8f, 1.1f);
                return true;
            }
            case SWORD_SLASH, INFECTION_BITE -> {
                RegistryEntry<StatusEffect> effect = ability == ShadowArcherMorphRegistry.Ability.INFECTION_BITE
                        ? StatusEffects.WEAKNESS : null;
                damageCone(player, 4.8, 62, ability == ShadowArcherMorphRegistry.Ability.INFECTION_BITE ? 6.0F : 7.0F,
                        effect, 0, 60, 0.5);
                world.spawnParticles(ParticleTypes.SWEEP_ATTACK, px + look.x, py, pz + look.z,
                        6, 0.5, 0.4, 0.5, 0);
                playMorphSound(player, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.0f);
                return true;
            }
            case ROOT_PULSE -> {
                damageCone(player, 10, 38, 4.0F, StatusEffects.SLOWNESS, 1, 60, 0.1);
                world.spawnParticles(ParticleTypes.SMOKE, px + look.x * 2, py + look.y * 2, pz + look.z * 2,
                        20, 0.7, 0.5, 0.7, 0.02);
                return true;
            }
            case HEALING_PULSE -> {
                player.heal(5.0F);
                applyMorphEffect(player, StatusEffects.REGENERATION, 1, 60);
                Box area = player.getBoundingBox().expand(6.0);
                for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                        e -> e instanceof ServerPlayerEntity && AllianceManager.get(world.getServer()).isAlly(player, e))) {
                    target.heal(2.0F);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0));
                }
                world.spawnParticles(ParticleTypes.HEART, px, py, pz, 18, 0.8, 0.8, 0.8, 0.03);
                playMorphSound(player, SoundEvents.ENTITY_PLAYER_LEVELUP, 0.55f, 1.5f);
                return true;
            }
            case PUFFER_BURST -> {
                damageRadius(player, 4.0, 2.5F, StatusEffects.POISON, 0, 100, 0.6);
                world.spawnParticles(ParticleTypes.BUBBLE, px, py, pz, 30, 0.8, 0.7, 0.8, 0.04);
                return true;
            }
            case NONE -> { return false; }
        }
        return false;
    }

    private void tickMorphTraits(ServerPlayerEntity player, ShadowArcherMorphRegistry.Profile profile) {
        Set<ShadowArcherMorphRegistry.Trait> traits = profile.traits();
        if (traits.contains(ShadowArcherMorphRegistry.Trait.FLIGHT) && !player.isCreative() && !player.isSpectator()) {
            if (!player.getAbilities().allowFlying) {
                player.getAbilities().allowFlying = true;
                player.sendAbilitiesUpdate();
            }
        }
        if (traits.contains(ShadowArcherMorphRegistry.Trait.FIRE_RESISTANCE)) applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 40);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.AQUATIC)) {
            applyMorphEffect(player, StatusEffects.WATER_BREATHING, 0, 40);
            if (player.isTouchingWater()) applyMorphEffect(player, StatusEffects.DOLPHINS_GRACE, 1, 40);
        }
        if (traits.contains(ShadowArcherMorphRegistry.Trait.SPEED)) applyMorphEffect(player, StatusEffects.SPEED, 0, 40);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.STRENGTH)) applyMorphEffect(player, StatusEffects.STRENGTH, 0, 40);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.JUMP)) applyMorphEffect(player, StatusEffects.JUMP_BOOST, 0, 40);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.RESISTANCE)) applyMorphEffect(player, StatusEffects.RESISTANCE, 0, 40);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.SLOW_FALLING)) applyMorphEffect(player, StatusEffects.SLOW_FALLING, 0, 40);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.NIGHT_VISION)) applyMorphEffect(player, StatusEffects.NIGHT_VISION, 0, 300);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.REGENERATION)) applyMorphEffect(player, StatusEffects.REGENERATION, 0, 40);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.POISON_RESISTANCE)) player.removeStatusEffect(StatusEffects.POISON);
        if (traits.contains(ShadowArcherMorphRegistry.Trait.LAVA_ADAPTED) && player.isInLava()) {
            applyMorphEffect(player, StatusEffects.FIRE_RESISTANCE, 0, 40);
            applyMorphEffect(player, StatusEffects.SPEED, 1, 40);
        }
        if (traits.contains(ShadowArcherMorphRegistry.Trait.WALL_CLIMB) && player.horizontalCollision) {
            Vec3d velocity = player.getVelocity();
            if (velocity.y < 0.18) {
                player.setVelocity(velocity.x, 0.18, velocity.z);
                ((com.yppowers.mixin.EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
            }
        }
        if (traits.contains(ShadowArcherMorphRegistry.Trait.BOUNCY) && player.isOnGround() && player.isSprinting()) {
            // A light hop on a sprint-jump makes slime forms feel springy without forcing constant jumps.
            Vec3d velocity = player.getVelocity();
            if (player.fallDistance > 1.5F) {
                player.setVelocity(velocity.x, Math.max(velocity.y, 0.45), velocity.z);
                ((com.yppowers.mixin.EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
            }
        }
    }

    private void applyMorphEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier, int duration) {
        StatusEffectInstance current = player.getStatusEffect(effect);
        if (current == null || (current.getDuration() < 12 && current.getAmplifier() <= amplifier)) {
            player.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier, false, false, true));
        }
    }

    private void damageCone(ServerPlayerEntity player, double range, double halfAngleDegrees, float damage,
                            RegistryEntry<StatusEffect> effect, int amplifier, int duration, double knockback) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d origin = new Vec3d(player.getX(), player.getY() + 1.0, player.getZ());
        Vec3d direction = player.getRotationVec(1.0F).normalize();
        double minDot = Math.cos(Math.toRadians(halfAngleDegrees));
        Box box = new Box(player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range + 1.5, player.getZ() + range);
        AllianceManager alliances = AllianceManager.get(world.getServer());
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                target -> target != player && target.isAlive() && !alliances.isAlly(player, target))) {
            Vec3d toTarget = target.getBoundingBox().getCenter().subtract(origin);
            double distance = toTarget.length();
            if (distance > range || distance < 0.01 || direction.dotProduct(toTarget.multiply(1.0 / distance)) < minDot) continue;
            if (target.damage(world, world.getDamageSources().playerAttack(player), damage)) {
                if (effect != null && duration > 0) target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier));
                if (knockback > 0) pushEntity(target, toTarget.normalize(), knockback, 0.15);
            }
        }
    }

    private void damageRadius(ServerPlayerEntity player, double radius, float damage,
                              RegistryEntry<StatusEffect> effect, int amplifier, int duration, double knockback) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Box box = player.getBoundingBox().expand(radius);
        AllianceManager alliances = AllianceManager.get(world.getServer());
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                target -> target != player && target.isAlive() && !alliances.isAlly(player, target))) {
            double distance = target.getBoundingBox().getCenter().distanceTo(player.getEntityPos());
            if (distance > radius) continue;
            float scaledDamage = (float) Math.max(1.0, damage * (1.0 - 0.35 * distance / radius));
            if (target.damage(world, world.getDamageSources().playerAttack(player), scaledDamage)) {
                if (effect != null && duration > 0) target.addStatusEffect(new StatusEffectInstance(effect, duration, amplifier));
                if (knockback > 0) {
                    Vec3d away = target.getEntityPos().subtract(player.getEntityPos()).normalize();
                    pushEntity(target, away, knockback, 0.25);
                }
            }
        }
    }

    private void pushCone(ServerPlayerEntity player, double range, double halfAngleDegrees, double power, double vertical) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d origin = new Vec3d(player.getX(), player.getY() + 1.0, player.getZ());
        Box box = new Box(player.getX() - range, player.getY() - range, player.getZ() - range,
                player.getX() + range, player.getY() + range + 1.5, player.getZ() + range);
        AllianceManager alliances = AllianceManager.get(world.getServer());
        double minDot = Math.cos(Math.toRadians(halfAngleDegrees));
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.isAlive() && !alliances.isAlly(player, e))) {
            Vec3d diff = target.getBoundingBox().getCenter().subtract(origin);
            double distance = diff.length();
            if (distance < 0.01 || distance > range || look.dotProduct(diff.multiply(1.0 / distance)) < minDot) continue;
            pushEntity(target, diff.normalize(), power, vertical);
        }
    }

    private void pushEntity(LivingEntity target, Vec3d direction, double power, double vertical) {
        Vec3d flat = new Vec3d(direction.x, 0, direction.z).normalize();
        Vec3d current = target.getVelocity();
        target.setVelocity(current.x + flat.x * power, Math.max(current.y, vertical), current.z + flat.z * power);
        ((com.yppowers.mixin.EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
    }

    private void applyDash(ServerPlayerEntity player, double power, double vertical) {
        Vec3d look = player.getRotationVec(1.0F).normalize();
        Vec3d current = player.getVelocity();
        player.setVelocity(current.x + look.x * power, Math.max(current.y, vertical), current.z + look.z * power);
        ((com.yppowers.mixin.EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
    }

    private boolean safeMorphTeleport(ServerPlayerEntity player, double maxDistance) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        Vec3d look = player.getRotationVec(1.0F).normalize();
        BlockPos current = player.getBlockPos();
        for (int step = (int) maxDistance; step >= 2; step--) {
            int x = net.minecraft.util.math.MathHelper.floor(player.getX() + look.x * step);
            int y = current.getY();
            int z = net.minecraft.util.math.MathHelper.floor(player.getZ() + look.z * step);
            BlockPos feet = new BlockPos(x, y, z);
            if (!world.getBlockState(feet).isAir() || !world.getBlockState(feet.up()).isAir()
                    || world.getBlockState(feet.down()).isAir()) continue;
            player.teleport(world, x + 0.5, y, z + 0.5, Set.of(), player.getYaw(), player.getPitch(), false);
            return true;
        }
        return false;
    }

    private void playMorphSound(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float volume, float pitch) {
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private void playMorphSound(ServerPlayerEntity player, SoundEvent sound, float volume, float pitch) {
        player.getEntityWorld().playSound(null, player.getBlockPos(), sound,
                SoundCategory.PLAYERS, volume, pitch);
    }

    private void cleanupTrackedArrows(net.minecraft.server.MinecraftServer server) {
        Set<UUID> live = new HashSet<>();
        for (ServerWorld world : server.getWorlds()) {
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof ArrowEntity arrow) live.add(arrow.getUuid());
            }
        }
        arrowTypes.keySet().retainAll(live);
        arrowOwners.keySet().retainAll(live);
        processedArrows.retainAll(live);
    }

    private void checkArrowCrafting(ServerPlayerEntity player) {
        Set<ArrowType> unlocked = unlockedArrows.computeIfAbsent(player.getUuid(), k -> new HashSet<>(Collections.singleton(ArrowType.SPLIT_SHOT)));
        for (ArrowType type : ArrowType.values()) {
            if (unlocked.contains(type)) continue;
            if (hasItems(player, Items.ARROW, 8) && hasItems(player, type.getCraftItem(), 1)) {
                consumeItems(player, Items.ARROW, 8);
                consumeItems(player, type.getCraftItem(), 1);
                unlocked.add(type);
                if (player.getEntityWorld().getServer() != null) {
                    PowerManager.get(player.getEntityWorld().getServer())
                            .recordShadowArcherArrowUnlock(player.getUuid(), type);
                }
                player.sendMessage(Text.literal("Unlocked " + type.getDisplayName() + "!"), false);
            }
        }
    }

    private boolean hasItems(ServerPlayerEntity player, Item item, int count) {
        int total = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (stack.isOf(item)) total += stack.getCount();
        }
        return total >= count;
    }

    private void consumeItems(ServerPlayerEntity player, Item item, int count) {
        int needed = count;
        for (int i = 0; i < player.getInventory().size() && needed > 0; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isOf(item)) continue;
            int take = Math.min(stack.getCount(), needed);
            stack.decrement(take);
            needed -= take;
        }
    }

    /**
     * Registers an owned arrow at spawn time so its selected type is locked before it
     * can collide. The tick scan below remains as a fallback for unusual spawn paths.
     */
    public static boolean registerSpawnedArrow(ServerWorld world, ArrowEntity arrow) {
        if (world == null || world.getServer() == null
                || !(arrow.getOwner() instanceof ServerPlayerEntity player)) return true;
        PowerManager manager = PowerManager.get(world.getServer());
        if (manager.getPowerType(player.getUuid()) != PowerType.SHADOW_ARCHER) return true;
        Power registered = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
        if (!(registered instanceof ShadowArcherPower archer)
                || archer.processedArrows.contains(arrow.getUuid())) return true;
        if (GraceManager.isActive(world.getServer())) {
            // Grace turns the projectile into an ordinary arrow, with PvP damage blocked by
            // the combat mixin; it must never gain a delayed special effect after grace ends.
            GraceManager.blockAbility(player);
            archer.trackOrdinaryArrow(player, arrow);
            return true;
        }
        // Railshot alone requires a staged draw. Each consecutive Railshot increases its
        // required draw time until vanilla's ~1-second maximum; releasing without a valid
        // shot, changing arrow type, death, reassignment, or disconnect resets charge.
        ArrowType selected = archer.selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
        if (selected == ArrowType.RAILSHOT) {
            YPConfig cfg = YPConfig.getInstance();
            int streak = Math.max(0, archer.railshotStreak.getOrDefault(player.getUuid(), 0));
            int base = Math.max(1, cfg.shadow_archer_railshot_base_draw_ticks);
            int maximum = Math.max(base, Math.min(20, cfg.shadow_archer_railshot_max_draw_ticks));
            int required = Math.min(maximum, base + streak * Math.max(1, cfg.shadow_archer_railshot_draw_gain_ticks));
            int held = archer.railshotHeldTicks.getOrDefault(player.getUuid(), 0);
            if (held < required) {
                player.sendMessage(Text.literal("Railshot draw incomplete: " + held + "/" + required + " ticks.").formatted(net.minecraft.util.Formatting.RED), true);
                archer.railshotHeldTicks.put(player.getUuid(), 0);
                return false;
            }
            archer.railshotArrowLevel.put(arrow.getUuid(), streak);
            archer.railshotStreak.put(player.getUuid(), Math.min(10, streak + 1));
            archer.railshotHeldTicks.put(player.getUuid(), 0);
        }
        // Charge once for the original arrow. Split-shot child arrows are marked processed
        // before spawning, so they do not recursively charge or duplicate the cost.
        if (!EnergyManager.get(world.getServer()).trySpendDirect(player,
                YPConfig.getInstance().energy_cost_shadow_archer_arrow, "Shadow Archer arrow")) {
            return false;
        }
        archer.trackArrow(player, arrow);
        return true;
    }

    private void trackOrdinaryArrow(ServerPlayerEntity player, ArrowEntity arrow) {
        UUID arrowId = arrow.getUuid();
        if (!processedArrows.add(arrowId)) return;
        arrowTypes.put(arrowId, ArrowType.SPLIT_SHOT);
        arrowOwners.put(arrowId, player.getUuid());
    }

    private void trackArrow(ServerPlayerEntity player, ArrowEntity arrow) {
        if (!processedArrows.add(arrow.getUuid())) return;
        ArrowType selected = selectedArrow.getOrDefault(player.getUuid(), ArrowType.SPLIT_SHOT);
        arrowTypes.put(arrow.getUuid(), selected);
        arrowOwners.put(arrow.getUuid(), player.getUuid());
        if (selected == ArrowType.SPLIT_SHOT) {
            double baseDamage = ((com.yppowers.mixin.PersistentProjectileDamageAccessor) arrow).yppowers$getProjectileDamage();
            arrow.setDamage(baseDamage * 0.75);
            Vec3d velocity = arrow.getVelocity();
            for (int side : new int[]{-1, 1}) {
                ArrowEntity split = new ArrowEntity(EntityType.ARROW, ((ServerWorld) player.getEntityWorld()));
                split.setOwner(player);
                split.setPosition(arrow.getX(), arrow.getY(), arrow.getZ());
                double angle = Math.toRadians(15 * side);
                double x = velocity.x * Math.cos(angle) - velocity.z * Math.sin(angle);
                double z = velocity.x * Math.sin(angle) + velocity.z * Math.cos(angle);
                split.setVelocity(x, velocity.y, z, (float) velocity.length(), 0.1f);
                split.setDamage(baseDamage * 0.75);
                arrowTypes.put(split.getUuid(), ArrowType.SPLIT_SHOT);
                arrowOwners.put(split.getUuid(), player.getUuid());
                processedArrows.add(split.getUuid());
                ((ServerWorld) player.getEntityWorld()).spawnEntity(split);
            }
        } else if (selected == ArrowType.RAILSHOT) {
            // Spawn registration saved the charge level before vanilla finishes configuring the arrow.
            int level = Math.max(0, Math.min(10, railshotArrowLevel.getOrDefault(arrow.getUuid(), 0)));
            railshotArrowLevel.remove(arrow.getUuid());
            double speed = 1.0 + level * Math.max(0, YPConfig.getInstance().shadow_archer_railshot_speed_multiplier_per_charge);
            double damageMultiplier = 1.0 + level * Math.max(0, YPConfig.getInstance().shadow_archer_railshot_damage_multiplier_per_charge);
            float baseDamage = (float)((com.yppowers.mixin.PersistentProjectileDamageAccessor) arrow).yppowers$getProjectileDamage();
            arrow.setDamage(baseDamage * damageMultiplier);
            Vec3d velocity = arrow.getVelocity();
            if (velocity.lengthSquared() > 1.0e-6) arrow.setVelocity(velocity.normalize().multiply(velocity.length() * speed));
            ((com.yppowers.mixin.PersistentProjectilePierceInvoker) arrow).yppowers$setPierceLevel((byte)Math.min(4, 1 + level / 3));
            ((ServerWorld) arrow.getEntityWorld()).spawnParticles(ParticleTypes.ELECTRIC_SPARK, arrow.getX(), arrow.getY(), arrow.getZ(), 10, .1, .1, .1, .02);
        }
    }

    private void processArrows(ServerPlayerEntity player) {
        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        if (GraceManager.isActive(world.getServer())) {
            // During grace, power arrows behave as ordinary arrows. Mark them so an arrow
            // fired during grace cannot become a delayed special arrow when grace expires.
            for (Entity entity : world.iterateEntities()) {
                if (entity instanceof ArrowEntity arrow && arrow.getOwner() == player) {
                    UUID arrowId = arrow.getUuid();
                    if (!processedArrows.contains(arrowId)) GraceManager.blockAbility(player);
                    processedArrows.add(arrowId);
                    arrowTypes.put(arrowId, ArrowType.SPLIT_SHOT);
                    arrowOwners.put(arrowId, player.getUuid());
                }
            }
            return;
        }
        // Track over a snapshot because split-shot processing can add two new arrows.
        List<ArrowEntity> arrows = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof ArrowEntity arrow) arrows.add(arrow);
        }
        for (ArrowEntity arrow : arrows) {
            if (arrow.getOwner() == player && !processedArrows.contains(arrow.getUuid())) trackArrow(player, arrow);
            if (!player.getUuid().equals(arrowOwners.get(arrow.getUuid()))) continue;
            ArrowType type = arrowTypes.get(arrow.getUuid());
            if (type == ArrowType.HOMING && !arrow.isOnGround() && !arrow.isRemoved()) {
                Box box = arrow.getBoundingBox().expand(35.0);
                AllianceManager alliances = AllianceManager.get(world.getServer());
                List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, box,
                        target -> target != player && !alliances.isAlly(player, target) && target.squaredDistanceTo(arrow) <= 35.0 * 35.0);
                LivingEntity closest = null;
                double closestDistance = Double.MAX_VALUE;
                for (LivingEntity target : targets) {
                    double distance = arrow.squaredDistanceTo(target);
                    if (distance < closestDistance) { closestDistance = distance; closest = target; }
                }
                if (closest != null) {
                    Vec3d direction = closest.getBoundingBox().getCenter().subtract(arrow.getEntityPos()).normalize();
                    double speed = arrow.getVelocity().length();
                    arrow.setVelocity(arrow.getVelocity().lerp(direction.multiply(speed), 0.2));
                    ((com.yppowers.mixin.EntityVelocityAccessor) arrow).invokeYppowersScheduleVelocityUpdate();
                }
            }
        }
    }

    /** Called by the projectile collision mixin; returns true when a special arrow consumed the collision. */
    public static boolean handleProjectileCollision(ProjectileEntity projectile, HitResult hitResult) {
        if (!(projectile instanceof ArrowEntity arrow) || !(projectile.getOwner() instanceof ServerPlayerEntity player)) return false;
        if (player.getEntityWorld().getServer() == null) return false;
        PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
        if (manager.getPowerType(player.getUuid()) != PowerType.SHADOW_ARCHER) return false;
        Power registered = manager.getPowerInstance(PowerType.SHADOW_ARCHER);
        if (!(registered instanceof ShadowArcherPower archer)) return false;
        if (GraceManager.isActive(player.getEntityWorld().getServer())) return false;
        if (!archer.processedArrows.contains(arrow.getUuid())) archer.trackArrow(player, arrow);
        ArrowType type = archer.arrowTypes.get(arrow.getUuid());
        if (type == null || type == ArrowType.SPLIT_SHOT || type == ArrowType.HOMING || type == ArrowType.RAILSHOT) return false;
        ServerWorld world = (ServerWorld) projectile.getEntityWorld();
        switch (type) {
            case TNT -> world.createExplosion(
                    player,
                    null,
                    null,
                    arrow.getX(), arrow.getY(), arrow.getZ(),
                    2.0F,
                    false,
                    World.ExplosionSourceType.TNT,
                    ParticleTypes.EXPLOSION,
                    ParticleTypes.EXPLOSION_EMITTER,
                    WeightedPool.empty(),
                    net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE
            );
            case LIGHTNING -> {
                Entity hit = hitResult.getType() == HitResult.Type.ENTITY ? ((net.minecraft.util.hit.EntityHitResult) hitResult).getEntity() : null;
                double x = hit == null ? arrow.getX() : hit.getX();
                double y = hit == null ? arrow.getY() : hit.getY();
                double z = hit == null ? arrow.getZ() : hit.getZ();
                for (int i = 0; i < 2; i++) {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
                    if (bolt != null) { bolt.refreshPositionAndAngles(x, y, z, 0, 0); world.spawnEntity(bolt); }
                }
            }
            case ICE_PRISON -> {
                // Use the collision's exact location rather than the projectile's last tick
                // position. This also keeps entity-hit impacts reliable at high arrow speeds.
                Vec3d impact = hitResult.getPos();
                double radius = 3.5;
                Box area = new Box(
                        impact.x - radius, impact.y - radius, impact.z - radius,
                        impact.x + radius, impact.y + radius, impact.z + radius
                );
                AllianceManager alliances = AllianceManager.get(world.getServer());
                Set<LivingEntity> affected = new LinkedHashSet<>();

                // Always include the entity actually struck, even if its bounding-box center
                // lies just outside the area radius due to a large hitbox.
                if (hitResult.getType() == HitResult.Type.ENTITY) {
                    Entity hitEntity = ((net.minecraft.util.hit.EntityHitResult) hitResult).getEntity();
                    if (hitEntity instanceof LivingEntity living && living != player
                            && !alliances.isAlly(player, living)) {
                        affected.add(living);
                    }
                }
                for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                        target -> target != player && !alliances.isAlly(player, target))) {
                    if (target.getBoundingBox().getCenter().squaredDistanceTo(impact) <= radius * radius) {
                        affected.add(target);
                    }
                }

                for (LivingEntity target : affected) {
                    // Preserve the documented Ice Prison effect: 4 seconds of Slowness II
                    // and Blindness, applied to both the directly hit target and nearby enemies.
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
                }
                world.spawnParticles(ParticleTypes.SNOWFLAKE, impact.x, impact.y, impact.z, 40, 1.0, 0.8, 1.0, 0.06);
                world.spawnParticles(ParticleTypes.CLOUD, impact.x, impact.y, impact.z, 10, 0.45, 0.3, 0.45, 0.02);
                world.playSound(null, BlockPos.ofFloored(impact), SoundEvents.BLOCK_GLASS_BREAK,
                        SoundCategory.PLAYERS, 0.8f, 1.5f);
            }
            default -> { return false; }
        }
        archer.arrowTypes.remove(arrow.getUuid());
        archer.arrowOwners.remove(arrow.getUuid());
        archer.processedArrows.remove(arrow.getUuid());
        arrow.discard();
        return true;
    }
}
