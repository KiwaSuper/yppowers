package com.yppowers.mixin;

import com.yppowers.alliance.AllianceManager;
import com.yppowers.command.GraceManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import com.yppowers.power.VanillaMeleeTracker;
import com.yppowers.power.impl.TimeWarperPower;
import com.yppowers.power.impl.IllusionistPower;
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

    @Inject(method = "tick", at = @At("TAIL"))
    private void yppowers$collectTimeShards(CallbackInfo ci) {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            TimeWarperPower.tryCollectTimeShard(player);
        }
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yppowers$onAttack(Entity target, CallbackInfo ci) {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            if (!GraceManager.isActive(player.getEntityWorld().getServer()) && yppowers$getPower() instanceof IllusionistPower illusionist
                    && illusionist.tryThrowGripOnAttack(player)) {
                ci.cancel();
                return;
            }
            if (target instanceof ServerPlayerEntity && GraceManager.isActive(player.getEntityWorld().getServer())) {
                GraceManager.notifyPvpBlocked(player);
                ci.cancel();
                return;
            }
            if (target instanceof ServerPlayerEntity targetPlayer && AllianceManager.get(player.getEntityWorld().getServer()).isAlly(player, targetPlayer)) {
                ci.cancel();
                return;
            }
            // Keep a narrow marker around the vanilla attack implementation. Ability code
            // runs outside this context even when it uses the PLAYER_ATTACK damage type.
            VanillaMeleeTracker.begin(player);
        }
    }

    // Run custom on-attack effects after vanilla has applied damage and knockback. This is
    // important for the Stick of Deletion: its launch velocity must not be overwritten by
    // the remainder of the vanilla attack method.
    @Inject(method = "attack", at = @At("TAIL"))
    private void yppowers$afterAttack(Entity target, CallbackInfo ci) {
        if (((Object) this) instanceof ServerPlayerEntity player && player.getEntityWorld().getServer() != null) {
            // Vanilla damage has already been applied. End the marker before running scripted
            // onAttack procs, so their playerAttack() damage is not mistaken for a melee hit.
            VanillaMeleeTracker.end(player);
            if (GraceManager.isActive(player.getEntityWorld().getServer())) return;
            Power power = yppowers$getPower();
            if (power != null) power.onAttack(player, target);
        }
    }
}
