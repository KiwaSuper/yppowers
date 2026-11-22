package com.yppowers.mixin.client;

import com.yppowers.client.ClientState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class ShadowArcherThirdPersonCameraMixin {
    @ModifyVariable(method = "clipToSpace", at = @At("HEAD"), argsOnly = true)
    private float yppowers$scaleThirdPersonDistance(float distance) {
        Camera camera = (Camera) (Object) this;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ClientState.shadowArcherMorphed || client.player == null
                || camera.getFocusedEntity() != client.player || !camera.isThirdPerson()) {
            return distance;
        }
        float scale = Math.max(0.7f, Math.min(1.6f, Math.max(0.1f, ClientState.shadowArcherMorphHeight) / 1.8f));
        return distance * scale;
    }
}
