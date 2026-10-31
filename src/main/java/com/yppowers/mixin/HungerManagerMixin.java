package com.yppowers.mixin;

import com.yppowers.power.impl.PoseidonPower;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps current food/saturation unchanged while a living Poseidon is in its configured water state. */
@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {
    @Shadow private float exhaustion;

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void yppowers$preventPoseidonHungerDepletion(ServerPlayerEntity player, CallbackInfo ci) {
        if (PoseidonPower.shouldPreventHungerDepletion(player)) {
            // Do not bank exhaustion for an immediate food drain after leaving water. Food and
            // saturation are not set/refilled here; the vanilla update is simply skipped.
            this.exhaustion = 0.0f;
            ci.cancel();
        }
    }
}
