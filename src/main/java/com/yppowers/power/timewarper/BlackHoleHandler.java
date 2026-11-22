package com.yppowers.power.timewarper;

import com.yppowers.config.PowerConfigs;
import com.yppowers.config.YPConfig;
import com.yppowers.mixin.EntityVelocityAccessor;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.CooldownSystem;
import com.yppowers.system.TempBlockSystem;
import com.yppowers.system.VfxSystem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Handles Time Warper's Black Hole ultimate (charge -> item/terrain pull -> charged implosion). */
final class BlackHoleHandler {
    static final String CD_BLACK_HOLE = "time_warper_black_hole";

    private static final class ActiveBlackHole {
        final UUID ownerId;
        final ServerWorld world;
        final Vec3d center;
        final int chargeTicks;
        final int pullTicks;
        final ArrayDeque<BlockPos> terrainQueue = new ArrayDeque<>();
        int tick;
        double absorbedItems;

        ActiveBlackHole(UUID ownerId, ServerWorld world, Vec3d center, int chargeTicks, int pullTicks) {
            this.ownerId = ownerId;
            this.world = world;
            this.center = center;
            this.chargeTicks = chargeTicks;
            this.pullTicks = pullTicks;
        }
    }

    private final Map<UUID, ActiveBlackHole> active = new HashMap<>();

    boolean isChargingOrActive(UUID playerId) {
        return active.containsKey(playerId);
    }

    void start(ServerPlayerEntity caster) {
        if (!(caster.getEntityWorld() instanceof ServerWorld world) || world.getServer() == null) return;
        PowerConfigs.TimeWarper cfg = YPConfig.get().powers.timeWarper;

        // Health sacrifice (never lethal; leaves at least 1 HP)
        float sacrifice = (float) (caster.getHealth() * cfg.blackHoleHealthSacrificePercent);
        if (sacrifice > 0.0f && caster.getHealth() > 1.0f) {
            caster.setHealth(Math.max(1.0f, caster.getHealth() - sacrifice));
        }

        HitResult hit = caster.raycast(24.0, 0.0f, false);
        Vec3d center = hit.getPos().add(0, 2.5, 0);
        ActiveBlackHole bh = new ActiveBlackHole(caster.getUuid(), world, center, cfg.blackHoleChargeTicks, cfg.blackHolePullTicks);

        if (cfg.blackHoleTerrainDamage && cfg.blackHoleTerrainRadius > 0.0) {
            int r = (int) Math.min(16, Math.ceil(cfg.blackHoleTerrainRadius));
            BlockPos base = BlockPos.ofFloored(center);
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= r * r) {
                            bh.terrainQueue.add(base.add(dx, dy, dz));
                        }
                    }
                }
            }
        }

        active.put(caster.getUuid(), bh);
        CooldownSystem.get(world.getServer()).setPersistent(caster.getUuid(), CD_BLACK_HOLE, cfg.blackHoleCooldownTicks);
        VfxSystem.playSound(world, center, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.5f, 0.5f);
        caster.sendMessage(Text.literal("SINGULARITY FORMING!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
    }

    void serverTick(MinecraftServer server) {
        if (active.isEmpty()) return;
        PowerConfigs.TimeWarper cfg = YPConfig.get().powers.timeWarper;
        Iterator<Map.Entry<UUID, ActiveBlackHole>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            ActiveBlackHole bh = it.next().getValue();
            bh.tick++;
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(bh.ownerId);
            double chargeRatio = Math.min(1.0, bh.absorbedItems / Math.max(1.0, cfg.blackHoleItemsForMaxPower));

            if (bh.tick <= bh.chargeTicks) {
                if (bh.tick % 3 == 0) {
                    VfxSystem.ring(bh.world, ParticleTypes.PORTAL, bh.center, 1.5 + 3.0 * (1.0 - (double) bh.tick / bh.chargeTicks), 20, 0.1);
                }
                continue;
            }

            // Pull phase
            int pullElapsed = bh.tick - bh.chargeTicks;
            double diskRadius = 2.5 + chargeRatio * 4.0;
            if (bh.tick % 2 == 0) {
                VfxSystem.ring(bh.world, ParticleTypes.SQUID_INK, bh.center, 1.2 + chargeRatio * 1.5, 16, 0.0);
                VfxSystem.ring(bh.world, ParticleTypes.REVERSE_PORTAL, bh.center, diskRadius, 24, 0.05);
                if (chargeRatio > 0.15) {
                    VfxSystem.lightPillar(bh.world, ParticleTypes.END_ROD, bh.center.subtract(0, 4.0, 0), 8.0 + chargeRatio * 6.0, 12);
                }
            }

            // Bounded terrain consumption (no item drops)
            if (cfg.blackHoleTerrainDamage && !bh.terrainQueue.isEmpty()) {
                int budget = cfg.blackHoleMaxBlocksPerTick;
                while (budget > 0 && !bh.terrainQueue.isEmpty()) {
                    BlockPos pos = bh.terrainQueue.poll();
                    var state = bh.world.getBlockState(pos);
                    if (!state.isAir() && !TempBlockSystem.isProtectedBlock(bh.world, pos, state)) {
                        bh.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
                        bh.absorbedItems += cfg.blackHoleBlockItemValue;
                        budget--;
                    }
                }
            }

            // Pull entities & swallow items
            Box box = Box.of(bh.center, cfg.blackHoleEntityRadius * 2.0, cfg.blackHoleEntityRadius * 2.0, cfg.blackHoleEntityRadius * 2.0);
            for (Entity e : bh.world.getEntitiesByClass(Entity.class, box, Entity::isAlive)) {
                if (e instanceof ItemEntity item && cfg.blackHoleAffectItems) {
                    Vec3d toCenter = bh.center.subtract(item.getEntityPos());
                    double dist = toCenter.length();
                    if (dist < 2.0) {
                        bh.absorbedItems += item.getStack().getCount();
                        item.discard();
                    } else if (dist <= cfg.blackHoleEntityRadius) {
                        item.setVelocity(toCenter.normalize().multiply(0.8));
                        ((EntityVelocityAccessor) item).invokeYppowersScheduleVelocityUpdate();
                    }
                    continue;
                }
                if (owner != null && !CombatUtil.canHarm(owner, e)) continue;
                if (e instanceof ServerPlayerEntity && !cfg.blackHoleAffectPlayers) continue;
                if (!(e instanceof ServerPlayerEntity) && e instanceof LivingEntity && !cfg.blackHoleAffectMobs) continue;

                Vec3d toCenter = bh.center.subtract(e.getEntityPos());
                double dist = toCenter.length();
                if (dist > 0.4 && dist <= cfg.blackHoleEntityRadius) {
                    e.setVelocity(toCenter.normalize().multiply(0.65));
                    ((EntityVelocityAccessor) e).invokeYppowersScheduleVelocityUpdate();
                }
            }

            if (pullElapsed >= bh.pullTicks) {
                implode(server, bh, cfg, chargeRatio);
                it.remove();
            }
        }
    }

    private static void implode(MinecraftServer server, ActiveBlackHole bh, PowerConfigs.TimeWarper cfg, double chargeRatio) {
        ServerPlayerEntity owner = server.getPlayerManager().getPlayer(bh.ownerId);
        double mult = 1.0 + chargeRatio * (cfg.blackHoleMaxPowerMultiplier - 1.0);
        double radius = cfg.blackHoleImplosionRadius * mult;
        double damage = Math.min(cfg.blackHoleMaxBlastDamage, cfg.blackHoleMaxDamage * mult);

        VfxSystem.flash(bh.world, bh.center);
        VfxSystem.shockwave(bh.world, ParticleTypes.EXPLOSION, ParticleTypes.PORTAL, bh.center, radius, 44);
        VfxSystem.playSound(bh.world, bh.center, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.4f, 0.45f);

        for (int i = 0; i < 4; i++) {
            double ang = (Math.PI * 0.5) * i;
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(bh.world, SpawnReason.TRIGGERED);
            if (bolt != null) {
                if (owner != null) bolt.setChanneler(owner);
                bolt.refreshPositionAfterTeleport(bh.center.x + Math.cos(ang) * 3.0, bh.center.y, bh.center.z + Math.sin(ang) * 3.0);
                bh.world.spawnEntity(bolt);
            }
        }

        Box box = Box.of(bh.center, radius * 2.0, radius * 2.0, radius * 2.0);
        for (LivingEntity target : bh.world.getEntitiesByClass(LivingEntity.class, box,
                e -> owner == null || CombatUtil.canHarm(owner, e))) {
            double dist = target.getEntityPos().distanceTo(bh.center);
            if (dist > radius) continue;
            double falloff = Math.max(0.25, 1.0 - (dist / radius) * 0.75);
            target.damage(bh.world, bh.world.getDamageSources().magic(), (float) (damage * falloff));
            Vec3d out = target.getEntityPos().subtract(bh.center);
            out = out.lengthSquared() < 1.0e-4 ? new Vec3d(0, 1, 0) : out.normalize();
            target.setVelocity(out.x * 1.8 * mult, 0.8 * mult, out.z * 1.8 * mult);
            ((EntityVelocityAccessor) target).invokeYppowersScheduleVelocityUpdate();
        }
    }

    void clearAll() {
        active.clear();
    }
}
