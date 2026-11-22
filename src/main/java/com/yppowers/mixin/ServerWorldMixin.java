package com.yppowers.mixin;

import com.yppowers.core.Power;
import com.yppowers.core.PowerRegistry;
import com.yppowers.system.FreezeSystem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void yppowers$onSpawnEntity(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof PersistentProjectileEntity arrow) {
            ServerWorld world = (ServerWorld) (Object) this;
            for (Power power : PowerRegistry.all()) {
                if (!power.onArrowSpawned(world, arrow)) {
                    cir.setReturnValue(false);
                    return;
                }
            }
        }
    }

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void yppowers$freezeEntityTick(Entity entity, CallbackInfo ci) {
        if (FreezeSystem.isFrozen(entity)) {
            ci.cancel();
        }
    }
}
