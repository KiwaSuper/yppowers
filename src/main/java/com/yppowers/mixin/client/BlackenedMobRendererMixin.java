package com.yppowers.mixin.client;

import com.yppowers.client.render.BlackenedMobRenderContext;
import com.yppowers.client.render.BlackenedMobRenderStateAccess;
import com.yppowers.entity.BlackenedMobEntityAccess;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin({LivingEntityRenderer.class})
public abstract class BlackenedMobRendererMixin {
   @Inject(
      method = {"updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V"},
      at = {@At("RETURN")}
   )
   private void yppowers$copyBlackenedState(LivingEntity entity, LivingEntityRenderState state, float tickDelta, CallbackInfo ci) {
      boolean blackened = entity instanceof MobEntity mob && ((BlackenedMobEntityAccess)mob).yppowers$isBlackenedByDarkness();
      ((BlackenedMobRenderStateAccess)state).yppowers$setBlackenedByDarkness(blackened);
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
      at = {@At("HEAD")}
   )
   private void yppowers$setBlackenedFeatureContext(
      LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci
   ) {
      BlackenedMobRenderContext.setBlackened(((BlackenedMobRenderStateAccess)state).yppowers$isBlackenedByDarkness());
   }

   @Inject(
      method = {"render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V"},
      at = {@At("RETURN")}
   )
   private void yppowers$clearBlackenedFeatureContext(
      LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci
   ) {
      BlackenedMobRenderContext.setBlackened(false);
   }

   @Inject(
      method = {"getMixColor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void yppowers$blackenMobBody(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
      if (((BlackenedMobRenderStateAccess)state).yppowers$isBlackenedByDarkness()) {
         cir.setReturnValue(-16777216);
      }
   }
}
