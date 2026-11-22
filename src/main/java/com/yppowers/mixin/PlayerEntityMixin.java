package com.yppowers.mixin;

import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.CombatUtil;
import com.yppowers.system.GraceSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void yppowers$preAttack(Entity target, CallbackInfo ci) {
        if (!(((Object) this) instanceof ServerPlayerEntity player)) return;
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return;

        if (!GraceSystem.isActive(server)) {
            Power power = PlayerDataStore.get(server).getPower(player);
            if (power != null && power.onPreAttack(player, target)) {
                ci.cancel();
                return;
            }
        }

        if (target instanceof ServerPlayerEntity targetPlayer) {
            if (GraceSystem.isActive(server)) {
                GraceSystem.notifyPvpBlocked(player);
                ci.cancel();
                return;
            }
            if (AllianceSystem.get(server).isAlly(player, targetPlayer)) {
                ci.cancel();
                return;
            }
        }

        CombatUtil.beginVanillaMelee(player.getUuid());
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void yppowers$afterAttack(Entity target, CallbackInfo ci) {
        if (!(((Object) this) instanceof ServerPlayerEntity player)) return;
        CombatUtil.endVanillaMelee();
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null || GraceSystem.isActive(server)) return;
        Power power = PlayerDataStore.get(server).getPower(player);
        if (power != null) power.onAttack(player, target);
    }
}
