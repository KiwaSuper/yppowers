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
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import java.util.UUID;
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

        if (owner instanceof MobEntity shooter && target != null && yppowers$isFriendlyMinionFire(shooter, target)) {
            projectile.discard();
            ci.cancel();
            return;
        }

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

    /** Summoned minions never shoot their owner, the owner's allies, or the owner's other minions. */
    @Unique
    private static boolean yppowers$isFriendlyMinionFire(MobEntity shooter, Entity target) {
        for (String tag : shooter.getCommandTags()) {
            if (!tag.startsWith("yppowers_owner_")) continue;
            try {
                UUID ownerId = UUID.fromString(tag.substring("yppowers_owner_".length()));
                if (target instanceof MobEntity mob && mob.getCommandTags().contains(tag)) return true;
                if (target instanceof ServerPlayerEntity player) {
                    if (player.getUuid().equals(ownerId)) return true;
                    if (player.getEntityWorld().getServer() != null) {
                        ServerPlayerEntity owner = player.getEntityWorld().getServer().getPlayerManager().getPlayer(ownerId);
                        if (owner != null && AllianceManager.get(player.getEntityWorld().getServer()).isAlly(owner, player)) return true;
                    }
                }
            } catch (IllegalArgumentException ignored) { }
        }
        return false;
    }
}
