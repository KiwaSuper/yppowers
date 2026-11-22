package com.yppowers.mixin.client;

import com.yppowers.client.ClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class ShadowArcherCameraMixin {
    @Inject(method = "getEyeHeight", at = @At("RETURN"), cancellable = true)
    private void yppowers$morphEyeHeight(EntityPose pose, CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if ((Object) this == client.player && ClientState.shadowArcherMorphed) {
            cir.setReturnValue(Math.max(0.45f, Math.min(2.6f, ClientState.shadowArcherMorphEyeHeight)));
        }
    }
}
