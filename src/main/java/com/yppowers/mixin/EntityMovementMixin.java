package com.yppowers.mixin;

import com.yppowers.core.Power;
import com.yppowers.core.PowerRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void yppowers$blockMovement(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.getEntityWorld().isClient()) return;
        for (Power power : PowerRegistry.all()) {
            if (power.blocksEntityMovement(entity, movement)) {
                ci.cancel();
                return;
            }
        }
    }
}
