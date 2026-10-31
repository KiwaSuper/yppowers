package com.yppowers.mixin;

import com.yppowers.power.impl.ThorPower;
import com.yppowers.power.impl.TeleportGambitDomainManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow @Final protected ServerPlayerEntity player;

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void yppowers$protectThorHeavyCore(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (player.getEntityWorld() instanceof ServerWorld world
                && TeleportGambitDomainManager.isProtectedChargeFrame(world, pos)) {
            cir.setReturnValue(false);
            return;
        }
        if (player.getEntityWorld() instanceof ServerWorld world
                && ThorPower.isProtectedHeavyCore(world, pos)) {
            // Cancel before vanilla break logic, including for Creative-mode players.
            cir.setReturnValue(false);
        }
    }
}
