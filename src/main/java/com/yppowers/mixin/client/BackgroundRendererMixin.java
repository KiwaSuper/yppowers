package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

/** Extends lava fog for Fire God using the 1.21.11 fog-buffer API. */
@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class BackgroundRendererMixin {
    @Unique
    private boolean yppowers$extendLavaFog;

    @Inject(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At("HEAD")
    )
    private void yppowers$trackLavaFog(Camera camera, int viewDistance, RenderTickCounter renderTickCounter,
                                       float tickDelta, ClientWorld world,
                                       CallbackInfoReturnable<Vector4f> cir) {
        yppowers$extendLavaFog = ClientPowerData.hasFireGodPower
                && camera.getSubmersionType() == CameraSubmersionType.LAVA;
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private float yppowers$extendLavaFogStart(float environmentalStart) {
        return yppowers$extendLavaFog ? 0.0f : environmentalStart;
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1
    )
    private float yppowers$extendLavaFogEnd(float environmentalEnd) {
        return yppowers$extendLavaFog ? 192.0f : environmentalEnd;
    }
}
