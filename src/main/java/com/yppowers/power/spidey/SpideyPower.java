package com.yppowers.power.spidey;

import com.yppowers.YPPowersMod;
import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.item.YPItems;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.GraceSystem;
import com.yppowers.system.SoulboundItemSystem;
import com.yppowers.system.TempBlockSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Spidey: Web Sling grappling (G / Web Slingers right-click), Web Grenade cloud (H),
 * Rage Mode + Heart Palpitations (V), chained Spider Dive (B), and Auto-Cobweb MLG toggle (R).
 */
public final class SpideyPower extends Power {
    private static final String CD_GRENADE = "spidey_grenade";
    private static final String CD_RAGE = "spidey_rage";
    private static final String CD_DIVE = "spidey_dive";
    private static final String CD_MLG = "spidey_mlg";
    private static final String SLINGERS_TAG = "spidey_web_slingers";
    private static final String NBT_MLG = "AutoMlgEnabled";
    private static final Identifier MOD_RAGE_SPEED = Identifier.of(YPPowersMod.MOD_ID, "spidey_rage_speed");

    private static final class DiveState {
        int chainCount;
        int windowTicks;
        boolean diving;
        int diveTicks;
    }

    private static final class WebCloud {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d center;
        final double radius;
        final double damage;
        int ticksRemaining;

        WebCloud(UUID ownerId, ServerWorld world, Vec3d center, double radius, double damage, int ticksRemaining) {
            this.ownerId = ownerId;
            this.world = world;
            this.center = center;
            this.radius = radius;
            this.damage = damage;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private final Map<UUID, Integer> rageTicks = new HashMap<>();
    private final Map<UUID, DiveState> dives = new HashMap<>();
    private final List<WebCloud> clouds = new ArrayList<>();

    @Override
    public PowerId id() {
        return PowerId.SPIDEY;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.utility(AbilitySlot.PRIMARY, "Web Sling"),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Web Grenade", CD_GRENADE),
                AbilitySpec.active(AbilitySlot.TERTIARY, "Rage Mode", CD_RAGE),
                AbilitySpec.active(AbilitySlot.QUATERNARY, "Spider Dive", CD_DIVE),
                AbilitySpec.toggle(AbilitySlot.ALTERNATE, "Auto-MLG")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        int r = rageTicks.getOrDefault(player.getUuid(), 0);
        if (r > 0) return "RAGE " + CooldownSystem.formatSeconds(r);
        DiveState ds = dives.get(player.getUuid());
        if (ds != null && ds.chainCount > 0) return "Dive Chain x" + ds.chainCount;
        return isAutoMlgEnabled(player) ? "MLG ON" : "MLG OFF";
    }

    @Override
    public boolean isToggleActive(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.ALTERNATE && isAutoMlgEnabled(player);
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY
                || slot == AbilitySlot.TERTIARY || slot == AbilitySlot.QUATERNARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        PowerConfigs.Spidey cfg = YPConfig.get().powers.spidey;
        return switch (slot) {
            case PRIMARY, ALTERNATE -> 0.0;
            case SECONDARY -> cfg.webGrenadeEnergyCost;
            case TERTIARY -> cfg.rageEnergyCost;
            case QUATERNARY -> {
                DiveState ds = dives.get(player.getUuid());
                yield (ds != null && ds.chainCount > 0) ? 0.0 : cfg.diveEnergyCost;
            }
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        return switch (slot) {
            case PRIMARY, ALTERNATE -> true;
            case SECONDARY -> cds.checkReady(player, CD_GRENADE, "Web Grenade");
            case TERTIARY -> {
                if (rageTicks.getOrDefault(player.getUuid(), 0) > 0) {
                    player.sendMessage(Text.literal("Rage Mode is already active!").formatted(Formatting.YELLOW), true);
                    yield false;
                }
                yield cds.checkReady(player, CD_RAGE, "Rage Mode");
            }
            case QUATERNARY -> {
                DiveState ds = dives.get(player.getUuid());
                if (ds != null && ds.chainCount > 0 && ds.windowTicks > 0) yield true;
                yield cds.checkReady(player, CD_DIVE, "Spider Dive");
            }
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> useWebSling(player);
            case SECONDARY -> throwWebGrenade(player);
            case TERTIARY -> startRage(player);
            case QUATERNARY -> startSpiderDive(player);
            case ALTERNATE -> {
                boolean next = !isAutoMlgEnabled(player);
                setAutoMlgEnabled(player, next);
                player.sendMessage(Text.literal("Auto Cobweb MLG: " + (next ? "ON" : "OFF"))
                        .formatted(next ? Formatting.GREEN : Formatting.YELLOW), true);
            }
            default -> {}
        }
    }

    @Override
    public ActionResult onUseItem(ServerPlayerEntity player, Hand hand) {
        if (hand != Hand.MAIN_HAND || !player.getMainHandStack().isOf(YPItems.WEB_SLINGERS)) return ActionResult.PASS;
        if (GraceSystem.blockAbility(player)) return ActionResult.SUCCESS;
        useWebSling(player);
        return ActionResult.SUCCESS;
    }

    public void useWebSling(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        PowerConfigs.Spidey cfg = YPConfig.get().powers.spidey;
        HitResult hit = player.raycast(cfg.webSlingRange, 0.0f, false);
        if (hit.getType() == HitResult.Type.MISS) {
            player.sendMessage(Text.literal("No anchor in Web Sling range!").formatted(Formatting.GRAY), true);
            return;
        }
        Vec3d anchor = hit.getPos();
        Vec3d pull = anchor.subtract(player.getEyePos()).normalize();
        double mult = rageTicks.getOrDefault(player.getUuid(), 0) > 0 ? cfg.rageWebVelocityMultiplier : 1.0;
        double speed = cfg.webSlingVelocity * mult;
        player.setVelocity(pull.x * speed, Math.max(0.35, pull.y * speed + 0.25), pull.z * speed);
        ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        player.fallDistance = 0.0f;

        VfxSystem.beam(world, ParticleTypes.CLOUD, player.getEyePos(), anchor, 16);
        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 1.0f, 1.4f);
    }

    private void throwWebGrenade(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.Spidey cfg = YPConfig.get().powers.spidey;
        HitResult hit = player.raycast(24.0, 0.0f, false);
        Vec3d target = hit.getPos();

        clouds.add(new WebCloud(player.getUuid(), world, target, cfg.webGrenadeRadius, cfg.webGrenadeDamage, cfg.webGrenadeDurationTicks));
        CooldownSystem.get(world.getServer()).set(player.getUuid(), CD_GRENADE, cfg.webGrenadeCooldownTicks);
        VfxSystem.shockwave(world, ParticleTypes.CLOUD, ParticleTypes.ITEM_SLIME, target.add(0, 0.3, 0), cfg.webGrenadeRadius, 28);
        VfxSystem.playSound(world, target, SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.PLAYERS, 1.1f, 0.8f);
    }

    private void startRage(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PowerConfigs.Spidey cfg = YPConfig.get().powers.spidey;
        rageTicks.put(player.getUuid(), cfg.rageDurationTicks);
        CooldownSystem.get(server).set(player.getUuid(), CD_RAGE, cfg.rageCooldownTicks);
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (speed.getModifier(MOD_RAGE_SPEED) != null) speed.removeModifier(MOD_RAGE_SPEED);
            speed.addTemporaryModifier(new EntityAttributeModifier(
                    MOD_RAGE_SPEED, Math.max(0.0, cfg.rageSpeedMultiplier - 1.0), EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        if (player.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.shockwave(world, ParticleTypes.CRIMSON_SPORE, ParticleTypes.FLAME, player.getEntityPos().add(0, 0.5, 0), 3.0, 24);
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 0.8f, 1.3f);
        }
        player.sendMessage(Text.literal("SPIDEY RAGE MODE!").formatted(Formatting.RED, Formatting.BOLD), true);
    }

    private void endRage(ServerPlayerEntity player, boolean applyDebuff) {
        rageTicks.remove(player.getUuid());
        EntityAttributeInstance speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(MOD_RAGE_SPEED) != null) speed.removeModifier(MOD_RAGE_SPEED);
        if (applyDebuff) {
            int debuff = YPConfig.get().powers.spidey.rageDebuffTicks;
            if (debuff > 0) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, debuff, 1));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, debuff, 0));
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, Math.min(debuff, 100), 0));
                player.sendMessage(Text.literal("Heart Palpitations! Rage Mode ended.").formatted(Formatting.DARK_RED), true);
            }
        }
    }

    private void startSpiderDive(ServerPlayerEntity player) {
        PowerConfigs.Spidey cfg = YPConfig.get().powers.spidey;
        DiveState ds = dives.computeIfAbsent(player.getUuid(), ignored -> new DiveState());
        ds.diving = true;
        ds.diveTicks = 0;
        Vec3d look = CombatUtil.safeLook(player);
        player.setVelocity(look.x * 1.9, Math.min(-0.4, look.y * 1.8 - 0.5), look.z * 1.9);
        ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
        if (player.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0f, 1.4f);
        }
    }

    @Override
    public float modifyOutgoingDamage(ServerPlayerEntity attacker, Entity target, DamageSource source, float amount) {
        return rageTicks.getOrDefault(attacker.getUuid(), 0) > 0 ? amount * 1.30f : amount;
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        if (clouds.isEmpty()) return;
        Iterator<WebCloud> it = clouds.iterator();
        while (it.hasNext()) {
            WebCloud cloud = it.next();
            cloud.ticksRemaining--;
            if (cloud.ticksRemaining <= 0) {
                it.remove();
                continue;
            }
            if (cloud.ticksRemaining % 6 == 0) {
                VfxSystem.ring(cloud.world, ParticleTypes.CLOUD, cloud.center.add(0, 0.3, 0), cloud.radius, 22, 0.01);
            }
            if (cloud.ticksRemaining % 20 == 0) {
                ServerPlayerEntity owner = server.getPlayerManager().getPlayer(cloud.ownerId);
                if (owner == null) continue;
                Box box = Box.of(cloud.center, cloud.radius * 2.0, 4.0, cloud.radius * 2.0);
                for (LivingEntity target : cloud.world.getEntitiesByClass(LivingEntity.class, box,
                        e -> CombatUtil.canHarm(owner, e))) {
                    if (target.getEntityPos().distanceTo(cloud.center) > cloud.radius) continue;
                    target.damage(cloud.world, cloud.world.getDamageSources().magic(), (float) cloud.damage);
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 3));
                    target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 50, 0));
                }
            }
        }
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || !(player.getEntityWorld() instanceof ServerWorld world)) return;
        PowerConfigs.Spidey cfg = YPConfig.get().powers.spidey;

        // Rage timer
        Integer r = rageTicks.get(uuid);
        if (r != null) {
            if (r <= 1) endRage(player, true);
            else rageTicks.put(uuid, r - 1);
        }

        // Spider Dive chain logic
        DiveState ds = dives.get(uuid);
        if (ds != null) {
            if (ds.diving) {
                ds.diveTicks++;
                LivingEntity hit = CombatUtil.nearestTarget(player, 2.8);
                if (hit != null) {
                    ds.diving = false;
                    ds.chainCount++;
                    boolean finisher = ds.chainCount >= cfg.diveMaxChain;
                    float dmg = (float) (finisher ? cfg.diveFinisherDamage : cfg.diveHitDamage);
                    hit.damage(world, world.getDamageSources().playerAttack(player), dmg);
                    VfxSystem.burst(world, finisher ? ParticleTypes.EXPLOSION : ParticleTypes.CRIT, hit.getEntityPos().add(0, 1.0, 0), 14, 0.4, 0.1);
                    VfxSystem.playSound(world, hit.getEntityPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.1f, finisher ? 0.7f : 1.2f);
                    if (finisher) {
                        CooldownSystem.get(server).set(uuid, CD_DIVE, cfg.diveCooldownTicks);
                        dives.remove(uuid);
                    } else {
                        ds.windowTicks = cfg.diveChainWindowTicks;
                        player.setVelocity(0.0, 0.85, 0.0);
                        ((EntityVelocityAccessor) player).invokeYppowersScheduleVelocityUpdate();
                        player.sendMessage(Text.literal("Dive Chain x" + ds.chainCount + "! Press B to continue!").formatted(Formatting.AQUA), true);
                    }
                } else if (player.isOnGround() || ds.diveTicks > 30) {
                    CooldownSystem.get(server).set(uuid, CD_DIVE, cfg.diveCooldownTicks);
                    dives.remove(uuid);
                }
            } else if (ds.windowTicks > 0) {
                ds.windowTicks--;
                if (ds.windowTicks <= 0) {
                    CooldownSystem.get(server).set(uuid, CD_DIVE, cfg.diveCooldownTicks);
                    dives.remove(uuid);
                }
            }
        }

        // Auto Cobweb MLG
        if (isAutoMlgEnabled(player) && !player.isOnGround() && player.fallDistance >= cfg.mlgFallThreshold
                && player.getVelocity().y < -0.4 && !CooldownSystem.get(server).isOnCooldown(uuid, CD_MLG)) {
            BlockPos feet = player.getBlockPos();
            for (int dy = 1; dy <= cfg.mlgSearchRadius; dy++) {
                BlockPos below = feet.down(dy);
                if (!world.getBlockState(below).isAir() && world.getBlockState(below.up()).isAir()) {
                    if (TempBlockSystem.setTemporary(world, below.up(), Blocks.COBWEB.getDefaultState(), 60, "spidey_mlg", false, null)) {
                        CooldownSystem.get(server).set(uuid, CD_MLG, cfg.mlgCooldownTicks);
                        VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.BLOCK_COBWEB_PLACE, SoundCategory.PLAYERS, 1.0f, 1.2f);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public void onAssign(ServerPlayerEntity player) {
        ensureWebSlingers(player);
    }

    @Override
    public void onJoin(ServerPlayerEntity player) {
        ensureWebSlingers(player);
    }

    @Override
    public void onRespawn(ServerPlayerEntity newPlayer, ServerPlayerEntity oldPlayer) {
        ensureWebSlingers(newPlayer);
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        endRage(player, false);
        dives.remove(player.getUuid());
    }

    @Override
    public void onDeath(ServerPlayerEntity player) {
        endRage(player, false);
        dives.remove(player.getUuid());
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        endRage(player, false);
        dives.remove(player.getUuid());
    }

    @Override
    public boolean shouldPreventDrop(ServerPlayerEntity player, ItemStack stack) {
        return SoulboundItemSystem.isSoulbound(stack, PowerId.SPIDEY, SLINGERS_TAG);
    }

    private static void ensureWebSlingers(ServerPlayerEntity player) {
        SoulboundItemSystem.ensureEquippedOrPresent(player, PowerId.SPIDEY, SLINGERS_TAG, () -> {
            ItemStack stack = new ItemStack(YPItems.WEB_SLINGERS);
            stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Web Slingers").formatted(Formatting.AQUA, Formatting.BOLD));
            return stack;
        });
    }

    private boolean isAutoMlgEnabled(ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return true;
        return PlayerDataStore.get(server).getPowerNbt(player.getUuid(), PowerId.SPIDEY).getBoolean(NBT_MLG, true);
    }

    private void setAutoMlgEnabled(ServerPlayerEntity player, boolean enabled) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;
        PlayerDataStore store = PlayerDataStore.get(server);
        store.getPowerNbt(player.getUuid(), PowerId.SPIDEY).putBoolean(NBT_MLG, enabled);
        store.markDirty();
    }
}
