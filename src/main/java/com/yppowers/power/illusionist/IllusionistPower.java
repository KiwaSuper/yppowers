package com.yppowers.power.illusionist;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.core.AbilitySlot;
import com.yppowers.core.AbilitySpec;
import com.yppowers.core.Power;
import com.yppowers.core.PowerId;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.GraceSystem;
import com.yppowers.system.TempBlockSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Illusionist: 3x3x3 Spatial Erasure (G + right-click block), Psychokinetic Grip (H grab, B/attack throw, R distance),
 * Celestial Pull hollow-moon gravity well + crater blast (V), and passive item/XP pull.
 */
public final class IllusionistPower extends Power {
    private static final String CD_ERASURE = "illusionist_erasure";
    private static final String CD_GRIP = "illusionist_grip";
    private static final String CD_PULL = "illusionist_celestial_pull";

    private static final class CelestialPull {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d center;
        final Vec3d moonCenter;
        final double moonRadius;
        final double weightlessRadius;
        final double craterRadius;
        final double damage;
        final List<FallingBlockEntity> moonBlocks = new ArrayList<>();
        final Set<UUID> weightlessEntities = new HashSet<>();
        int ticksRemaining;
        int age;

        CelestialPull(UUID ownerId, ServerWorld world, Vec3d center, Vec3d moonCenter, double moonRadius,
                      double weightlessRadius, double craterRadius, double damage, int ticksRemaining) {
            this.ownerId = ownerId;
            this.world = world;
            this.center = center;
            this.moonCenter = moonCenter;
            this.moonRadius = moonRadius;
            this.weightlessRadius = weightlessRadius;
            this.craterRadius = craterRadius;
            this.damage = damage;
            this.ticksRemaining = ticksRemaining;
        }
    }

    private final Set<UUID> erasureArmed = new HashSet<>();
    private final Map<UUID, Entity> heldEntities = new HashMap<>();
    private final Map<UUID, Double> gripDistances = new HashMap<>();
    private final List<CelestialPull> activePulls = new ArrayList<>();

    @Override
    public PowerId id() {
        return PowerId.ILLUSIONIST;
    }

    @Override
    public List<AbilitySpec> hudAbilities(ServerPlayerEntity player) {
        return List.of(
                AbilitySpec.active(AbilitySlot.PRIMARY, "Spatial Erase", CD_ERASURE),
                AbilitySpec.active(AbilitySlot.SECONDARY, "Psychic Grip", CD_GRIP),
                AbilitySpec.active(AbilitySlot.TERTIARY, "Celestial Pull", CD_PULL),
                AbilitySpec.utility(AbilitySlot.QUATERNARY, "Throw Grip"),
                AbilitySpec.utility(AbilitySlot.ALTERNATE, "Grip Dist+")
        );
    }

    @Override
    public String hudStatusChip(ServerPlayerEntity player) {
        if (heldEntities.containsKey(player.getUuid())) {
            double dist = gripDistances.getOrDefault(player.getUuid(), YPConfig.get().powers.illusionist.gripDefaultDistance);
            return String.format("Holding (%.0fm)", dist);
        }
        return erasureArmed.contains(player.getUuid()) ? "Erase Armed" : "";
    }

    @Override
    public boolean supportsSlot(ServerPlayerEntity player, AbilitySlot slot) {
        return slot == AbilitySlot.PRIMARY || slot == AbilitySlot.SECONDARY
                || slot == AbilitySlot.TERTIARY || slot == AbilitySlot.QUATERNARY || slot == AbilitySlot.ALTERNATE;
    }

    @Override
    public double energyCost(ServerPlayerEntity player, AbilitySlot slot) {
        return switch (slot) {
            case PRIMARY, QUATERNARY, ALTERNATE -> 0.0;
            case TERTIARY -> YPConfig.get().powers.illusionist.celestialPullEnergyCost;
            default -> super.energyCost(player, slot);
        };
    }

    @Override
    public boolean canActivate(ServerPlayerEntity player, AbilitySlot slot) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;
        CooldownSystem cds = CooldownSystem.get(server);
        return switch (slot) {
            case PRIMARY, QUATERNARY, ALTERNATE -> true;
            case SECONDARY -> heldEntities.containsKey(player.getUuid()) || cds.checkReady(player, CD_GRIP, "Psychokinetic Grip");
            case TERTIARY -> cds.checkReady(player, CD_PULL, "Celestial Pull");
            default -> false;
        };
    }

    @Override
    public void activate(ServerPlayerEntity player, AbilitySlot slot) {
        switch (slot) {
            case PRIMARY -> {
                if (erasureArmed.remove(player.getUuid())) {
                    player.sendMessage(Text.literal("Spatial Erasure cancelled.").formatted(Formatting.GRAY), true);
                } else {
                    erasureArmed.add(player.getUuid());
                    player.sendMessage(Text.literal("Spatial Erasure armed — right-click a block to erase 3x3x3.").formatted(Formatting.LIGHT_PURPLE), true);
                }
            }
            case SECONDARY -> toggleGrip(player);
            case TERTIARY -> startCelestialPull(player);
            case QUATERNARY -> throwGrip(player);
            case ALTERNATE -> adjustGripDistance(player);
            default -> {}
        }
    }

    @Override
    public ActionResult onUseBlock(ServerPlayerEntity player, Hand hand, BlockPos center) {
        if (hand != Hand.MAIN_HAND || !erasureArmed.remove(player.getUuid())) return ActionResult.PASS;
        if (GraceSystem.blockAbility(player)) return ActionResult.SUCCESS;
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return ActionResult.PASS;

        CooldownSystem cds = CooldownSystem.get(world.getServer());
        if (!cds.checkReady(player, CD_ERASURE, "Spatial Erasure")) return ActionResult.SUCCESS;

        // Validate entire 3x3x3 before erasing
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = center.add(dx, dy, dz);
                    BlockState state = world.getBlockState(p);
                    if (state.isAir()) continue;
                    if (TempBlockSystem.isProtectedBlock(world, p, state)) {
                        player.sendMessage(Text.literal("Spatial Erasure cancelled: protected/container block in 3x3x3.").formatted(Formatting.RED), true);
                        return ActionResult.SUCCESS;
                    }
                    targets.add(p.toImmutable());
                }
            }
        }

        PowerConfigs.Illusionist cfg = YPConfig.get().powers.illusionist;
        BlockPos c = center.toImmutable();
        for (int i = 0; i < targets.size(); i++) {
            boolean isCenter = i == 0;
            Runnable onRestore = isCenter ? () -> applyErasureRestoreDamage(world, player.getUuid(), c, cfg) : null;
            TempBlockSystem.setTemporary(world, targets.get(i), Blocks.AIR.getDefaultState(),
                    cfg.spatialErasureDurationTicks, "illusionist_erasure", false, onRestore);
        }

        cds.set(player.getUuid(), CD_ERASURE, cfg.spatialErasureCooldownTicks);
        VfxSystem.burst(world, ParticleTypes.REVERSE_PORTAL, Vec3d.ofCenter(center), 50, 1.5, 0.2);
        VfxSystem.playSound(world, Vec3d.ofCenter(center), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.9f, 0.8f);
        return ActionResult.SUCCESS;
    }

    private static void applyErasureRestoreDamage(ServerWorld world, UUID ownerId, BlockPos center, PowerConfigs.Illusionist cfg) {
        if (world.getServer() == null) return;
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(ownerId);
        Box box = new Box(center).expand(2.0);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, LivingEntity::isAlive)) {
            if (owner != null && !CombatUtil.canHarm(owner, e)) continue;
            e.damage(world, world.getDamageSources().inWall(), (float) cfg.spatialErasureDamage);
            if (cfg.spatialErasureSlownessTicks > 0) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, cfg.spatialErasureSlownessTicks, 2));
            }
        }
    }

    private void toggleGrip(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        Entity current = heldEntities.remove(id);
        if (current != null) {
            releaseEntity(current);
            player.sendMessage(Text.literal("Released Psychokinetic Grip.").formatted(Formatting.GRAY), true);
            return;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world)) return;
        PowerConfigs.Illusionist cfg = YPConfig.get().powers.illusionist;
        Vec3d eye = player.getEyePos();
        Vec3d look = CombatUtil.safeLook(player);
        Box search = player.getBoundingBox().stretch(look.multiply(cfg.gripRange)).expand(1.8);

        Entity best = null;
        double bestDistSq = cfg.gripRange * cfg.gripRange;
        for (Entity candidate : world.getOtherEntities(player, search,
                e -> e.isAlive() && (e instanceof ItemEntity || CombatUtil.canHarm(player, e)))) {
            Vec3d delta = candidate.getBoundingBox().getCenter().subtract(eye);
            double d = delta.length();
            if (d < 0.2 || d > cfg.gripRange || look.dotProduct(delta.normalize()) < 0.90) continue;
            if (d * d < bestDistSq) {
                bestDistSq = d * d;
                best = candidate;
            }
        }
        if (best == null) {
            player.sendMessage(Text.literal("No target in Psychokinetic Grip range.").formatted(Formatting.YELLOW), true);
            return;
        }
        heldEntities.put(id, best);
        gripDistances.put(id, Math.max(2.0, Math.min(10.0, best.getEntityPos().distanceTo(eye))));
        best.setNoGravity(true);
        player.sendMessage(Text.literal("Gripped " + best.getName().getString() + "! (B or Attack to throw)").formatted(Formatting.LIGHT_PURPLE), true);
    }

    private boolean throwGrip(ServerPlayerEntity player) {
        Entity held = heldEntities.remove(player.getUuid());
        if (held == null || held.isRemoved()) return false;
        releaseEntity(held);
        PowerConfigs.Illusionist cfg = YPConfig.get().powers.illusionist;
        Vec3d vel = CombatUtil.safeLook(player).multiply(cfg.gripThrowMultiplier);
        held.setVelocity(vel);
        ((EntityVelocityAccessor) held).invokeYppowersScheduleVelocityUpdate();
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server != null) CooldownSystem.get(server).set(player.getUuid(), CD_GRIP, cfg.gripReleaseCooldownTicks);
        if (player.getEntityWorld() instanceof ServerWorld world) {
            VfxSystem.playSound(world, player.getEntityPos(), SoundEvents.ENTITY_SHULKER_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.2f);
        }
        return true;
    }

    private void adjustGripDistance(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (!heldEntities.containsKey(id)) {
            player.sendMessage(Text.literal("Grab an entity first with H.").formatted(Formatting.GRAY), true);
            return;
        }
        double cur = gripDistances.getOrDefault(id, YPConfig.get().powers.illusionist.gripDefaultDistance);
        double next = cur >= 10.0 ? 2.0 : cur + 1.0;
        gripDistances.put(id, next);
        player.sendMessage(Text.literal("Grip distance: " + (int) next + " blocks").formatted(Formatting.LIGHT_PURPLE), true);
    }

    private void startCelestialPull(ServerPlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.Illusionist cfg = YPConfig.get().powers.illusionist;
        HitResult hit = player.raycast(40.0, 0.0f, false);
        Vec3d center = hit.getPos();
        double radius = Math.min(14.0, cfg.celestialMoonRadius * cfg.celestialMoonScale);
        Vec3d moonPos = center.add(0, cfg.celestialMoonHeight, 0);
        double dmg = Math.max(cfg.celestialPullDamage, cfg.celestialCraterDamage);

        CelestialPull pull = new CelestialPull(
                player.getUuid(), world, center, moonPos, radius,
                cfg.celestialWeightlessRadius, cfg.celestialCraterRadius, dmg, cfg.celestialPullDurationTicks
        );
        spawnHollowMoon(pull, cfg.celestialMoonMaxBlocks);
        activePulls.add(pull);

        CooldownSystem.get(world.getServer()).setPersistent(player.getUuid(), CD_PULL, cfg.celestialPullCooldownTicks);
        VfxSystem.lightPillar(world, ParticleTypes.PORTAL, center, cfg.celestialMoonHeight, 32);
        VfxSystem.playSound(world, center, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.4f, 0.6f);
        player.sendMessage(Text.literal("CELESTIAL PULL!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
    }

    private static void spawnHollowMoon(CelestialPull pull, int maxBlocks) {
        int count = Math.min(maxBlocks, 160);
        double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0));
        BlockState[] palette = {Blocks.END_STONE.getDefaultState(), Blocks.DEEPSLATE.getDefaultState(), Blocks.OBSIDIAN.getDefaultState()};
        for (int i = 0; i < count; i++) {
            double y = 1.0 - (2.0 * i) / Math.max(1, count - 1);
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = goldenAngle * i;
            Vec3d offset = new Vec3d(Math.cos(theta) * r, y, Math.sin(theta) * r).multiply(pull.moonRadius);
            Vec3d pos = pull.moonCenter.add(offset);
            FallingBlockEntity fb = FallingBlockEntity.spawnFromBlock(pull.world, BlockPos.ofFloored(pos), palette[i % palette.length]);
            if (fb != null) {
                fb.setNoGravity(true);
                fb.dropItem = false;
                fb.timeFalling = 1;
                fb.setPosition(pos.x, pos.y, pos.z);
                pull.moonBlocks.add(fb);
            }
        }
    }

    @Override
    public boolean onPreAttack(ServerPlayerEntity attacker, Entity target) {
        return throwGrip(attacker);
    }

    @Override
    public void onServerTick(MinecraftServer server) {
        if (activePulls.isEmpty()) return;
        Iterator<CelestialPull> it = activePulls.iterator();
        while (it.hasNext()) {
            CelestialPull pull = it.next();
            pull.age++;
            pull.ticksRemaining--;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pull.ownerId);

            // Keep hollow moon hovering and slowly rotating
            for (FallingBlockEntity fb : pull.moonBlocks) {
                if (fb.isRemoved()) continue;
                fb.timeFalling = 1;
                fb.setVelocity(0, 0, 0);
            }

            // Weightless field + upward gravity well
            Box wBox = Box.of(pull.center, pull.weightlessRadius * 2.0, pull.weightlessRadius * 2.0, pull.weightlessRadius * 2.0);
            for (Entity e : pull.world.getEntitiesByClass(Entity.class, wBox, Entity::isAlive)) {
                if (owner != null && (e == owner || !CombatUtil.canHarm(owner, e))) continue;
                if (e instanceof FallingBlockEntity) continue;
                double dist = e.getEntityPos().distanceTo(pull.center);
                if (dist <= pull.weightlessRadius) {
                    if (pull.weightlessEntities.add(e.getUuid())) e.setNoGravity(true);
                    if (dist <= pull.craterRadius) {
                        Vec3d toMoon = pull.moonCenter.subtract(0, pull.moonRadius + 2.0, 0).subtract(e.getEntityPos());
                        e.setVelocity(toMoon.normalize().multiply(0.35));
                        ((EntityVelocityAccessor) e).invokeYppowersScheduleVelocityUpdate();
                    }
                }
            }

            if (pull.age % 5 == 0) {
                VfxSystem.ring(pull.world, ParticleTypes.PORTAL, pull.center.add(0, 0.3, 0), pull.craterRadius, 28, 0.05);
            }

            if (pull.ticksRemaining <= 0) {
                detonateCelestialPull(server, pull);
                it.remove();
            }
        }
    }

    private void detonateCelestialPull(MinecraftServer server, CelestialPull pull) {
        restorePullEntities(server, pull);
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(pull.ownerId);

        VfxSystem.flash(pull.world, pull.center.add(0, 1.0, 0));
        VfxSystem.shockwave(pull.world, ParticleTypes.EXPLOSION, ParticleTypes.FLAME, pull.center.add(0, 0.4, 0), pull.craterRadius, 40);
        VfxSystem.playSound(pull.world, pull.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.2f, 0.5f);

        Box cBox = Box.of(pull.center, pull.craterRadius * 2.0, pull.craterRadius * 2.0, pull.craterRadius * 2.0);
        for (LivingEntity target : pull.world.getEntitiesByClass(LivingEntity.class, cBox,
                e -> owner == null || CombatUtil.canHarm(owner, e))) {
            if (target.getEntityPos().distanceTo(pull.center) <= pull.craterRadius) {
                target.damage(pull.world, pull.world.getDamageSources().magic(), (float) pull.damage);
                target.setVelocity(0, -1.6, 0);
                ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
            }
        }
    }

    private static void restorePullEntities(MinecraftServer server, CelestialPull pull) {
        for (FallingBlockEntity fb : pull.moonBlocks) {
            if (!fb.isRemoved()) fb.discard();
        }
        pull.moonBlocks.clear();
        for (UUID id : pull.weightlessEntities) {
            Entity e = pull.world.getEntity(id);
            if (e != null) e.setNoGravity(false);
        }
        pull.weightlessEntities.clear();
    }

    @Override
    public void onPlayerTick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        Entity held = heldEntities.get(uuid);
        if (held != null) {
            if (held.isRemoved() || !held.isAlive() || held.getEntityWorld() != player.getEntityWorld()) {
                heldEntities.remove(uuid);
                releaseEntity(held);
            } else {
                double dist = gripDistances.getOrDefault(uuid, YPConfig.get().powers.illusionist.gripDefaultDistance);
                Vec3d targetPos = player.getEyePos().add(CombatUtil.safeLook(player).multiply(dist));
                Vec3d vel = targetPos.subtract(held.getEntityPos()).multiply(0.5);
                held.setVelocity(vel);
                held.fallDistance = 0.0f;
                ((EntityVelocityAccessor) held).invokeYppowersScheduleVelocityUpdate();
            }
        }

        // Passive item & XP attraction
        int rad = YPConfig.get().powers.illusionist.gripPassiveRadius;
        if (rad > 0 && player.getEntityWorld() instanceof ServerWorld world
                && world.getServer() != null && world.getServer().getTicks() % 5 == 0) {
            Box box = player.getBoundingBox().expand(rad);
            for (Entity e : world.getOtherEntities(player, box, x -> x instanceof ItemEntity || x instanceof ExperienceOrbEntity)) {
                Vec3d pull = player.getEntityPos().add(0, 0.7, 0).subtract(e.getEntityPos());
                if (pull.lengthSquared() > 0.25) {
                    e.addVelocity(pull.normalize().multiply(0.25));
                }
            }
        }
    }

    @Override
    public void onDisconnect(ServerPlayerEntity player) {
        erasureArmed.remove(player.getUuid());
        releaseEntity(heldEntities.remove(player.getUuid()));
    }

    @Override
    public void onRemove(ServerPlayerEntity player) {
        erasureArmed.remove(player.getUuid());
        releaseEntity(heldEntities.remove(player.getUuid()));
    }

    @Override
    public void onServerStopping(MinecraftServer server) {
        for (CelestialPull pull : activePulls) restorePullEntities(server, pull);
        activePulls.clear();
        for (Entity e : heldEntities.values()) releaseEntity(e);
        heldEntities.clear();
    }

    private static void releaseEntity(Entity entity) {
        if (entity != null && !entity.isRemoved()) {
            entity.setNoGravity(false);
        }
    }
}
