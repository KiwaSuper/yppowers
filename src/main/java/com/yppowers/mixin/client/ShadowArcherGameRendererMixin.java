package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies a restrained FOV adjustment for noticeably smaller/larger morph forms. */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class ShadowArcherGameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void yppowers$scaleFovForMorph(Camera camera, float tickProgress, boolean changingFov,
                                          CallbackInfoReturnable<Float> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ClientPowerData.shadowArcherMorphed || client.player == null
                || camera.getFocusedEntity() != client.player) return;

        float morphHeight = Math.max(0.1F, ClientPowerData.shadowArcherMorphHeight);
        float scale = (float) Math.sqrt(1.8F / morphHeight);
        scale = Math.max(0.85F, Math.min(1.15F, scale));
        float scaledFov = cir.getReturnValue() * scale;
        // Keep the adjustment gentle and inside the familiar playable FOV range.
        cir.setReturnValue(Math.max(30.0F, Math.min(110.0F, scaledFov)));
    }
}
