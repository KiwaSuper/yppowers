package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.power.impl.DivinityShieldPower;
import com.yppowers.power.impl.ShadowArcherPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityMixin {
    @Inject(method = "onCollision", at = @At("HEAD"), cancellable = true)
    private void yppowers$onCollision(HitResult hitResult, CallbackInfo ci) {
        ProjectileEntity projectile = (ProjectileEntity) (Object) this;
        if (projectile.getEntityWorld().isClient()) return;
        Entity owner = projectile.getOwner();
        Entity target = hitResult.getType() == HitResult.Type.ENTITY ? ((EntityHitResult) hitResult).getEntity() : null;

        if (target instanceof ServerPlayerEntity targetPlayer && targetPlayer.getEntityWorld().getServer() != null) {
            if (owner instanceof ServerPlayerEntity ownerPlayer && ownerPlayer != targetPlayer
                    && GraceManager.isActive(targetPlayer.getEntityWorld().getServer())) {
                GraceManager.notifyPvpBlocked(ownerPlayer);
                projectile.discard();
                ci.cancel();
                return;
            }
            if (owner instanceof ServerPlayerEntity ownerPlayer && AllianceManager.get(targetPlayer.getEntityWorld().getServer()).isAlly(ownerPlayer, targetPlayer)) {
                projectile.discard();
                ci.cancel();
                return;
            }
            if (DivinityShieldPower.isShieldActive(targetPlayer) && owner != targetPlayer) {
                projectile.setVelocity(projectile.getVelocity().multiply(-1.2));
                projectile.setPosition(projectile.getX() + projectile.getVelocity().x * 0.05,
                        projectile.getY() + projectile.getVelocity().y * 0.05,
                        projectile.getZ() + projectile.getVelocity().z * 0.05);
                ((com.yppowers.mixin.EntityVelocityAccessor) projectile).invokeYppowersScheduleVelocityUpdate();
                ci.cancel();
                return;
            }
        }

        // TNT, Lightning, and Ice Prison arrows trigger at entity OR block impact.
        if (ShadowArcherPower.handleProjectileCollision(projectile, hitResult)) ci.cancel();
    }
}
