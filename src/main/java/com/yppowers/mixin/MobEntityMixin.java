package com.yppowers.mixin;

import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import com.yppowers.system.AllianceSystem;
import com.yppowers.system.GraceSystem;
import com.yppowers.system.SummonSystem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void yppowers$setTarget(LivingEntity target, CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        if (target instanceof ServerPlayerEntity player && yppowers$shouldIgnore(mob, player)) {
            ci.cancel();
        } else if (target != null && yppowers$sameSide(mob, target)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void yppowers$clearIgnoredTarget(CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        if (target instanceof ServerPlayerEntity player && yppowers$shouldIgnore(mob, player)) {
            mob.setTarget(null);
        } else if (yppowers$sameSide(mob, target)) {
            mob.setTarget(null);
        }
    }

    @Unique
    private static boolean yppowers$sameSide(MobEntity a, LivingEntity target) {
        if (!(target instanceof MobEntity b)) return false;
        if (SummonSystem.isRogue(a) || SummonSystem.isRogue(b)) return false;
        UUID ownerA = SummonSystem.getOwnerUuid(a);
        UUID ownerB = SummonSystem.getOwnerUuid(b);
        if (ownerA == null || ownerB == null) return false;
        if (ownerA.equals(ownerB)) return true;
        MinecraftServer server = a.getEntityWorld().getServer();
        return server != null && AllianceSystem.get(server).areAllied(ownerA, ownerB);
    }

    @Unique
    private static boolean yppowers$shouldIgnore(MobEntity mob, ServerPlayerEntity player) {
        MinecraftServer server = player.getEntityWorld().getServer();
        if (server == null) return false;

        if (!SummonSystem.isRogue(mob)) {
            UUID ownerId = SummonSystem.getOwnerUuid(mob);
            if (ownerId != null && AllianceSystem.get(server).areAllied(ownerId, player.getUuid())) {
                return true;
            }
        }
        if (GraceSystem.isActive(server)) return false;
        Power power = PlayerDataStore.get(server).getPower(player);
        return power != null && power.shouldMobIgnore(player, mob);
    }
}
