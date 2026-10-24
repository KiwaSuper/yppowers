package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes the local player's view originate at the active Shadow Archer form's eye height. */
@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class ShadowArcherCameraMixin {
    @Inject(method = "getEyeHeight", at = @At("RETURN"), cancellable = true)
    private void yppowers$useMorphEyeHeight(EntityPose pose, CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this == client.player && ClientPowerData.shadowArcherMorphed) {
            cir.setReturnValue(ClientPowerData.shadowArcherMorphEyeHeight);
        }
    }
}
