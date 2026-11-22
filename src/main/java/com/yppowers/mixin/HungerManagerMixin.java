package com.yppowers.mixin;

import com.yppowers.core.PlayerDataStore;
import com.yppowers.core.Power;
import net.minecraft.entity.player.HungerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HungerManager.class)
public abstract class HungerManagerMixin {
    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void yppowers$preventHungerDepletion(ServerPlayerEntity player, CallbackInfo ci) {
        if (player == null || player.getEntityWorld().getServer() == null) return;
        Power power = PlayerDataStore.get(player.getEntityWorld().getServer()).getPower(player);
        if (power != null && power.shouldPreventHungerDepletion(player)) {
            ci.cancel();
        }
    }
}
