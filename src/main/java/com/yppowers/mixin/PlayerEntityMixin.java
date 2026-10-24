package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Unique
    private Power yppowers$getPower() {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
            PowerType type = manager.getPowerType(player.getUuid());
            return type == null ? null : manager.getPowerInstance(type);
        }
        return null;
    }

    // Minecraft 1.21.11 no longer declares PlayerEntity.disableShield. Do not target the
    // removed method: with the mixin config's required injections, that would crash startup.
    // The prior shield-disable hook is therefore intentionally omitted on this version.

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yppowers$onAttack(Entity target, CallbackInfo ci) {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            if (target instanceof ServerPlayerEntity && GraceManager.isActive(player.getEntityWorld().getServer())) {
                GraceManager.notifyPvpBlocked(player);
                ci.cancel();
                return;
            }
            if (target instanceof ServerPlayerEntity targetPlayer && AllianceManager.get(player.getEntityWorld().getServer()).isAlly(player, targetPlayer)) {
                ci.cancel();
                return;
            }
        }
    }

    // Run custom on-attack effects after vanilla has applied damage and knockback. This is
    // important for the Stick of Deletion: its launch velocity must not be overwritten by
    // the remainder of the vanilla attack method.
    @Inject(method = "attack", at = @At("TAIL"))
    private void yppowers$afterAttack(Entity target, CallbackInfo ci) {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            // Passive custom hit procs (for example, Thor's lightning) are abilities too.
            if (GraceManager.isActive(player.getEntityWorld().getServer())) return;
            Power power = yppowers$getPower();
            if (power != null) power.onAttack(player, target);
        }
    }
}
