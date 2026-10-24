package com.yppowers.mixin;

import com.yppowers.power.impl.TimeWarperPower;
import com.yppowers.power.impl.SeriousPunchPower;
import com.yppowers.power.impl.IceEmperorPower;
import com.yppowers.power.impl.ShadowArcherPower;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin {
    @Inject(method = "spawnEntity", at = @At("HEAD"), cancellable = true)
    private void yppowers$registerShadowArcherArrowAtSpawn(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ArrowEntity arrow
                && !ShadowArcherPower.registerSpawnedArrow((ServerWorld) (Object) this, arrow)) {
            // Insufficient Energy prevents the special-arrow ability from launching at all.
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    private void yppowers$freezeEntityTick(Entity entity, CallbackInfo ci) {
        if (TimeWarperPower.isEntityFrozen(entity) || SeriousPunchPower.isEntityFrozen(entity)
                || IceEmperorPower.isEntityFrozen(entity)) ci.cancel();
    }
}
