package com.yppowers.mixin;

import com.yppowers.power.impl.DivinityShieldPower;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMovementMixin {
    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void yppowers$blockDomeEntry(MovementType movementType, Vec3d movement, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        World world = entity.getEntityWorld();
        if (!world.isClient() && DivinityShieldPower.blocksEntityMovement(entity, movement)) {
            ci.cancel();
        }
    }
}
