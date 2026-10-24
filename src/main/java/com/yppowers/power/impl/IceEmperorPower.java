package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.config.YPConfig;
import com.yppowers.power.Power;
import com.yppowers.power.PowerType;
import com.yppowers.power.PowerManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.DamageTypeTags;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class IceEmperorPower extends Power {
    private final Map<UUID, Integer> absoluteZeroCooldowns = new HashMap<>();
    private final Map<UUID, Integer> meteorCooldowns = new HashMap<>();
    private final Map<UUID, Integer> playerAuraTicks = new HashMap<>();
    private final Map<UUID, IceMeteor> activeMeteors = new HashMap<>();
    private final Map<UUID, Snowstorm> activeSnowstorms = new HashMap<>();

    private static final class IceMeteor {
        final ServerWorld world;
        final UUID caster;
        final double x, z;
        double y;
        final double impactY;
        final java.util.List<Entity> coreEntities = new java.util.ArrayList<>();
        IceMeteor(ServerWorld world, UUID caster, double x, double y, double z, double impactY) {
            this.world = world; this.caster = caster; this.x = x; this.y = y; this.z = z; this.impactY = impactY;
        }
    }
    private static final class Snowstorm {
        final ServerWorld world;
        final UUID caster;
        final double x, y, z;
        int ticks;
        Snowstorm(ServerWorld world, UUID caster, double x, double y, double z, int ticks) {
            this.world = world; this.caster = caster; this.x = x; this.y = y; this.z = z; this.ticks = ticks;
        }
    }
    /** Last server tick on which Glacial Aura dealt freeze damage to each hostile entity. */
    private final Map<UUID, Long> lastAuraFreezeDamageTicks = new HashMap<>();
    private long lastCleanupTick = Long.MIN_VALUE;

    private static final class IceFrozenEntity {
        final Vec3d position;
        final Vec3d velocity;
        final float yaw;
        final float pitch;
        long expiresAt;
        IceFrozenEntity(Entity entity, Vec3d originalVelocity, long expiresAt) {
            this.position = entity.getEntityPos();
            this.velocity = originalVelocity;
            this.yaw = entity.getYaw();
            this.pitch = entity.getPitch();
            this.expiresAt = expiresAt;
        }
    }
    private final Map<Entity, IceFrozenEntity> iceFrozenEntities = new IdentityHashMap<>();

    private static final class TemporaryBlockKey {
        final RegistryKey<World> dimension;
        final BlockPos pos;
        TemporaryBlockKey(RegistryKey<World> dimension, BlockPos pos) { this.dimension = dimension; this.pos = pos.toImmutable(); }
        @Override public boolean equals(Object other) {
            return other instanceof TemporaryBlockKey key && dimension.equals(key.dimension) && pos.equals(key.pos);
        }
        @Override public int hashCode() { return 31 * dimension.hashCode() + pos.hashCode(); }
    }
    private static final class TemporaryBlock {
        final ServerWorld world;
        final BlockPos pos;
        final BlockState original;
        BlockState placed;
        int ticks;
        TemporaryBlock(ServerWorld world, BlockPos pos, BlockState original, BlockState placed, int ticks) {
            this.world = world; this.pos = pos.toImmutable(); this.original = original; this.placed = placed; this.ticks = ticks;
        }
    }
    private final Map<TemporaryBlockKey, TemporaryBlock> temporaryBlocks = new HashMap<>();

    @Override public PowerType getType() { return PowerType.ICE_EMPEROR; }

    public static void serverTick(MinecraftServer server) {
        Power registered = PowerManager.get(server).getPowerInstance(PowerType.ICE_EMPEROR);
        if (registered instanceof IceEmperorPower ice && ice.lastCleanupTick != server.getTicks()) {
            ice.lastCleanupTick = server.getTicks();
            ice.tickTemporaryBlocks();
            ice.tickFrozenEntities(server);
            ice.tickIceMeteors(server);
            if (server.getTicks() % 200 == 0) {
                ice.cleanupAuraFreezeDamageTicks(server.getTicks());
            }
        }
    }

    /** True while an entity is held by Absolute Zero's ice prison. */
    public static boolean isEntityFrozen(Entity entity) {
        if (entity == null || !(entity.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return false;
        Power registered = PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR);
        return registered instanceof IceEmperorPower ice && ice.iceFrozenEntities.containsKey(entity);
    }

    /** Resolve the original velocity across overlapping time stop / punch / ice freezes. */
    public static Vec3d getFrozenVelocity(Entity entity) {
        if (entity == null || !(entity.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return null;
        Power registered = PowerManager.get(world.getServer()).getPowerInstance(PowerType.ICE_EMPEROR);
        if (!(registered instanceof IceEmperorPower ice)) return null;
        IceFrozenEntity frozen = ice.iceFrozenEntities.get(entity);
        return frozen == null ? null : frozen.velocity;
    }

    private void freezeEntity(Entity entity, long durationTicks, MinecraftServer server) {
        long expiresAt = server.getTicks() + durationTicks;
        IceFrozenEntity existing = iceFrozenEntities.get(entity);
        if (existing != null) {
            existing.expiresAt = Math.max(existing.expiresAt, expiresAt);
        } else {
            Vec3d originalVelocity = TimeWarperPower.getFrozenVelocity(entity);
            if (originalVelocity == null) originalVelocity = SeriousPunchPower.getFrozenVelocity(entity);
            if (originalVelocity == null) originalVelocity = entity.getVelocity();
            iceFrozenEntities.put(entity, new IceFrozenEntity(entity, originalVelocity, expiresAt));
        }
        entity.setVelocity(Vec3d.ZERO);
        ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
    }

    private void tickFrozenEntities(MinecraftServer server) {
        long now = server.getTicks();
        Iterator<Map.Entry<Entity, IceFrozenEntity>> iterator = iceFrozenEntities.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Entity, IceFrozenEntity> entry = iterator.next();
            Entity entity = entry.getKey();
            IceFrozenEntity frozen = entry.getValue();
            if (entity.isRemoved() || !entity.isAlive()) {
                iterator.remove();
                continue;
            }
            if (now >= frozen.expiresAt) {
                iterator.remove();
                // Another freeze may still own the entity. Let that power restore its own
                // snapshot; otherwise restore the velocity captured before the first freeze.
                if (!TimeWarperPower.isEntityFrozen(entity) && !SeriousPunchPower.isEntityFrozen(entity)) {
                    entity.setVelocity(frozen.velocity);
                    ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
                }
                continue;
            }
            entity.setPosition(frozen.position);
            entity.setVelocity(Vec3d.ZERO);
            entity.setYaw(frozen.yaw);
            entity.setPitch(frozen.pitch);
            ((com.yppowers.mixin.EntityVelocityAccessor) entity).invokeYppowersScheduleVelocityUpdate();
        }
    }

    @Override public void onAlternateActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null) return;
        boolean enabled = PowerManager.get(player.getEntityWorld().getServer()).togglePowerEnabled(player, PowerType.ICE_EMPEROR);
        if (!enabled) {
            playerAuraTicks.remove(player.getUuid());
            player.removeStatusEffect(StatusEffects.SPEED);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
            player.sendMessage(Text.literal("Ice Emperor's powers and Glacial Field are disabled. Press R to re-enable them.").formatted(Formatting.YELLOW), true);
        } else {
            player.sendMessage(Text.literal("Ice Emperor's powers and Glacial Field are enabled.").formatted(Formatting.AQUA), true);
        }
    }

    @Override public void onPlayerJoin(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() != null
                && !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.removeStatusEffect(StatusEffects.SPEED);
            player.removeStatusEffect(StatusEffects.WEAKNESS);
        }
    }

    @Override public void onPlayerRespawn(ServerPlayerEntity player, ServerPlayerEntity oldPlayer) {
        onPlayerJoin(player);
    }

    @Override public void tick(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        ServerWorld world = ((ServerWorld) player.getEntityWorld());

        int cd = absoluteZeroCooldowns.getOrDefault(uuid, 0);
        if (cd > 0) absoluteZeroCooldowns.put(uuid, cd - 1);
        int meteorCd = meteorCooldowns.getOrDefault(uuid, 0);
        if (meteorCd > 0) meteorCooldowns.put(uuid, meteorCd - 1);

        if (world.getServer() == null
                || !PowerManager.get(world.getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) return;

        int radius = Math.max(1, (int) Math.ceil(YPConfig.getInstance().ice_emperor_aura_radius));
        if (player.getFrozenTicks() > 0) player.setFrozenTicks(0);

        int auraTicks = playerAuraTicks.merge(uuid, 1, Integer::sum);
        if (auraTicks >= 5) {
            playerAuraTicks.put(uuid, 0);
            applyGlacialAura(player, world, radius);
            spawnVisibleGlacialField(player, world, radius);
        }

        // Build a continuous slippery ribbon while the player sprint-glides. The previous
        // implementation only refreshed every fourth tick, leaving gaps at normal sprint speed.
        Vec3d velocity = player.getVelocity();
        double horizontalSpeedSquared = velocity.x * velocity.x + velocity.z * velocity.z;
        boolean sprintGliding = player.isSprinting() && horizontalSpeedSquared > 0.0025;
        if (sprintGliding) {
            double trailX = player.getX() - velocity.x * 1.15;
            double trailZ = player.getZ() - velocity.z * 1.15;
            double trailY = player.getY() + 0.06;

            // Visual trail is refreshed every server tick, including airborne sprints.
            world.spawnParticles(ParticleTypes.SNOWFLAKE, trailX, trailY, trailZ,
                    3, 0.09, 0.025, 0.09, 0.002);
            if (player.age % 2 == 0) {
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                        trailX, trailY, trailZ, 1, 0.06, 0.015, 0.06, 0.015);
            }

            // Place ice at the current footfall and slightly behind it. Temporary-block
            // bookkeeping preserves the original ground and refreshes the trail without gaps.
            if (player.isOnGround()) {
                BlockPos underfoot = player.getBlockPos().down();
                placeTrailIceIfSafe(world, underfoot);
                BlockPos behind = BlockPos.ofFloored(trailX, player.getY(), trailZ).down();
                if (!behind.equals(underfoot)) placeTrailIceIfSafe(world, behind);
            }
        }

        BlockState under = world.getBlockState(player.getBlockPos().down());
        if (isIce(under) || sprintGliding) {
            // Refresh every tick so the speed aid does not flicker as the player crosses
            // between freshly-created ice blocks.
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20, 0, false, false, true));
        }
        if (world.getRegistryKey() == World.NETHER) player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 0, false, false, true));

        // Temporary blocks are aged by serverTick(), even after the power is unassigned.
    }

    private void placeTrailIceIfSafe(ServerWorld world, BlockPos pos) {
        TemporaryBlockKey key = new TemporaryBlockKey(world.getRegistryKey(), pos);
        if (temporaryBlocks.containsKey(key)) {
            // Refresh only blocks this trail already owns; natural ice elsewhere is left intact.
            placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), 60);
            return;
        }
        BlockState state = world.getBlockState(pos);
        if (state.isOf(Blocks.HEAVY_CORE) && ThorPower.isProtectedHeavyCore(world, pos)) return;
        if (isIce(state) || !state.isOpaque() || state.hasBlockEntity()
                || state.getHardness(world, pos) < 0 || state.isOf(Blocks.BEDROCK)) return;
        placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), 60);
    }

    /** A persistent snowflake cylinder makes the passive field visible, not just its effects. */
    private void spawnVisibleGlacialField(ServerPlayerEntity player, ServerWorld world, int radius) {
        double x = player.getX();
        double z = player.getZ();
        double[] heights = {0.15, 1.0, 1.85};
        for (double height : heights) {
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle + (player.age * 3 % 360));
                double px = x + Math.cos(radians) * radius;
                double pz = z + Math.sin(radians) * radius;
                world.spawnParticles(ParticleTypes.SNOWFLAKE, px, player.getY() + height, pz,
                        1, 0.025, 0.04, 0.025, 0.005);
            }
        }
        // A smaller, slowly rotating inner ring gives the field depth without flooding the server.
        for (int angle = 0; angle < 360; angle += 45) {
            double radians = Math.toRadians(-angle + player.age * 2 % 360);
            double innerRadius = radius * 0.58;
            world.spawnParticles(ParticleTypes.SNOWFLAKE,
                    x + Math.cos(radians) * innerRadius, player.getY() + 1.0,
                    z + Math.sin(radians) * innerRadius, 1, 0.02, 0.03, 0.02, 0.002);
        }
    }

    private void applyGlacialAura(ServerPlayerEntity player, ServerWorld world, int radius) {
        BlockPos center = player.getBlockPos();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.add(x, y, z);
                    if (pos.getSquaredDistance(center) > radius * radius) continue;
                    BlockState state = world.getBlockState(pos);
                    if (state.isOf(Blocks.WATER)) {
                        placeTemporary(world, pos, Blocks.FROSTED_ICE.getDefaultState(), 100);
                    } else if (state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE)) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                    }
                }
            }
        }

        Box box = player.getBoundingBox().expand(radius);
        long now = world.getServer().getTicks();
        YPConfig config = YPConfig.getInstance();
        int damageInterval = Math.max(1, config.ice_emperor_aura_freeze_damage_interval_ticks);
        float freezeDamage = (float) Math.max(0.0, config.ice_emperor_aura_freeze_damage);
        for (HostileEntity mob : world.getEntitiesByClass(HostileEntity.class, box,
                e -> e.isAlive() && e.squaredDistanceTo(player) <= radius * radius)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0, false, false, true));

            // Glacial Aura deals vanilla freeze-type damage, at a controlled interval, while
            // a hostile remains inside the field. The shared per-entity timestamp prevents
            // overlapping Ice Emperor fields from multiplying the damage rate.
            long lastDamageTick = lastAuraFreezeDamageTicks.computeIfAbsent(mob.getUuid(), ignored -> now);
            if (freezeDamage > 0.0f && now - lastDamageTick >= damageInterval) {
                mob.damage(world, world.getDamageSources().freeze(), freezeDamage);
                lastAuraFreezeDamageTicks.put(mob.getUuid(), now);
                world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        mob.getX(), mob.getY() + 1, mob.getZ(), 6, 0.22, 0.24, 0.22, 0.04);
                world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_GLASS_HIT,
                        SoundCategory.HOSTILE, 0.35f, 1.8f);
            }
            world.spawnParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getY() + 1, mob.getZ(),
                    2, 0.2, 0.2, 0.2, 0.05);
        }
    }

    private void cleanupAuraFreezeDamageTicks(long now) {
        // Hostile UUIDs can accumulate as mobs die or despawn. Entries idle for a minute
        // are no longer needed for the field's damage cadence.
        lastAuraFreezeDamageTicks.entrySet().removeIf(entry -> now - entry.getValue() > 1200);
    }

    private static boolean isIce(BlockState state) {
        return state.isOf(Blocks.PACKED_ICE) || state.isOf(Blocks.ICE) || state.isOf(Blocks.BLUE_ICE) || state.isOf(Blocks.FROSTED_ICE);
    }

    private void placeTemporary(ServerWorld world, BlockPos pos, BlockState placed, int ticks) {
        TemporaryBlockKey key = new TemporaryBlockKey(world.getRegistryKey(), pos);
        TemporaryBlock active = temporaryBlocks.get(key);
        if (active != null) {
            active.ticks = Math.max(active.ticks, ticks);
            if (world.getBlockState(pos).isOf(active.placed.getBlock())) {
                world.setBlockState(pos, placed, 3);
                active.placed = placed;
            }
            return;
        }
        BlockState original = world.getBlockState(pos);
        temporaryBlocks.put(key, new TemporaryBlock(world, pos, original, placed, ticks));
        world.setBlockState(pos, placed, 3);
    }

    private void tickTemporaryBlocks() {
        Iterator<Map.Entry<TemporaryBlockKey, TemporaryBlock>> iterator = temporaryBlocks.entrySet().iterator();
        while (iterator.hasNext()) {
            TemporaryBlock block = iterator.next().getValue();
            if (--block.ticks > 0) continue;
            // Do not overwrite a block another system/player changed after we placed the ice.
            if (block.world.getBlockState(block.pos).isOf(block.placed.getBlock())) {
                block.world.setBlockState(block.pos, block.original, 3);
            }
            iterator.remove();
        }
    }

    @Override public float modifyOutgoingDamage(ServerPlayerEntity player, Entity target, float amount) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) return amount;
        return isIce(player.getEntityWorld().getBlockState(player.getBlockPos().down())) ? amount * 1.2f : amount;
    }

    @Override public float modifyIncomingDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) return amount;
        if (source.isOf(DamageTypes.FREEZE)) return 0.0f;
        if (source.isIn(DamageTypeTags.IS_FIRE) || source.isOf(DamageTypes.LAVA) || source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE)) return amount * 1.3f;
        return amount;
    }

    /** H: call down an enormous ice meteor; impact radius and cooldown are configurable. */
    @Override public void onSecondaryActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
            return;
        }
        UUID uuid = player.getUuid();
        int cooldown = meteorCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            player.sendMessage(Text.literal("Ice Meteor on cooldown: " + ((cooldown + 19) / 20) + "s"), true);
            return;
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos target = player.getBlockPos().offset(player.getHorizontalFacing(), 18);
        // Aim forward, but never spawn the meteor below the target's surface.
        double x = target.getX() + 0.5;
        double z = target.getZ() + 0.5;
        int surfaceY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, target.getX(), target.getZ());
        double impactY = Math.max(world.getBottomY() + 2, surfaceY + 1.0);
        IceMeteor meteor = new IceMeteor(world, uuid, x, Math.max(impactY + 36, player.getY() + 36), z, impactY);

        // A dense sphere of real snowball entities forms the meteor's core. They are
        // controlled as one cluster so the impact is synchronized rather than scattered.
        for (int i = 0; i < 64; i++) {
            double yy = 1.0 - 2.0 * (i + 0.5) / 64.0;
            double ring = Math.sqrt(Math.max(0, 1.0 - yy * yy));
            double angle = i * Math.PI * (3.0 - Math.sqrt(5.0));
            Entity core = EntityType.SNOWBALL.create(world, SpawnReason.TRIGGERED);
            if (core == null) continue;
            core.setNoGravity(true);
            core.setPosition(x + Math.cos(angle) * ring * 3.0, meteor.y + yy * 3.0, z + Math.sin(angle) * ring * 3.0);
            if (world.spawnEntity(core)) meteor.coreEntities.add(core);
        }
        activeMeteors.put(uuid, meteor);
        meteorCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().ice_emperor_meteor_cooldown_ticks));
        world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.2f, 0.55f);
        player.sendMessage(Text.literal("ICE METEOR INCOMING!").formatted(Formatting.AQUA, Formatting.BOLD), true);
    }

    private void tickIceMeteors(MinecraftServer server) {
        Iterator<Map.Entry<UUID, IceMeteor>> meteorIterator = activeMeteors.entrySet().iterator();
        while (meteorIterator.hasNext()) {
            IceMeteor meteor = meteorIterator.next().getValue();
            meteor.y -= 1.35;
            for (Entity entity : meteor.coreEntities) {
                if (entity.isRemoved()) continue;
                double dx = entity.getX() - meteor.x;
                double dy = entity.getY() - (meteor.y + (entity.getY() - meteor.y));
                double dz = entity.getZ() - meteor.z;
                entity.setPosition(meteor.x + dx, meteor.y + (entity.getY() - (meteor.y + 1.35)), meteor.z + dz);
            }
            // Massive spherical particle shell surrounding the entity core.
            for (int i = 0; i < 100; i++) {
                double yy = 1.0 - 2.0 * (i + 0.5) / 100.0;
                double ring = Math.sqrt(Math.max(0, 1.0 - yy * yy));
                double angle = i * Math.PI * (3.0 - Math.sqrt(5.0)) + server.getTicks() * 0.045;
                meteor.world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        meteor.x + Math.cos(angle) * ring * 4.1, meteor.y + yy * 4.1,
                        meteor.z + Math.sin(angle) * ring * 4.1, 1, 0.04, 0.04, 0.04, 0.01);
            }
            meteor.world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()),
                    meteor.x, meteor.y, meteor.z, 35, 1.2, 1.2, 1.2, 0.08);
            if (meteor.y <= meteor.impactY) {
                for (Entity entity : meteor.coreEntities) if (!entity.isRemoved()) entity.discard();
                impactIceMeteor(meteor);
                meteorIterator.remove();
            }
        }
        Iterator<Map.Entry<UUID, Snowstorm>> stormIterator = activeSnowstorms.entrySet().iterator();
        while (stormIterator.hasNext()) {
            Snowstorm storm = stormIterator.next().getValue();
            if (--storm.ticks <= 0) { stormIterator.remove(); continue; }
            for (int i = 0; i < 30; i++) {
                double angle = Math.random() * Math.PI * 2;
                double radius = Math.random() * 12.0;
                storm.world.spawnParticles(ParticleTypes.SNOWFLAKE,
                        storm.x + Math.cos(angle) * radius, storm.y + Math.random() * 8.0,
                        storm.z + Math.sin(angle) * radius, 1, 0.12, 0.08, 0.12, 0.02);
            }
        }
    }

    private void impactIceMeteor(IceMeteor meteor) {
        YPConfig config = YPConfig.getInstance();
        double radius = Math.max(1.0, Math.min(128.0, config.ice_emperor_meteor_radius));
        int blockRadius = (int) Math.ceil(radius);
        BlockPos center = BlockPos.ofFloored(meteor.x, meteor.impactY, meteor.z);
        // Cover the circular impact zone with temporary packed ice, without replacing
        // containers, bedrock, or blocks that are not safe to overwrite.
        for (int dx = -blockRadius; dx <= blockRadius; dx++) {
            for (int dz = -blockRadius; dz <= blockRadius; dz++) {
                if ((double) dx * dx + (double) dz * dz > radius * radius) continue;
                int topY = meteor.world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, center.getX() + dx, center.getZ() + dz);
                BlockPos ground = new BlockPos(center.getX() + dx, topY - 1, center.getZ() + dz);
                BlockPos cover = ground.up();
                BlockState state = meteor.world.getBlockState(cover);
                if (state.isReplaceable() && !state.hasBlockEntity() && meteor.world.getBlockState(ground).isOpaque()) {
                    placeTemporary(meteor.world, cover, Blocks.PACKED_ICE.getDefaultState(), Math.max(20, config.ice_emperor_meteor_ice_duration_ticks));
                }
            }
        }
        ServerPlayerEntity caster = meteor.world.getServer().getPlayerManager().getPlayer(meteor.caster);
        AllianceManager alliances = AllianceManager.get(meteor.world.getServer());
        Box affected = new Box(meteor.x - radius, meteor.impactY - 8, meteor.z - radius,
                meteor.x + radius, meteor.impactY + 12, meteor.z + radius);
        int effectTicks = Math.max(20, config.ice_emperor_meteor_effect_duration_ticks);
        for (LivingEntity target : meteor.world.getEntitiesByClass(LivingEntity.class, affected,
                e -> e.isAlive() && e.squaredDistanceTo(meteor.x, meteor.impactY, meteor.z) <= radius * radius
                        && e != caster && (caster == null || !alliances.isAlly(caster, e)))) {
            target.damage(meteor.world, meteor.world.getDamageSources().freeze(), (float) Math.max(0, config.ice_emperor_meteor_damage));
            target.setFrozenTicks(Math.max(target.getFrozenTicks(), effectTicks));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, effectTicks, 3, false, true, true));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, effectTicks, 0, false, true, true));
        }
        if (caster != null) {
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, effectTicks, 1, false, true, true));
        }
        activeSnowstorms.put(meteor.caster, new Snowstorm(meteor.world, meteor.caster, meteor.x, meteor.impactY, meteor.z, effectTicks));
        meteor.world.spawnParticles(ParticleTypes.EXPLOSION, meteor.x, meteor.impactY + 1, meteor.z, 8, 2.5, 1.0, 2.5, 0.05);
        meteor.world.spawnParticles(ParticleTypes.SNOWFLAKE, meteor.x, meteor.impactY + 1, meteor.z, 500, radius / 3.0, 1.8, radius / 3.0, 0.12);
        meteor.world.playSound(null, center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0f, 0.6f);
    }

    @Override public void onPrimaryActivate(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null
                || !PowerManager.get(player.getEntityWorld().getServer()).isPowerEnabled(player, PowerType.ICE_EMPEROR)) {
            player.sendMessage(Text.literal("Ice Emperor is disabled. Press R to re-enable it.").formatted(Formatting.YELLOW), true);
            return;
        }
        UUID uuid = player.getUuid();
        int cooldown = absoluteZeroCooldowns.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            player.sendMessage(net.minecraft.text.Text.literal("Absolute Zero on cooldown: " + cooldown / 20 + "s"), true);
            return;
        }
        ServerWorld world = ((ServerWorld) player.getEntityWorld());
        AllianceManager alliances = AllianceManager.get(world.getServer());
        Box box = player.getBoundingBox().expand(10.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != player && e.squaredDistanceTo(player) <= 100.0 && !alliances.isAlly(player, e))) {
            target.damage(world, world.getDamageSources().magic(), 4.0f);
            if (target.isRemoved() || !target.isAlive()) continue;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3, false, false, true));
            freezeEntity(target, Math.max(1, YPConfig.getInstance().ice_emperor_freeze_duration_ticks), world.getServer());
            BlockPos center = target.getBlockPos();
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y <= 2; y++) {
                    for (int z = -1; z <= 1; z++) {
                        boolean shell = x == -1 || x == 1 || z == -1 || z == 1 || y == 0 || y == 2;
                        if (!shell) continue;
                        BlockPos pos = center.add(x, y, z);
                        if (world.getBlockState(pos).isReplaceable() && !world.getBlockState(pos).hasBlockEntity()) {
                            placeTemporary(world, pos, Blocks.PACKED_ICE.getDefaultState(), YPConfig.getInstance().ice_emperor_freeze_duration_ticks);
                        }
                    }
                }
            }
            world.spawnParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
            world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.PACKED_ICE.getDefaultState()), target.getX(), target.getY() + 1, target.getZ(), 20, 0.5, 0.5, 0.5, 0.1);
        }
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.0f, 0.5f);
        for (int angle = 0; angle < 360; angle += 10) {
            double radians = Math.toRadians(angle);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX() + Math.cos(radians) * 10, player.getY() + 0.5, player.getZ() + Math.sin(radians) * 10, 1, 0, 0, 0, 0.1);
        }
        absoluteZeroCooldowns.put(uuid, Math.max(0, YPConfig.getInstance().ice_emperor_cooldown_ticks));
    }

    @Override public void onRemove(ServerPlayerEntity player) {
        absoluteZeroCooldowns.remove(player.getUuid());
        meteorCooldowns.remove(player.getUuid());
        IceMeteor meteor = activeMeteors.remove(player.getUuid());
        if (meteor != null) for (Entity entity : meteor.coreEntities) if (!entity.isRemoved()) entity.discard();
        activeSnowstorms.remove(player.getUuid());
        playerAuraTicks.remove(player.getUuid());
        player.removeStatusEffect(StatusEffects.SPEED);
        player.removeStatusEffect(StatusEffects.WEAKNESS);
    }
}
