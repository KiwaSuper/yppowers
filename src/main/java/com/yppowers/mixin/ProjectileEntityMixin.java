package com.yppowers.mixin;

import com.yppowers.core.Power;
import com.yppowers.core.PowerRegistry;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.GraceSystem;
import com.yppowers.system.SummonSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin {
    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void yppowers$onCollision(HitResult hitResult, CallbackInfo ci) {
        ProjectileEntity projectile = (ProjectileEntity) (Object) this;
        if (projectile.getEntityWorld().isClient()) return;

        Entity owner = projectile.getOwner();
        Entity target = hitResult.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) hitResult).getEntity() : null;
        MinecraftServer server = projectile.getEntityWorld().getServer();

        if (owner instanceof MobEntity shooter && target != null && yppowers$isFriendlyMinionShot(server, shooter, target)) {
            projectile.discard();
            ci.cancel();
            return;
        }

        if (target instanceof ServerPlayerEntity targetPlayer && server != null) {
            if (owner instanceof ServerPlayerEntity ownerPlayer && ownerPlayer != targetPlayer) {
                if (GraceSystem.isActive(server)) {
                    GraceSystem.notifyPvpBlocked(ownerPlayer);
                    projectile.discard();
                    ci.cancel();
                    return;
                }
                if (AllianceSystem.get(server).isAlly(ownerPlayer, targetPlayer)) {
                    projectile.discard();
                    ci.cancel();
                    return;
                }
            }
        }

        for (Power power : PowerRegistry.all()) {
            if (power.onProjectileCollision(projectile, hitResult)) {
                ci.cancel();
                return;
            }
        }
    }

    @Unique
    private static boolean yppowers$isFriendlyMinionShot(MinecraftServer server, MobEntity shooter, Entity target) {
        if (SummonSystem.isRogue(shooter)) return false;
        UUID ownerId = SummonSystem.getOwnerUuid(shooter);
        if (ownerId == null) return false;
        if (target instanceof ServerPlayerEntity player) {
            return server != null ? AllianceSystem.get(server).areAllied(ownerId, player.getUuid())
                    : ownerId.equals(player.getUuid());
        }
        if (target instanceof MobEntity otherMob && !SummonSystem.isRogue(otherMob)) {
            UUID otherOwner = SummonSystem.getOwnerUuid(otherMob);
            if (otherOwner != null) {
                return ownerId.equals(otherOwner)
                        || (server != null && AllianceSystem.get(server).areAllied(ownerId, otherOwner));
            }
        }
        return false;
    }
}
