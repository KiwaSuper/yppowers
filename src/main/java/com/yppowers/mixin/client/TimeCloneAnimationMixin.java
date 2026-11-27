package com.yppowers.mixin.client;

import com.yppowers.client.render.TimeCloneRenderStateAccess;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.AbstractZombieModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({AbstractZombieModel.class})
public abstract class TimeCloneAnimationMixin {
   @Shadow
   @Final
   public ModelPart rightArm;
   @Shadow
   @Final
   public ModelPart leftArm;

   @Inject(
      method = {"setAngles(Lnet/minecraft/client/render/entity/state/ZombieEntityRenderState;)V"},
      at = {@At("TAIL")}
   )
   private void yppowers$animateCloneLikePlayer(LivingEntityRenderState state, CallbackInfo ci) {
      if (state instanceof TimeCloneRenderStateAccess access && access.yppowers$getCloneOwner() != null) {
         float phase = state.limbSwingAnimationProgress * 0.6662F;
         float amount = state.limbSwingAmplitude;
         this.rightArm.pitch = MathHelper.cos(phase + (float) Math.PI) * 2.0F * amount * 0.5F;
         this.leftArm.pitch = MathHelper.cos(phase) * 2.0F * amount * 0.5F;
         this.rightArm.yaw = 0.0F;
         this.leftArm.yaw = 0.0F;
         this.rightArm.roll = 0.0F;
         this.leftArm.roll = 0.0F;
      }
   }
}
