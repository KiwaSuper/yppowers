package com.yppowers.power.impl;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.mob.MobEntity;
import java.util.UUID;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Shared server-side target validation helpers for the added summon powers. */
final class PowerCombatUtil {
    private PowerCombatUtil() {}

    static boolean canHarm(ServerPlayerEntity owner, Entity target) {
        if (owner == null || target == null || target == owner || target.isRemoved() || !target.isAlive()) return false;
        if (owner.getEntityWorld().getServer() == null || GraceManager.isActive(owner.getEntityWorld().getServer())) return false;
        if (target instanceof ServerPlayerEntity other && AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, other)) return false;
        if (target instanceof MobEntity mob) {
            for (String tag : mob.getCommandTags()) {
                if (!tag.startsWith("yppowers_owner_")) continue;
                try {
                    UUID ownerId = UUID.fromString(tag.substring("yppowers_owner_".length()));
                    ServerPlayerEntity minionOwner = owner.getEntityWorld().getServer().getPlayerManager().getPlayer(ownerId);
                    if (minionOwner != null && (minionOwner == owner || AllianceManager.get(owner.getEntityWorld().getServer()).isAlly(owner, minionOwner))) return false;
                } catch (IllegalArgumentException ignored) { }
            }
        }
        return true;
    }

    static LivingEntity nearestTarget(ServerPlayerEntity owner, double radius) {
        if (!(owner.getEntityWorld() instanceof ServerWorld world) || owner.getEntityWorld().getServer() == null
                || GraceManager.isActive(owner.getEntityWorld().getServer())) return null;
        AllianceManager alliances = AllianceManager.get(owner.getEntityWorld().getServer());
        Box box = owner.getBoundingBox().expand(radius);
        LivingEntity best = null;
        double bestDistance = radius * radius;
        for (LivingEntity candidate : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e != owner && e.isAlive() && canHarm(owner, e))) {
            double d = owner.squaredDistanceTo(candidate);
            if (d < bestDistance) { best = candidate; bestDistance = d; }
        }
        return best;
    }

    static Vec3d safeLook(ServerPlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0f);
        return look.lengthSquared() < 1.0e-6 ? new Vec3d(0, 0, 1) : look.normalize();
    }
}
