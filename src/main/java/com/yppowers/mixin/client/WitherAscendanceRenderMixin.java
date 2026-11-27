package com.yppowers.mixin.client;

import com.yppowers.client.render.WitherAscendanceVisual;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({EntityRenderManager.class})
public abstract class WitherAscendanceRenderMixin {
   @Inject(
      method = {"render"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$renderWitherAscendantPlayer(
      EntityRenderState state,
      CameraRenderState cameraState,
      double x,
      double y,
      double z,
      MatrixStack matrices,
      OrderedRenderCommandQueue queue,
      CallbackInfo ci
   ) {
      if (WitherAscendanceVisual.renderAsWither((EntityRenderManager)(Object)this, state, cameraState, x, y, z, matrices, queue)) {
         ci.cancel();
      }
   }
}
