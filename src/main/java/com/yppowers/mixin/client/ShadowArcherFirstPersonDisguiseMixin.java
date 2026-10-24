package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides only the owner's separate disguise entity in first person, avoiding a mob model inside the camera. */
@Environment(EnvType.CLIENT)
@Mixin(EntityRenderManager.class)
public abstract class ShadowArcherFirstPersonDisguiseMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void yppowers$hideDisguiseInFirstPerson(Entity entity, Frustum frustum,
                                                   double x, double y, double z,
                                                   CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (ClientPowerData.shadowArcherMorphed && client.player != null
                && ClientPowerData.shadowArcherDisguiseEntityId >= 0
                && entity.getId() == ClientPowerData.shadowArcherDisguiseEntityId
                && client.options.getPerspective().isFirstPerson()) {
            cir.setReturnValue(false);
        }
    }
}
