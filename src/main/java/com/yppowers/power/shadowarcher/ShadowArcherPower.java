package com.yppowers.power.shadowarcher;

import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.network.YPNetworking;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
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
 * Shadow Archer: unlockable special arrows (B), searchable mob morph menu (V),
 * mob disguise with flight/aquatic/climbing/strider traits and G/H/R morph abilities.
 */
public final class ShadowArcherPower extends Power {
    private static final String CD_MORPH = "shadow_archer_morph";
    private static final String CD_MORPH_PRIMARY = "shadow_archer_morph_g";
    private static final String CD_MORPH_SECONDARY = "shadow_archer_morph_h";
    private static final String NBT_KILLS = "MobKills";

    private static final class ActiveMorph {
        final MorphRegistry.MorphProfile profile;
        final Entity disguise;
        ActiveMorph(MorphRegistry.MorphProfile profile, Entity disguise) {
            this.profile = profile;
            this.disguise = disguise;
        }
    }

    private final SpecialArrowHandler arrowHandler = new SpecialArrowHandler();
    private final Map<UUID, ActiveMorph> activeMorphs = new HashMap<>();

    public ShadowArcherPower() {
        YPNetworking.setMorphRequestHandler(this::handleMorphRequest);
    }

    @Override
    public PowerId id() {
        return PowerId.SHADOW_ARCHER;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        ActiveMorph morph = activeMorphs.get(player.getUuid());
        if (morph != null) {
            return List.of(
                    AbilitySpec.active(AbilitySlot.PRIMARY, morph.profile.primaryDesc(), CD_MORPH_PRIMARY),
                    AbilitySpec.active(AbilitySlot.SECONDARY, morph.profile.secondaryDesc(), CD_MORPH_SECONDARY),
                    AbilitySpec.active(AbilitySlot.TERTIARY, "Morph Menu", CD_MORPH),
                    AbilitySpec.utility(AbilitySlot.QUATERNARY, "Arrows"),
                    AbilitySpec.utility(AbilitySlot.ALTERNATE, "Unmorph")
            );
        }
        return List.of(
                AbilitySpec.active(AbilitySlot.TERTIARY, "Morph Menu", CD_MORPH),
                AbilitySpec.utility(AbilitySlot.QUATERNARY, "Arrows")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        String arrow = arrowHandler.getSelected(player).displayName();
        ActiveMorph morph = activeMorphs.get(player.getUuid());
        return morph != null ? morph.profile.displayName() + " • " + arrow : arrow;
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY
                || slot == AbilitySlot.TERTIARY || slot == AbilitySlot.QUATERNARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        if (slot == AbilitySlot.QUATERNARY || slot == AbilitySlot.TERTIARY) return 0.0;
        if (slot == AbilitySlot.ALTERNATE) return 0.0;
        return super.energyCost(player, slot);
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        return switch (slot) {
            case PRIMARY -> activeMorphs.containsKey(player.getUuid()) && cds.checkReady(player, CD_MORPH_PRIMARY, "Morph Ability");
            case SECONDARY -> activeMorphs.containsKey(player.getUuid()) && cds.checkReady(player, CD_MORPH_SECONDARY, "Morph Secondary");
            case TERTIARY, QUATERNARY, ALTERNATE -> true;
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> useMorphAbility(player, true);
            case SECONDARY -> useMorphAbility(player, false);
            case QUATERNARY -> arrowHandler.cycleArrow(player);
            case ALTERNATE -> {
                if (activeMorphs.containsKey(player.getUuid())) clearMorph(player, true);
            }
            default -> {}
        }
    }

    private void handleMorphRequest(ServerPlayerEntity player, String requestedMobId) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || !PlayerDataStore.get(server).hasPower(player, PowerId.SHADOW_ARCHER)) return;
        if (requestedMobId == null || requestedMobId.isBlank() || "human".equalsIgnoreCase(requestedMobId)) {
            clearMorph(player, true);
            return;
        }
        Set<String> kills = getKilledMobs(player);
        if (!kills.contains(requestedMobId)) {
            player.sendMessage(Text.literal("Kill a " + requestedMobId + " first to unlock this morph!").formatted(Formatting.RED), true);
            return;
        }
        CooldownSystem cds = CooldownSystem.get(server);
        if (!cds.checkReady(player, CD_MORPH, "Morph")) return;

        MorphRegistry.MorphProfile profile = MorphRegistry.get(requestedMobId);
        if (profile == null) return;
        applyMorph(player, profile);
        cds.set(player.getUuid(), CD_MORPH, YPConfig.get().powers.shadowArcher.morphCooldownTicks);
    }

    private void applyMorph(ServerPlayerEntity player, MorphRegistry.MorphProfile profile) {
        clearMorph(player, false);
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        Identifier id = Identifier.tryParse(profile.entityId());
        if (id == null || !Registries.ENTITY_TYPE.containsId(id)) return;
        EntityType<?> type = Registries.ENTITY_TYPE.get(id);
        Entity disguise = type.create(world, SpawnReason.TRIGGERED);
        if (disguise == null) return;

        disguise.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        disguise.setInvulnerable(true);
        disguise.setSilent(true);
        disguise.setNoGravity(true);
        if (disguise instanceof MobEntity mob) mob.setAiDisabled(true);
        world.spawnEntity(disguise);

        activeMorphs.put(player.getUuid(), new ActiveMorph(profile, disguise));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, StatusEffectInstance.INFINITE, 0, false, false));

        if (profile.canFly() && !player.isCreative() && !player.isSpectator()) {
            player.getAbilities().allowFlying = true;
            player.getAbilities().setFlySpeed(profile.flightSpeed());
            player.sendAbilitiesUpdate();
        }

        YPNetworking.sendMorphView(player, true, disguise.getId(), profile.eyeHeight(), profile.entityHeight(), profile.entityId());
        VfxSystem.burst(world, ParticleTypes.PORTAL, player.getEntityPos().add(0, 1.0, 0), 26, 0.6, 0.12);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0f, 1.2f);
        player.sendMessage(Text.literal("Morphed into " + profile.displayName() + "!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
    }

    private void clearMorph(ServerPlayerEntity player, boolean notify) {
        ActiveMorph old = activeMorphs.remove(player.getUuid());
        if (old != null) {
            if (old.disguise != null && !old.disguise.isRemoved()) old.disguise.discard();
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
            if (old.profile.canFly() && !player.isCreative() && !player.isSpectator()) {
                player.getAbilities().flying = false;
                player.getAbilities().allowFlying = false;
                player.getAbilities().setFlySpeed(0.05f);
                player.sendAbilitiesUpdate();
            }
            YPNetworking.sendMorphView(player, false, -1, 1.62f, 1.8f, "");
            if (notify) player.sendMessage(Text.literal("Returned to human form.").formatted(Formatting.GRAY), true);
        }
    }

    private void useMorphAbility(ServerPlayerEntity player, boolean primary) {
        ActiveMorph morph = activeMorphs.get(player.getUuid());
        if (morph == null || !(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        String id = morph.profile.entityId();
        Vec3d look = CombatUtil.safeLook(player);
        CooldownSystem cds = CooldownSystem.get(world.getServer());

        if (primary) {
            switch (id) {
                case "minecraft:blaze", "minecraft:ghast" -> {
                    FireballEntity fb = new FireballEntity(world, player, look.multiply(0.1), id.equals("minecraft:ghast") ? 2 : 1);
                    fb.setPosition(player.getEyePos().add(look.multiply(1.4)));
                    fb.setVelocity(look.multiply(1.7));
                    world.spawnEntity(fb);
                    VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.1f);
                }
                case "minecraft:enderman" -> {
                    HitResult hit = player.raycast(24.0, 0.0f, false);
                    Vec3d dest = hit.getPos().subtract(look.multiply(0.7));
                    player.requestTeleport(dest.x, dest.y, dest.z);
                    VfxSystem.burst(world, ParticleTypes.PORTAL, dest, 20, 0.5, 0.1);
                    VfxSystem.playSound(world, dest, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                case "minecraft:creeper" -> {
                    world.createExplosion(player, player.getX(), player.getY(), player.getZ(), 3.0f, World.ExplosionSourceType.NONE);
                }
                case "minecraft:warden" -> {
                    VfxSystem.beam(world, ParticleTypes.SONIC_BOOM, player.getEyePos(), player.getEyePos().add(look.multiply(18.0)), 14);
                    Box box = player.getBoundingBox().stretch(look.multiply(18.0)).expand(1.5);
                    for (LivingEntity t : world.getEntitiesByClass(LivingEntity.class, box, e -> CombatUtil.canHarm(player, e))) {
                        t.damage(world, world.getDamageSources().sonicBoom(player), 12.0f);
                    }
                    VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.4f, 1.0f);
                }
                default -> {
                    player.setVelocity(look.x * 1.5, Math.max(0.4, look.y * 1.3), look.z * 1.5);
                    ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
                    VfxSystem.burst(world, ParticleTypes.CLOUD, player.getEntityPos(), 10, 0.3, 0.08);
                }
            }
            cds.set(player.getUuid(), CD_MORPH_PRIMARY, 100);
        } else {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 0));
            VfxSystem.ring(world, ParticleTypes.PORTAL, player.getEntityPos().add(0, 0.4, 0), 2.5, 20, 0.04);
            cds.set(player.getUuid(), CD_MORPH_SECONDARY, 160);
        }
    }

    @Override
    public ActionResult onAttackEntity(ServerPlayerEntity player, Hand hand, Entity target) {
        ActiveMorph morph = activeMorphs.get(player.getUuid());
        if (morph != null && morph.disguise == target) {
            return ActionResult.FAIL; // never punch own disguise
        }
        return ActionResult.PASS;
    }

    @Override
    public ActionResult onUseEntity(ServerPlayerEntity player, Hand hand, Entity target) {
        ActiveMorph morph = activeMorphs.get(player.getUuid());
        if (morph != null && morph.disguise == target) {
            return ActionResult.SUCCESS; // swallow accidental interaction with own disguise
        }
        return ActionResult.PASS;
    }

    @Override
    public boolean onArrowSpawned(ServerWorld world, PersistentProjectileEntity arrow) {
        return arrowHandler.onArrowSpawned(world, arrow);
    }

    @Override
    public boolean onProjectileCollision(ProjectileEntity projectile, HitResult hitResult) {
        return arrowHandler.onProjectileCollision(projectile, hitResult);
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        arrowHandler.serverTick(server);
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() != null && player.getEntityWorld().getServer().getTicks() % 20 == 0) {
            arrowHandler.checkInventoryUnlocks(player);
        }

        ActiveMorph morph = activeMorphs.get(player.getUuid());
        if (morph == null) return;
        if (morph.disguise != null && !morph.disguise.isRemoved()) {
            morph.disguise.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
            morph.disguise.setHeadYaw(player.getHeadYaw());
        }
        if (morph.profile.aquatic()) {
            StatusEffectInstance wb = player.getStatusEffect(StatusEffects.WATER_BREATHING);
            if (wb == null || wb.getDuration() <= 10) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 40, 0, false, false));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 40, 0, false, false));
            }
        }
        if (morph.profile.lavaStrider() && player.isInLava()) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, false, false));
            player.setVelocity(player.getVelocity().x, 0.15, player.getVelocity().z);
            ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        }
    }

    @Override
    public void onMobKilled(ServerPlayerEntity killer, MobEntity victim) {
        Identifier id = Registries.ENTITY_TYPE.getId(victim.getType());
        if (id == null) return;
        if (recordMobKill(killer, id.toString())) {
            YPNetworking.sendMorphCatalog(killer, getKilledMobs(killer));
            MorphRegistry.MorphProfile p = MorphRegistry.get(id.toString());
            String label = p != null ? p.displayName() : id.toString();
            killer.sendMessage(Text.literal("Morph Unlocked: " + label + " (Press V)!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
        }
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        YPNetworking.sendMorphCatalog(player, getKilledMobs(player));
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        YPNetworking.sendMorphCatalog(player, getKilledMobs(player));
        YPNetworking.sendMorphView(player, false, -1, 1.62f, 1.8f, "");
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        clearMorph(player, false);
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        clearMorph(player, false);
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        clearMorph(player, false);
        arrowHandler.clearPlayer(player.getUuid());
    }

    private Set<String> getKilledMobs(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return Set.of();
        NbtList list = PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.SHADOW_ARCHER).getListOrEmpty(NBT_KILLS);
        Set<String> set = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            String s = list.getString(i, "");
            if (!s.isBlank()) set.add(s);
        }
        return set;
    }

    private boolean recordMobKill(ServerPlayerEntity player, String entityId) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        PlayerDataStore store = PlayerDataStore.get(server);
        NbtCompound nbt = store.getPowerNbt(player.getUuid(), PowerId.SHADOW_ARCHER);
        NbtList list = nbt.getListOrEmpty(NBT_KILLS);
        for (int i = 0; i < list.size(); i++) {
            if (entityId.equals(list.getString(i, ""))) return false;
        }
        list.add(NbtString.of(entityId));
        nbt.put(NBT_KILLS, list);
        store.markDirty();
        return true;
    }
}
