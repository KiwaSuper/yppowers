package com.yppowers.mixin;

import com.yppowers.command.GraceManager;
import com.yppowers.power.Power;
import com.yppowers.power.PowerManager;
import com.yppowers.power.PowerType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobEntity.class)
public abstract class MobEntityMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void yppowers$setTarget(LivingEntity target, CallbackInfo ci) {
        if (target instanceof ServerPlayerEntity player && yppowers$shouldIgnore(player)) {
            ci.cancel();
        }
    }

    /**
     * Clear targets that were assigned before the player received a power, or by a mob subclass
     * that overrides setTarget. This runs before the mob's normal AI tick, preventing stale
     * target state from causing an extra attack after a defensive power becomes active.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void yppowers$clearIgnoredTargetBeforeAi(CallbackInfo ci) {
        MobEntity mob = (MobEntity) (Object) this;
        if (mob.getTarget() instanceof ServerPlayerEntity player && yppowers$shouldIgnore(player)) {
            mob.setTarget(null);
        }
    }

    @Unique
    private boolean yppowers$shouldIgnore(ServerPlayerEntity player) {
        if (player.getEntityWorld().getServer() == null
                || GraceManager.isActive(player.getEntityWorld().getServer())) return false;
        PowerManager manager = PowerManager.get(player.getEntityWorld().getServer());
        PowerType type = manager.getPowerType(player.getUuid());
        if (type == null) return false;
        Power power = manager.getPowerInstance(type);
        return power != null && power.shouldMobIgnore(player, (MobEntity) (Object) this);
    }
}
