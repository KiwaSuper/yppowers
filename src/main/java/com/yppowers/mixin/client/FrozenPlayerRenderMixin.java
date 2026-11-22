package com.yppowers.mixin.client;

import com.yppowers.client.ClientState;
import com.yppowers.client.render.FrozenRenderContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class FrozenPlayerRenderMixin {
    private static final int FROZEN_GRAY = 0xFFB3B3B3;

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("HEAD")
    )
    private void yppowers$enterFrozenContext(LivingEntityRenderState state, MatrixStack matrices,
                                             OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        boolean frozen = state instanceof PlayerEntityRenderState ps && ClientState.timeStoppedPlayerIds.contains(ps.id);
        FrozenRenderContext.setFrozen(frozen);
    }

    @Inject(
            method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("RETURN")
    )
    private void yppowers$exitFrozenContext(LivingEntityRenderState state, MatrixStack matrices,
                                            OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        FrozenRenderContext.setFrozen(false);
    }

    @Inject(method = "getMixColor(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;)I",
            at = @At("RETURN"), cancellable = true)
    private void yppowers$tintFrozenBody(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (state instanceof PlayerEntityRenderState ps && ClientState.timeStoppedPlayerIds.contains(ps.id)) {
            int c = cir.getReturnValue();
            int a = (c >>> 24) & 0xFF;
            if (c == 0 || a == 0) {
                cir.setReturnValue(FROZEN_GRAY);
            } else {
                int r = ((c >>> 16) & 0xFF) * 0xB3 / 0xFF;
                int g = ((c >>> 8) & 0xFF) * 0xB3 / 0xFF;
                int b = (c & 0xFF) * 0xB3 / 0xFF;
                cir.setReturnValue((a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }
}
