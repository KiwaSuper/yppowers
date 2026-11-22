package com.yppowers.mixin.client;

import com.yppowers.client.ClientState;
import com.yppowers.core.PowerId;
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

/** Clear lava vision for Fire God. */
@Environment(EnvType.CLIENT)
@Mixin(FogRenderer.class)
public abstract class BackgroundRendererMixin {
    @Unique
    private boolean yppowers$extendLavaFog;

    @Inject(
            method = "applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;",
            at = @At("HEAD")
    )
    private void yppowers$trackLavaFog(Camera camera, int viewDistance, RenderTickCounter tickCounter,
                                       float tickDelta, ClientWorld world, CallbackInfoReturnable<Vector4f> cir) {
        yppowers$extendLavaFog = ClientState.hasPower(PowerId.FIRE_GOD)
                && camera.getSubmersionType() == CameraSubmersionType.LAVA;
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0
    )
    private float yppowers$lavaStart(float start) {
        return yppowers$extendLavaFog ? 0.0f : start;
    }

    @ModifyVariable(
            method = "applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1
    )
    private float yppowers$lavaEnd(float end) {
        return yppowers$extendLavaFog ? 192.0f : end;
    }
}
