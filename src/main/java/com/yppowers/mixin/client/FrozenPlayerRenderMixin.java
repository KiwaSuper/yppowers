package com.yppowers.mixin.client;

import com.yppowers.client.ClientPowerData;
import com.yppowers.client.render.FrozenPlayerRenderContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a muted monochrome-gray tint to player models while Time Stop is active on them. */
@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class FrozenPlayerRenderMixin {
    private static final int YPPOWERS_FROZEN_GRAY = 0xFFB3B3B3;

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void yppowers$setFrozenPlayerRenderContext(LivingEntityRenderState state, MatrixStack matrices,
                                                        OrderedRenderCommandQueue queue, CameraRenderState cameraState,
                                                        CallbackInfo ci) {
        boolean frozenPlayer = state instanceof PlayerEntityRenderState playerState
                && ClientPowerData.timeStoppedPlayerEntityIds.contains(playerState.id);
        FrozenPlayerRenderContext.setFrozenPlayer(frozenPlayer);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN")
    )
    private void yppowers$clearFrozenPlayerRenderContext(LivingEntityRenderState state, MatrixStack matrices,
                                                          OrderedRenderCommandQueue queue, CameraRenderState cameraState,
                                                          CallbackInfo ci) {
        FrozenPlayerRenderContext.setFrozenPlayer(false);
    }

    @Inject(method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I", at = @At("RETURN"), cancellable = true)
    private void yppowers$grayFrozenPlayerBody(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (state instanceof PlayerEntityRenderState playerState
                && ClientPowerData.timeStoppedPlayerEntityIds.contains(playerState.id)) {
            cir.setReturnValue(yppowers$applyGrayTint(cir.getReturnValue()));
        }
    }

    private static int yppowers$applyGrayTint(int color) {
        // Treat the no-overlay/default-white cases as a clean starting point, while preserving
        // any existing renderer alpha when a vanilla overlay is already present.
        int alpha = (color >>> 24) & 0xFF;
        if (color == 0 || alpha == 0) return YPPOWERS_FROZEN_GRAY;
        int red = ((color >>> 16) & 0xFF) * 0xB3 / 0xFF;
        int green = ((color >>> 8) & 0xFF) * 0xB3 / 0xFF;
        int blue = (color & 0xFF) * 0xB3 / 0xFF;
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
