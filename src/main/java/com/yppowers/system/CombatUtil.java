package com.yppowers.system;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Shared server-side target validation, offline-safe friendly-minion checks,
 * and vanilla-melee context tracking.
 */
public final class CombatUtil {
    private static final ThreadLocal<UUID> ACTIVE_VANILLA_MELEE_PLAYER = new ThreadLocal<>();

    private CombatUtil() {}

    public static void beginVanillaMelee(UUID playerId) {
        ACTIVE_VANILLA_MELEE_PLAYER.set(playerId);
    }

    public static void endVanillaMelee() {
        ACTIVE_VANILLA_MELEE_PLAYER.remove();
    }

    public static boolean isVanillaMeleeActive(UUID playerId) {
        return playerId != null && playerId.equals(ACTIVE_VANILLA_MELEE_PLAYER.get());
    }

    public static boolean canHarm(ServerPlayerEntity owner, Entity target) {
        if (owner == null || target == null || target == owner || target.isRemoved() || !target.isAlive()) {
            return false;
        }
        MinecraftServer server = owner.getEntityWorld().getServer();
        if (server == null || GraceSystem.isActive(server)) return false;
        if (target instanceof ServerPlayerEntity other && AllianceSystem.get(server).isAlly(owner, other)) {
            return false;
        }
        if (isFriendlyMinion(owner, target)) return false;
        return true;
    }

    /**
     * Offline-safe check: returns true if {@code target} is a non-rogue summoned mob owned by
     * {@code owner} or by any of {@code owner}'s allies (even if that ally is currently offline).
     */
    public static boolean isFriendlyMinion(ServerPlayerEntity owner, Entity target) {
        if (owner == null || !(target instanceof MobEntity mob) || SummonSystem.isRogue(mob)) return false;
        MinecraftServer server = owner.getEntityWorld().getServer();
        if (server == null) return false;
        UUID minionOwner = SummonSystem.getOwnerUuid(mob);
        if (minionOwner == null) return false;
        return AllianceSystem.get(server).areAllied(owner.getUuid(), minionOwner);
    }

    /** Pick best target for a protective minion: owner's attacker -> owner's victim -> nearest threat. */
    public static LivingEntity pickDefenseTarget(ServerPlayerEntity owner, double radius) {
        if (owner == null || !(owner.getEntityWorld() instanceof ServerWorld world)) return null;
        MinecraftServer server = world.getServer();
        if (server == null || GraceSystem.isActive(server)) return null;

        LivingEntity attacker = owner.getAttacker();
        if (attacker != null && canHarm(owner, attacker)) return attacker;

        LivingEntity victim = owner.getAttacking();
        if (victim != null && canHarm(owner, victim)) return victim;

        LivingEntity best = null;
        double bestDistSq = radius * radius;
        for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class,
                owner.getBoundingBox().expand(radius),
                e -> e != owner && e.isAlive() && canHarm(owner, e))) {
            boolean isThreat = candidate instanceof Monster
                    || candidate instanceof ServerPlayerEntity
                    || (candidate instanceof MobEntity mob && mob.getTarget() == owner);
            if (!isThreat) continue;
            double dSq = owner.squaredDistanceTo(candidate);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = candidate;
            }
        }
        return best;
    }

    public static LivingEntity nearestTarget(ServerPlayerEntity owner, double radius) {
        if (owner == null || !(owner.getEntityWorld() instanceof ServerWorld world)) return null;
        MinecraftServer server = world.getServer();
        if (server == null || GraceSystem.isActive(server)) return null;
        Box box = owner.getBoundingBox().expand(radius);
        LivingEntity best = null;
        double bestDistSq = radius * radius;
        for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && canHarm(owner, e))) {
            double dSq = owner.squaredDistanceTo(candidate);
            if (dSq < bestDistSq) {
                bestDistSq = dSq;
                best = candidate;
            }
        }
        return best;
    }

    public static Vec3d safeLook(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        return look.lengthSquared() < 1.0e-6 ? new Vec3d(0.0, 0.0, 1.0) : look.normalize();
    }
}
