package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Adjusts third-person camera distance so large forms fit and tiny forms remain visible. */
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class ShadowArcherThirdPersonCameraMixin {
    @ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
    private float yppowers$scaleThirdPersonDistance(float distance) {
        Camera camera = (Camera) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ClientPowerData.shadowArcherMorphed || client.player == null
                || camera.getFocusedEntity() != client.player || !camera.isThirdPerson()) return distance;

        float morphHeight = Math.max(0.1F, ClientPowerData.shadowArcherMorphHeight);
        float scale = morphHeight / 1.8F;
        scale = Math.max(0.7F, Math.min(1.6F, scale));
        return distance * scale;
    }
}
